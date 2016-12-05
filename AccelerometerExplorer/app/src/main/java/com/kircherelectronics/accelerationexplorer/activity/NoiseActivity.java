package com.kircherelectronics.accelerationexplorer.activity;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationexplorer.R;
import com.kircherelectronics.accelerationexplorer.activity.config.FilterConfigActivity;
import com.kircherelectronics.accelerationexplorer.activity.config.NoiseConfigActivity;
import com.kircherelectronics.accelerationexplorer.filter.LowPassFilterSmoothing;
import com.kircherelectronics.accelerationexplorer.filter.MeanFilterSmoothing;
import com.kircherelectronics.accelerationexplorer.filter.MedianFilterSmoothing;
import com.kircherelectronics.accelerationexplorer.plot.DynamicBarPlot;

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
 * A class intended to measure the noise of the sensor in terms
 * root-mean-squared (RMS). Effectively, the average deviation from the mean of
 * the sensor outputs. Multiple smoothing filters are applied simultaneously to
 * the raw sensor output and can be compared via a bar chart.
 * 
 * @author Kaleb
 *
 */
public class NoiseActivity extends Activity implements SensorEventListener
{
	private final static String tag = NoiseActivity.class.getSimpleName();

	// Only noise below this threshold will be plotted
	private final static float MAX_NOISE_THRESHOLD = 0.1f;

	// Plot keys for the noise bar plot
	private final static int BAR_PLOT_ACCEL_KEY = 0;
	private final static int BAR_PLOT_LPF_KEY = 1;
	private final static int BAR_PLOT_MEAN_KEY = 2;
	private final static int BAR_PLOT_MEDIAN_KEY = 3;

	public static int STD_DEV_SAMPLE_WINDOW = 20;

	// Outputs for the acceleration and LPFs
	private float[] acceleration = new float[3];
	private float[] lpfOutput = new float[3];
	private float[] meanFilterOutput = new float[3];
	private float[] medianFilterOutput = new float[3];

	// RMS Noise levels
	private DescriptiveStatistics stdDevMaginitudeAccel;
	private DescriptiveStatistics stdDevMaginitudeLpf;
	private DescriptiveStatistics stdDevMaginitudeMean;
	private DescriptiveStatistics stdDevMaginitudeMedian;

	private DynamicBarPlot barPlot;

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// Low-Pass Filter
	private LowPassFilterSmoothing lpf;

	// Mean filter
	private MeanFilterSmoothing meanFilter;

	private MedianFilterSmoothing medianFilter;

	private Runnable runable;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	// Text views for real-time output
	private TextView textViewXAxis;
	private TextView textViewYAxis;
	private TextView textViewZAxis;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_noise);

		textViewXAxis = (TextView) findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) findViewById(R.id.value_z_axis);

		XYPlot noiseLevelsPlot = (XYPlot) findViewById(R.id.plot_noise);
		noiseLevelsPlot.setTitle("Noise");

		barPlot = new DynamicBarPlot(noiseLevelsPlot, "Sensor Noise", this);

		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		lpf = new LowPassFilterSmoothing();
		meanFilter = new MeanFilterSmoothing();
		medianFilter = new MedianFilterSmoothing();

		initStatistics();

		handler = new Handler();

		runable = new Runnable()
		{
			@Override
			public void run()
			{
				handler.postDelayed(this, 100);

				updateBarPlot();
				updateAccelerationText();
			}
		};
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_noise, menu);
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
		case R.id.action_settings:

			Intent intent = new Intent(NoiseActivity.this,
					NoiseConfigActivity.class);
			startActivity(intent);

			return true;

			// Log the data
		case R.id.menu_settings_help:

			showHelpDialog();

			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();

		sensorManager.unregisterListener(this);

		handler.removeCallbacks(runable);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);

		lpf.setTimeConstant(getPrefLpfSmoothingTimeConstant());

		meanFilter.setTimeConstant(getPrefMeanFilterSmoothingTimeConstant());

		medianFilter
				.setTimeConstant(getPrefMedianFilterSmoothingTimeConstant());

		handler.post(runable);
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		// Get a local copy of the sensor values
		System.arraycopy(event.values, 0, acceleration, 0, event.values.length);

		lpfOutput = lpf.addSamples(acceleration);

		meanFilterOutput = meanFilter.addSamples(acceleration);

		medianFilterOutput = medianFilter.addSamples(acceleration);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}

	private float getPrefLpfSmoothingTimeConstant()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		return Float.valueOf(prefs.getString(
				NoiseConfigActivity.LPF_SMOOTHING_TIME_CONSTANT_KEY, "1"));
	}

	private float getPrefMeanFilterSmoothingTimeConstant()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		return Float.valueOf(prefs.getString(
				NoiseConfigActivity.MEAN_FILTER_SMOOTHING_TIME_CONSTANT_KEY,
				"1"));
	}

	private float getPrefMedianFilterSmoothingTimeConstant()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		return Float.valueOf(prefs.getString(
				NoiseConfigActivity.MEDIAN_FILTER_SMOOTHING_TIME_CONSTANT_KEY,
				"1"));
	}

	/**
	 * Initialize the statistics.
	 */
	private void initStatistics()
	{
		// Create the RMS Noise calculations
		stdDevMaginitudeAccel = new DescriptiveStatistics();
		stdDevMaginitudeAccel.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitudeLpf = new DescriptiveStatistics();
		stdDevMaginitudeLpf.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitudeMean = new DescriptiveStatistics();
		stdDevMaginitudeMean.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitudeMedian = new DescriptiveStatistics();
		stdDevMaginitudeMedian.setWindowSize(STD_DEV_SAMPLE_WINDOW);
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);

		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);
		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		View view = getLayoutInflater().inflate(R.layout.layout_help_noise,
				null);

		helpDialog.setContentView(view);

		helpDialog.show();
	}

	/**
	 * Update the bar plot.
	 */
	private void updateBarPlot()
	{
		Number[] seriesNumbers = new Number[4];

		stdDevMaginitudeAccel.addValue(Math.sqrt(Math.pow(acceleration[0], 2)
				+ Math.pow(acceleration[1], 2) + Math.pow(acceleration[2], 2)));

		double var = stdDevMaginitudeAccel.getStandardDeviation();

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_ACCEL_KEY] = var;

		stdDevMaginitudeLpf.addValue(Math.sqrt(Math.pow(lpfOutput[0], 2)
				+ Math.pow(lpfOutput[1], 2) + Math.pow(lpfOutput[2], 2)));

		var = stdDevMaginitudeLpf.getStandardDeviation();

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_LPF_KEY] = var;

		stdDevMaginitudeMean
				.addValue(Math.abs(meanFilterOutput[0])
						+ Math.abs(meanFilterOutput[1])
						+ Math.abs(meanFilterOutput[2]));

		var = stdDevMaginitudeMean.getStandardDeviation();

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_MEAN_KEY] = var;

		stdDevMaginitudeMedian.addValue(Math.abs(medianFilterOutput[0])
				+ Math.abs(medianFilterOutput[1])
				+ Math.abs(medianFilterOutput[2]));

		var = stdDevMaginitudeMedian.getStandardDeviation();

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_MEDIAN_KEY] = var;

		barPlot.onDataAvailable(seriesNumbers);
	}

	private void updateAccelerationText()
	{
		// Update the acceleration data
		textViewXAxis.setText(String.format("%.2f", acceleration[0]));
		textViewYAxis.setText(String.format("%.2f", acceleration[1]));
		textViewZAxis.setText(String.format("%.2f", acceleration[2]));
	}

}
