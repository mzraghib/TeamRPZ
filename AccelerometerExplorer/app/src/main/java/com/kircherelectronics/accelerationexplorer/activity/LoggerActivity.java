package com.kircherelectronics.accelerationexplorer.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationexplorer.R;
import com.kircherelectronics.accelerationexplorer.activity.config.FilterConfigActivity;
import com.kircherelectronics.accelerationexplorer.plot.DynamicLinePlot;
import com.kircherelectronics.accelerationexplorer.plot.PlotColor;

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
 * An Activity that plots the three axes outputs of the acceleration sensor in
 * real-time, as well as displays the tilt of the device and acceleration of the
 * device in two-dimensions. The acceleration sensor can be logged to an
 * external .CSV file.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class LoggerActivity extends FilterActivity implements Runnable
{
	private final static String tag = LoggerActivity.class.getSimpleName();

	// Plot keys for the acceleration plot
	private final static int PLOT_ACCEL_X_AXIS_KEY = 0;
	private final static int PLOT_ACCEL_Y_AXIS_KEY = 1;
	private final static int PLOT_ACCEL_Z_AXIS_KEY = 2;

	// Indicate if the output should be logged to a .csv file
	private boolean logData = false;

	// The generation of the log output
	private int generation = 0;

	// Color keys for the acceleration plot
	private int plotAccelXAxisColor;
	private int plotAccelYAxisColor;
	private int plotAccelZAxisColor;

	// Log output time stamp
	private long logTime = 0;

	private DecimalFormat df;

	// Graph plot for the UI outputs
	private DynamicLinePlot dynamicPlot;

	// Plot colors
	private PlotColor color;

	// Acceleration plot titles
	private String plotAccelXAxisTitle = "X-Axis";
	private String plotAccelYAxisTitle = "Y-Axis";
	private String plotAccelZAxisTitle = "Z-Axis";
	private String plotSensorFrequencyTitle = "Frequency";

	// Output log
	private String log;

	private Thread thread;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_logger);

		textViewXAxis = (TextView) findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) findViewById(R.id.value_z_axis);
		textViewHzFrequency = (TextView) findViewById(R.id.value_hz_frequency);

		NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
		df = (DecimalFormat) nf;
		df.applyPattern("###.####");

		initColor();
		initPlots();
		initStartButton();

		runable = new Runnable()
		{
			@Override
			public void run()
			{
				handler.postDelayed(this, 10);

				updateAccelerationText();
				plotData();
			}
		};
	}

	@Override
	public void onPause()
	{
		super.onPause();

		stopDataLog();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_logger, menu);
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
		case R.id.action_settings_sensor:
			startIntentSensorSettings();
			return true;

			// Start the vector activity
		case R.id.action_help:
			showHelpDialog();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Output and logs are run on their own thread to keep the UI from hanging
	 * and the output smooth.
	 */
	@Override
	public void run()
	{
		while (logData && !Thread.currentThread().isInterrupted())
		{
			logData();
		}

		Thread.currentThread().interrupt();
	}

	/**
	 * Create the output graph line chart.
	 */
	private void addAccelerationPlot()
	{
		addGraphPlot(plotAccelXAxisTitle, PLOT_ACCEL_X_AXIS_KEY,
				plotAccelXAxisColor);
		addGraphPlot(plotAccelYAxisTitle, PLOT_ACCEL_Y_AXIS_KEY,
				plotAccelYAxisColor);
		addGraphPlot(plotAccelZAxisTitle, PLOT_ACCEL_Z_AXIS_KEY,
				plotAccelZAxisColor);
	}

	/**
	 * Add a plot to the graph.
	 * 
	 * @param title
	 *            The name of the plot.
	 * @param key
	 *            The unique plot key
	 * @param color
	 *            The color of the plot
	 */
	private void addGraphPlot(String title, int key, int color)
	{
		dynamicPlot.addSeriesPlot(title, key, color);
	}

	/**
	 * Create the plot colors.
	 */
	private void initColor()
	{
		color = new PlotColor(this);

		plotAccelXAxisColor = color.getDarkBlue();
		plotAccelYAxisColor = color.getDarkGreen();
		plotAccelZAxisColor = color.getDarkRed();
	}

	/**
	 * Initialize the plots.
	 */
	private void initPlots()
	{
		// Create the graph plot
		XYPlot plot = (XYPlot) findViewById(R.id.plot_sensor);

		plot.setTitle("Acceleration");
		dynamicPlot = new DynamicLinePlot(plot, this);
		dynamicPlot.setMaxRange(20);
		dynamicPlot.setMinRange(-20);

		addAccelerationPlot();
	}

	private void initStartButton()
	{
		final Button button = (Button) findViewById(R.id.button_start);

		button.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				if (!logData)
				{
					button.setBackgroundResource(R.drawable.stop_button_background);
					button.setText("Stop Log");

					startDataLog();

					thread = new Thread(LoggerActivity.this);

					thread.start();
				}
				else
				{
					button.setBackgroundResource(R.drawable.start_button_background);
					button.setText("Start Log");

					stopDataLog();
				}
			}
		});
	}

	/**
	 * Log output data to an external .csv file.
	 */
	private void logData()
	{
		if (logData && dataReady)
		{
			if (generation == 0)
			{
				logTime = System.currentTimeMillis();
			}

			log += generation++ + ",";

			float timestamp = (System.currentTimeMillis() - logTime) / 1000.0f;
			
			log += df.format(timestamp) + ",";

			if (!lpfLinearAccelEnabled && !imuLaCfOrienationEnabled
					&& !imuLaCfRotationMatrixEnabled
					&& !imuLaCfQuaternionEnabled && !androidLinearAccelEnabled
					&& !imuLaKfQuaternionEnabled)
			{
				log += df.format(acceleration[0]) + ",";
				log += df.format(acceleration[1]) + ",";
				log += df.format(acceleration[2]) + ",";
			}
			else
			{
				log += df.format(linearAcceleration[0]) + ",";
				log += df.format(linearAcceleration[1]) + ",";
				log += df.format(linearAcceleration[2]) + ",";
			}

			log += df.format(hz) + ",";

			log += System.getProperty("line.separator");

			dataReady = false;
		}
	}

	/**
	 * Plot the output data in the UI.
	 */
	private void plotData()
	{
		if (!lpfLinearAccelEnabled && !imuLaCfOrienationEnabled
				&& !imuLaCfRotationMatrixEnabled && !imuLaCfQuaternionEnabled
				&& !androidLinearAccelEnabled && !imuLaKfQuaternionEnabled)
		{
			dynamicPlot.setData(acceleration[0], PLOT_ACCEL_X_AXIS_KEY);
			dynamicPlot.setData(acceleration[1], PLOT_ACCEL_Y_AXIS_KEY);
			dynamicPlot.setData(acceleration[2], PLOT_ACCEL_Z_AXIS_KEY);
		}
		else
		{
			dynamicPlot.setData(linearAcceleration[0], PLOT_ACCEL_X_AXIS_KEY);
			dynamicPlot.setData(linearAcceleration[1], PLOT_ACCEL_Y_AXIS_KEY);
			dynamicPlot.setData(linearAcceleration[2], PLOT_ACCEL_Z_AXIS_KEY);
		}

		dynamicPlot.draw();
	}

	/**
	 * Remove a plot from the graph.
	 * 
	 * @param key
	 */
	private void removeGraphPlot(int key)
	{
		dynamicPlot.removeSeriesPlot(key);
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);

		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);
		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		View view = getLayoutInflater().inflate(R.layout.layout_help_logger,
				null);

		helpDialog.setContentView(view);

		helpDialog.show();
	}

	/**
	 * Show a settings dialog.
	 */
	private void startIntentSensorSettings()
	{
		Intent intent = new Intent(LoggerActivity.this,
				FilterConfigActivity.class);

		startActivity(intent);
	}

	/**
	 * Begin logging data to an external .csv file.
	 */
	private void startDataLog()
	{
		if (logData == false)
		{
			generation = 0;

			CharSequence text = "Logging Data";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();

			String headers = "Generation" + ",";

			headers += "Timestamp" + ",";

			headers += this.plotAccelXAxisTitle + ",";

			headers += this.plotAccelYAxisTitle + ",";

			headers += this.plotAccelZAxisTitle + ",";

			headers += this.plotSensorFrequencyTitle + ",";

			log = headers;

			log += System.getProperty("line.separator");

			logData = true;
		}
	}

	private void stopDataLog()
	{
		if (logData)
		{
			writeLogToFile();
		}

		if (logData && thread != null)
		{
			logData = false;

			thread.interrupt();

			thread = null;
		}
	}

	/**
	 * Write the logged data out to a persisted file.
	 */
	private void writeLogToFile()
	{
		Calendar c = Calendar.getInstance();
		String filename = "AccelerationExplorer-" + c.get(Calendar.YEAR) + "-"
				+ (c.get(Calendar.MONTH) + 1) + "-"
				+ c.get(Calendar.DAY_OF_MONTH) + "-" + c.get(Calendar.HOUR)
				+ "-" + c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
				+ ".csv";

		File dir = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "AccelerationExplorer" + File.separator
				+ "Logs");
		if (!dir.exists())
		{
			dir.mkdirs();
		}

		File file = new File(dir, filename);

		FileOutputStream fos;
		byte[] data = log.getBytes();
		try
		{
			fos = new FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();

			CharSequence text = "Log Saved";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
		}
		catch (FileNotFoundException e)
		{
			CharSequence text = e.toString();
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
		}
		catch (IOException e)
		{
			// handle exception
		}
		finally
		{
			// Update the MediaStore so we can view the file without rebooting.
			// Note that it appears that the ACTION_MEDIA_MOUNTED approach is
			// now blocked for non-system apps on Android 4.4.
			MediaScannerConnection.scanFile(this, new String[]
			{ file.getPath() }, null,
					new MediaScannerConnection.OnScanCompletedListener()
					{
						@Override
						public void onScanCompleted(final String path,
								final Uri uri)
						{

						}
					});
		}
	}

}
