package com.kircherelectronics.accelerationexplorer.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.kircherelectronics.accelerationexplorer.R;
import com.kircherelectronics.accelerationexplorer.filter.MeanFilterSmoothing;

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
 * A class that provides a navigation menu to the features of Acceleration
 * Explorer.
 * 
 * @author Kaleb
 *
 */
public class HomeActivity extends Activity implements SensorEventListener
{
	private final static String tag = HomeActivity.class.getSimpleName();

	// The acceleration, in units of meters per second, as measured by the
	// accelerometer.
	private float[] acceleration = new float[3];

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	private MeanFilterSmoothing meanFilter;

	private Runnable runable;

	// Sensor manager to access the accelerometer
	private SensorManager sensorManager;

	// Text views for real-time output
	private TextView textViewXAxis;
	private TextView textViewYAxis;
	private TextView textViewZAxis;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_home);

		textViewXAxis = (TextView) findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) findViewById(R.id.value_z_axis);

		initButtonDiagnostic();
		initButtonGauge();
		initButtonLogger();
		initButtonNoise();
		initButtonVector();

		meanFilter = new MeanFilterSmoothing();
		meanFilter.setTimeConstant(0.2f);

		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		handler = new Handler();

		runable = new Runnable()
		{
			@Override
			public void run()
			{
				handler.postDelayed(this, 100);

				updateAccelerationText();
			}
		};
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_home, menu);
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

		// Start the vector activity
		case R.id.action_help:
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
				SensorManager.SENSOR_DELAY_NORMAL);

		handler.post(runable);
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			// Get a local copy of the acceleration measurements
			System.arraycopy(event.values, 0, acceleration, 0,
					event.values.length);

			acceleration = meanFilter.addSamples(acceleration);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}

	private void initButtonGauge()
	{
		Button button = (Button) this.findViewById(R.id.button_gauge_mode);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				Intent intent = new Intent(HomeActivity.this,
						GaugeActivity.class);

				startActivity(intent);
			}
		});
	}

	private void initButtonDiagnostic()
	{
		Button button = (Button) this.findViewById(R.id.button_diagnostic_mode);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				Intent intent = new Intent(HomeActivity.this,
						DiagnosticActivity.class);

				startActivity(intent);
			}
		});
	}

	private void initButtonLogger()
	{
		Button button = (Button) this.findViewById(R.id.button_logger_mode);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				Intent intent = new Intent(HomeActivity.this,
						LoggerActivity.class);

				startActivity(intent);
			}
		});
	}

	private void initButtonNoise()
	{
		Button button = (Button) this.findViewById(R.id.button_noise_mode);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				Intent intent = new Intent(HomeActivity.this,
						NoiseActivity.class);

				startActivity(intent);
			}
		});
	}

	private void initButtonVector()
	{
		Button button = (Button) this.findViewById(R.id.button_vector_mode);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				Intent intent = new Intent(HomeActivity.this,
						VectorActivity.class);

				startActivity(intent);
			}
		});
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);

		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);
		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		View view = getLayoutInflater()
				.inflate(R.layout.layout_help_home, null);

		helpDialog.setContentView(view);

		helpDialog.show();
	}

	private void updateAccelerationText()
	{
		// Update the acceleration data
		textViewXAxis.setText(String.format("%.2f", acceleration[0]));
		textViewYAxis.setText(String.format("%.2f", acceleration[1]));
		textViewZAxis.setText(String.format("%.2f", acceleration[2]));
	}
}
