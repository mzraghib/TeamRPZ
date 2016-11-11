package com.kircherelectronics.accelerationexplorer.activity;

import java.text.DecimalFormat;

import com.kircherelectronics.accelerationexplorer.R;
import com.kircherelectronics.accelerationexplorer.R.drawable;
import com.kircherelectronics.accelerationexplorer.R.id;
import com.kircherelectronics.accelerationexplorer.R.layout;
import com.kircherelectronics.accelerationexplorer.R.menu;
import com.kircherelectronics.accelerationexplorer.data.SampleAxisStatable;
import com.kircherelectronics.accelerationexplorer.data.SampleAxisState;
import com.kircherelectronics.accelerationexplorer.data.Sampler;
import com.kircherelectronics.accelerationexplorer.filter.MeanFilterSmoothing;
import com.kircherelectronics.accelerationexplorer.gauge.GaugeAcceleration;

import android.R.color;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

/*
 * Acceleration Explorer
 * Copyright (C) 2013-2015, Kaleb Kircher - Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * AccelerationActivity will measure the negative and positive x, y and z axes
 * of from an Android device accelerometer sensor. This provides a measurement
 * of sensor offset, skew, noise and output frequency.
 *
 * @author Kaleb
 * @version %I%, %G%
 */
public class DiagnosticActivity extends Activity implements
		SensorEventListener, OnClickListener, Sampler
{
	// The six state measurements
	private final static int Y_POS = 0;
	private final static int Y_NEG = 1;
	private final static int X_POS = 2;
	private final static int X_NEG = 3;
	private final static int Z_POS = 4;
	private final static int Z_NEG = 5;

	// The total sample size for each state measurement.
	private final static int SAMPLE_SIZE = 500;

	// The minimum threshold, measured as the variance of the samples, that
	// needs to be seen before samples will be recorded.
	private final static double SAMPLE_THRESHOLD = 0.005;

	// The number of samples in the window used to measure the variance
	private final static int SAMPLE_WINDOW = 50;

	// The maximum gravity measurement, in units of gravities of earth, i.e g's,
	// that must be seen before samples will be recorded.
	private final static double GRAVITY_THRESHOLD_MAX = (SensorManager.GRAVITY_EARTH + (SensorManager.GRAVITY_EARTH * 0.1))
			/ SensorManager.GRAVITY_EARTH;
	// The minimum gravity measurement, in units of gravities of earth, i.e g's,
	// that must be seen before samples will be recorde
	private final static double GRAVITY_THRESHOLD_MIN = (SensorManager.GRAVITY_EARTH - (SensorManager.GRAVITY_EARTH * 0.1))
			/ SensorManager.GRAVITY_EARTH;

	// Indicates true if sampling is occurring
	private boolean sampling = false;
	// Indicates true if samples are trying to be taken or are being taken
	private boolean running = false;
	// Indicates true if samples have been recorded successfully
	private boolean finished = false;

	// The number of updates per second, i.e sample frequency. The inverse, ie
	// 1/frequency is
	// the sample period.
	private double frequency;

	// The acceleration, in units of meters per second, as measured by the
	// accelerometer.
	private float[] acceleration = new float[3];

	// Keep track of the sample frequency
	private float time = System.nanoTime();
	private float timeOld = System.nanoTime();

	// The event timestamps for the sample updates are erratic, so we average by
	// dividing total time by the number of samples with is much more stable.
	private int count = 0;

	// The current state
	private int sampleState = 0;

	// Button to control the state
	private Button startButton;

	// Format the output
	private DecimalFormat df = new DecimalFormat("#.##");
	private DecimalFormat dfLong = new DecimalFormat("#.####");

	private GaugeAcceleration accelerationGauge;

	// View for the phone orientation images
	private ImageView imageViewPhone;

	private MeanFilterSmoothing meanFilter;

	// State managers manage almost all of the state for each set of samples
	private SampleAxisStatable yPos;
	private SampleAxisStatable yNeg;
	private SampleAxisStatable xPos;
	private SampleAxisStatable xNeg;
	private SampleAxisStatable zPos;
	private SampleAxisStatable zNeg;

	// Sensor manager to access the accelerometer
	private SensorManager sensorManager;

	// Text views for real-time output
	private TextView textViewXAxis;
	private TextView textViewYAxis;
	private TextView textViewZAxis;

	// Vibrations for successful samples.
	private Vibrator vibe;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_diagnostic);
		createInputView();

		startButton = (Button) findViewById(R.id.button_start);
		startButton.setOnClickListener(this);

		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);

		vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

		meanFilter = new MeanFilterSmoothing();
		meanFilter.setTimeConstant(0.2f);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		sensorManager.unregisterListener(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			// Get a local copy of the acceleration measurements
			System.arraycopy(event.values, 0, acceleration, 0,
					event.values.length);

			time = System.nanoTime();

			// The event timestamps are irregular so we average to determine the
			// update frequency instead of measuring deltas.
			frequency = count++ / ((time - timeOld) / 1000000000.0);

			acceleration = meanFilter.addSamples(acceleration);

			// Update the acceleration data
			textViewXAxis.setText(String.format("%.2f", acceleration[0]));
			textViewYAxis.setText(String.format("%.2f", acceleration[1]));
			textViewZAxis.setText(String.format("%.2f", acceleration[2]));

			accelerationGauge.updatePoint(acceleration[0], acceleration[1],
					Color.rgb(255, 61, 0));

			// Attempt to sample the data
			if (running)
			{
				sample();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}

	@Override
	public void onClick(View v)
	{
		// If the user starts the samples
		if (v.equals(startButton))
		{
			if (!running && !finished)
			{
				CharSequence text = "Hold the device in the described orientation until the device vibrates";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				startButton
						.setBackgroundResource(R.drawable.stop_button_background);
				startButton.setText("Stop");
				running = true;

				yPos.startSample();
			}
			// If the user re-starts the samples
			else if (!running && finished)
			{
				createInputView();

				CharSequence text = "Hold the device in the described orientation until the device vibrates";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				startButton
						.setBackgroundResource(R.drawable.stop_button_background);
				startButton.setText("Stop");

				running = true;
				finished = false;

				yPos.startSample();
			}
			// If the user stops the samples
			else
			{
				startButton
						.setBackgroundResource(R.drawable.start_button_background);
				startButton.setText("Start");

				createInputView();

				running = false;
				finished = false;
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_diagnostic, menu);
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu
	 * item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{

		// Log the data
		case R.id.action_help:
			showHelpDialog();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Get the maximum gravity threshold, in units of earth's of gravity, i.e
	 * g's, the acceleration must be under for samples to be recorded.
	 * 
	 * @return The maximum gravity threshold in units of g's.
	 */
	public static double getGravityThresholdMax()
	{
		return GRAVITY_THRESHOLD_MAX;
	}

	/**
	 * Get the minimum gravity threshold, in units of earth's of gravity, i.e
	 * g's, the acceleration must be above for samples to be recorded.
	 * 
	 * @return The minimum gravity threshold in units of g's.
	 */
	public static double getGravityThresholdMin()
	{
		return GRAVITY_THRESHOLD_MIN;
	}

	/**
	 * The number of samples that will be recorded per measurement.
	 * 
	 * @return The number of samples per measurement.
	 */
	public static int getSampleSize()
	{
		return SAMPLE_SIZE;
	}

	/**
	 * The state of the measurements.
	 * 
	 * @return The state of the measurements.
	 */
	@Override
	public int getSampleState()
	{
		return sampleState;
	}

	/**
	 * The minimum threshold, measured as the variance of the samples, that
	 * needs to be seen before samples will be recorded.
	 * 
	 * @return The sample threshold.
	 */
	public static double getSampleThreshold()
	{
		return SAMPLE_THRESHOLD;
	}

	/**
	 * The number of samples in the window when calculating the minimum
	 * threshold.
	 * 
	 * @return The size of the sample window.
	 */
	public static int getSampleWindow()
	{
		return SAMPLE_WINDOW;
	}

	/**
	 * Return the positive x-axis measurement state key.
	 * 
	 * @return Return the positive x-axis measurement state key
	 */
	public static int getxPos()
	{
		return X_POS;
	}

	/**
	 * Return the negative x-axis measurement state key.
	 * 
	 * @return Return the negative x-axis measurement state key
	 */
	public static int getxNeg()
	{
		return X_NEG;
	}

	/**
	 * Return the positive y-axis measurement state key.
	 * 
	 * @return Return the positive y-axis measurement state key
	 */
	public static int getyPos()
	{
		return Y_POS;
	}

	/**
	 * Return the negative y-axis measurement state key.
	 * 
	 * @return Return the negative y-axis measurement state key
	 */
	public static int getyNeg()
	{
		return Y_NEG;
	}

	/**
	 * Return the positive z-axis measurement state key.
	 * 
	 * @return Return the positive z-axis measurement state key
	 */
	public static int getzPos()
	{
		return Z_POS;
	}

	/**
	 * Return the negative z-axis measurement state key.
	 * 
	 * @return Return the negative z-axis measurement state key
	 */
	public static int getzNeg()
	{
		return Z_NEG;
	}

	/**
	 * Determine if the samples are being recorded.
	 * 
	 * @return True is samples are being recorded.
	 */
	@Override
	public boolean isSampling()
	{
		return sampling;
	}

	/**
	 * Indicate if samples are being recorded.
	 * 
	 * @param sampling
	 *            True if samples are being recorded.
	 */
	@Override
	public void setSampling(boolean sampling)
	{
		this.sampling = sampling;
	}

	/**
	 * Indicate the sample measurement state.
	 * 
	 * @param state
	 *            The sample measurement state.
	 */
	@Override
	public void setSampleState(int state)
	{
		sampleState = state;
	}

	/**
	 * Create the sample input view.
	 */
	private void createInputView()
	{
		RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout_content);
		layout.removeAllViews();

		RelativeLayout inputLayout = (RelativeLayout) getLayoutInflater()
				.inflate(R.layout.layout_diagnostic_input, null);

		RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);

		textViewXAxis = (TextView) inputLayout.findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) inputLayout.findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) inputLayout.findViewById(R.id.value_z_axis);

		imageViewPhone = (ImageView) inputLayout
				.findViewById(R.id.imageViewPhone);
		imageViewPhone.setImageResource(R.drawable.phone);

		accelerationGauge = (GaugeAcceleration) inputLayout
				.findViewById(R.id.gauge_acceleration);

		layout.addView(inputLayout, relativeParams);

		yPos = new SampleAxisState(this, Y_POS, true);
		yNeg = new SampleAxisState(this, Y_NEG, false);
		xPos = new SampleAxisState(this, X_POS, true);
		xNeg = new SampleAxisState(this, X_NEG, false);
		zPos = new SampleAxisState(this, Z_POS, true);
		zNeg = new SampleAxisState(this, Z_NEG, false);
	}

	/**
	 * Create the sample output view.
	 */
	private void createOutputView()
	{
		RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout_content);
		layout.removeAllViews();

		LinearLayout outputLayout = (LinearLayout) getLayoutInflater().inflate(
				R.layout.layout_diagnostic_output, null);

		LinearLayout xAxisLayout = (LinearLayout) outputLayout
				.findViewById(R.id.layout_x_axis);
		LinearLayout yAxisLayout = (LinearLayout) outputLayout
				.findViewById(R.id.layout_y_axis);
		LinearLayout zAxisLayout = (LinearLayout) outputLayout
				.findViewById(R.id.layout_z_axis);

		TextView textViewXAxisLabel = (TextView) xAxisLayout
				.findViewById(R.id.label_app_description);
		TextView textViewYAxisLabel = (TextView) yAxisLayout
				.findViewById(R.id.label_app_description);
		TextView textViewZAxisLabel = (TextView) zAxisLayout
				.findViewById(R.id.label_app_description);

		textViewXAxisLabel.setText("X-Axis");
		textViewYAxisLabel.setText("Y-Axis");
		textViewZAxisLabel.setText("Z-Axis");

		// Set the positive axis max value
		TextView textViewXPosAxisMax = (TextView) xAxisLayout
				.findViewById(R.id.value_pos_axis_max);
		TextView textViewYPosAxisMax = (TextView) yAxisLayout
				.findViewById(R.id.value_pos_axis_max);
		TextView textViewZPosAxisMax = (TextView) zAxisLayout
				.findViewById(R.id.value_pos_axis_max);

		textViewXPosAxisMax.setText(df.format(xPos.getSampleMax()));
		textViewYPosAxisMax.setText(df.format(yPos.getSampleMax()));
		textViewZPosAxisMax.setText(df.format(zPos.getSampleMax()));

		// Set the positive axis minimum value
		TextView textViewXPosAxisMin = (TextView) xAxisLayout
				.findViewById(R.id.value_pos_axis_min);
		TextView textViewYPosAxisMin = (TextView) yAxisLayout
				.findViewById(R.id.value_pos_axis_min);
		TextView textViewZPosAxisMin = (TextView) zAxisLayout
				.findViewById(R.id.value_pos_axis_min);

		textViewXPosAxisMin.setText(df.format(xPos.getSampleMin()));
		textViewYPosAxisMin.setText(df.format(yPos.getSampleMin()));
		textViewZPosAxisMin.setText(df.format(zPos.getSampleMin()));

		// Set the positive axis variance value
		TextView textViewXPosAxisRMS = (TextView) xAxisLayout
				.findViewById(R.id.value_pos_axis_rms);
		TextView textViewYPosAxisRMS = (TextView) yAxisLayout
				.findViewById(R.id.value_pos_axis_rms);
		TextView textViewZPosAxisRMS = (TextView) zAxisLayout
				.findViewById(R.id.value_pos_axis_rms);

		textViewXPosAxisRMS.setText(dfLong.format(xPos.getSampleRMS()));
		textViewYPosAxisRMS.setText(dfLong.format(yPos.getSampleRMS()));
		textViewZPosAxisRMS.setText(dfLong.format(zPos.getSampleRMS()));

		// Set the positive axis mean value
		TextView textViewXPosAxisMean = (TextView) xAxisLayout
				.findViewById(R.id.value_pos_axis_mean);
		TextView textViewYPosAxisMean = (TextView) yAxisLayout
				.findViewById(R.id.value_pos_axis_mean);
		TextView textViewZPosAxisMean = (TextView) zAxisLayout
				.findViewById(R.id.value_pos_axis_mean);

		textViewXPosAxisMean.setText(df.format(xPos.getSampleMean()));
		textViewYPosAxisMean.setText(df.format(yPos.getSampleMean()));
		textViewZPosAxisMean.setText(df.format(zPos.getSampleMean()));

		// Set the negative axis max value
		TextView textViewXNegAxisMax = (TextView) xAxisLayout
				.findViewById(R.id.value_neg_axis_max);
		TextView textViewYNegAxisMax = (TextView) yAxisLayout
				.findViewById(R.id.value_neg_axis_max);
		TextView textViewZNegAxisMax = (TextView) zAxisLayout
				.findViewById(R.id.value_neg_axis_max);

		textViewXNegAxisMax.setText(df.format(xNeg.getSampleMax()));
		textViewYNegAxisMax.setText(df.format(yNeg.getSampleMax()));
		textViewZNegAxisMax.setText(df.format(zNeg.getSampleMax()));

		// Set the negative axis minimum value
		TextView textViewXNegAxisMin = (TextView) xAxisLayout
				.findViewById(R.id.value_neg_axis_min);
		TextView textViewYNegAxisMin = (TextView) yAxisLayout
				.findViewById(R.id.value_neg_axis_min);
		TextView textViewZNegAxisMin = (TextView) zAxisLayout
				.findViewById(R.id.value_neg_axis_min);

		textViewXNegAxisMin.setText(df.format(xNeg.getSampleMin()));
		textViewYNegAxisMin.setText(df.format(yNeg.getSampleMin()));
		textViewZNegAxisMin.setText(df.format(zNeg.getSampleMin()));

		// Set the negative axis variance value
		TextView textViewXNegAxisRMS = (TextView) xAxisLayout
				.findViewById(R.id.value_neg_axis_rms);
		TextView textViewYNegAxisRMS = (TextView) yAxisLayout
				.findViewById(R.id.value_neg_axis_rms);
		TextView textViewZNegAxisRMS = (TextView) zAxisLayout
				.findViewById(R.id.value_neg_axis_rms);

		textViewXNegAxisRMS.setText(dfLong.format(xNeg.getSampleRMS()));
		textViewYNegAxisRMS.setText(dfLong.format(yNeg.getSampleRMS()));
		textViewZNegAxisRMS.setText(dfLong.format(zNeg.getSampleRMS()));

		// Set the negative axis mean value
		TextView textViewXNegAxisMean = (TextView) xAxisLayout
				.findViewById(R.id.value_neg_axis_mean);
		TextView textViewYNegAxisMean = (TextView) yAxisLayout
				.findViewById(R.id.value_neg_axis_mean);
		TextView textViewZNegAxisMean = (TextView) zAxisLayout
				.findViewById(R.id.value_neg_axis_mean);

		textViewXNegAxisMean.setText(df.format(xNeg.getSampleMean()));
		textViewYNegAxisMean.setText(df.format(yNeg.getSampleMean()));
		textViewZNegAxisMean.setText(df.format(zNeg.getSampleMean()));

		// Set the nois variance value
		TextView textViewXNoiseAxisMaxAmp = (TextView) xAxisLayout
				.findViewById(R.id.value_noise_axis_max);
		TextView textViewYNoiseAxisMaxAmp = (TextView) yAxisLayout
				.findViewById(R.id.value_noise_axis_max);
		TextView textViewZNoiseAxisMaxAmp = (TextView) zAxisLayout
				.findViewById(R.id.value_noise_axis_max);

		textViewXNoiseAxisMaxAmp
				.setText(dfLong.format(((xNeg.getSampleMax() - xNeg
						.getSampleMin()) + (xPos.getSampleMax() - xPos
						.getSampleMin())) / 2));
		textViewYNoiseAxisMaxAmp
				.setText(dfLong.format(((yNeg.getSampleMax() - yNeg
						.getSampleMin()) + (yPos.getSampleMax() - yPos
						.getSampleMin())) / 2));
		textViewZNoiseAxisMaxAmp
				.setText(dfLong.format(((zNeg.getSampleMax() - zNeg
						.getSampleMin()) + (zPos.getSampleMax() - zPos
						.getSampleMin())) / 2));

		// Set the noise rms value
		TextView textViewXNoiseAxisRMS = (TextView) xAxisLayout
				.findViewById(R.id.value_noise_axis_rms);
		TextView textViewYNoiseAxisRMS = (TextView) yAxisLayout
				.findViewById(R.id.value_noise_axis_rms);
		TextView textViewZNoiseAxisRMS = (TextView) zAxisLayout
				.findViewById(R.id.value_noise_axis_rms);

		textViewXNoiseAxisRMS.setText(dfLong.format((xNeg.getSampleRMS() + xPos
				.getSampleRMS()) / 2));
		textViewYNoiseAxisRMS.setText(dfLong.format((yNeg.getSampleRMS() + yPos
				.getSampleRMS()) / 2));
		textViewZNoiseAxisRMS.setText(dfLong.format((zNeg.getSampleRMS() + zPos
				.getSampleRMS()) / 2));

		// Set the noise rms value
		TextView textViewXNoiseAxisFreq = (TextView) xAxisLayout
				.findViewById(R.id.value_noise_axis_frequency);
		TextView textViewYNoiseAxisFreq = (TextView) yAxisLayout
				.findViewById(R.id.value_noise_axis_frequency);
		TextView textViewZNoiseAxisFreq = (TextView) zAxisLayout
				.findViewById(R.id.value_noise_axis_frequency);

		textViewXNoiseAxisFreq.setText(df.format(frequency));
		textViewYNoiseAxisFreq.setText(df.format(frequency));
		textViewZNoiseAxisFreq.setText(df.format(frequency));

		RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);

		layout.addView(outputLayout, relativeParams);
	}

	/**
	 * Run the sample state machine.
	 */
	private void sample()
	{
		switch (sampleState)
		{
		case Y_POS:
			yPos.addSample(acceleration[1] / SensorManager.GRAVITY_EARTH);

			if (yPos.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone_0);

				CharSequence text = "Only 5 more to go... Invert the device 180 degrees.";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				yPos.stopSample();

				yNeg.startSample();
			}

			break;
		case Y_NEG:
			yNeg.addSample(acceleration[1] / SensorManager.GRAVITY_EARTH);

			if (yNeg.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone_1);

				CharSequence text = "Only 4 more to go... Rotate the device 90 degrees clock-wise";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				yNeg.stopSample();

				xPos.startSample();
			}
			break;

		case X_POS:
			xPos.addSample(acceleration[0] / SensorManager.GRAVITY_EARTH);

			if (xPos.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone_2);

				CharSequence text = "Only 3 more to go... Rotate the device 180 degrees clock-wise";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				xPos.stopSample();

				xNeg.startSample();
			}

			break;
		case X_NEG:
			xNeg.addSample(acceleration[0] / SensorManager.GRAVITY_EARTH);

			if (xNeg.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone_3);

				CharSequence text = "Only 2 more to go... Set the phone down face-up";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				xNeg.stopSample();

				zPos.startSample();
			}

			break;

		case Z_POS:
			zPos.addSample(acceleration[2] / SensorManager.GRAVITY_EARTH);

			if (zPos.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone_4);

				CharSequence text = "Only 1 more to go... Set the phone down face-down";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				zPos.stopSample();

				zNeg.startSample();
			}

			break;
		case Z_NEG:
			zNeg.addSample(acceleration[2] / SensorManager.GRAVITY_EARTH);

			if (zNeg.isSampleComplete())
			{
				imageViewPhone.setImageResource(R.drawable.phone);

				CharSequence text = "Sampling complete...";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(this, text, duration);
				toast.setGravity(Gravity.CENTER, 0, 0);

				toast.show();

				vibe.vibrate(50);
				vibe.vibrate(50);

				zNeg.stopSample();

				running = false;
				finished = true;

				startButton
						.setBackgroundResource(R.drawable.reset_button_background);
				startButton.setText("Restart");

				createOutputView();
			}

			break;

		default:
			break;
		}
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);

		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);
		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		View view = getLayoutInflater().inflate(
				R.layout.layout_help_diagnostic, null);

		helpDialog.setContentView(view);

		helpDialog.show();
	}
}
