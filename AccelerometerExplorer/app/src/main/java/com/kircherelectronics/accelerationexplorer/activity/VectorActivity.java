package com.kircherelectronics.accelerationexplorer.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;

import com.kircherelectronics.accelerationexplorer.R;
import com.kircherelectronics.accelerationexplorer.activity.config.FilterConfigActivity;
import com.kircherelectronics.accelerationexplorer.view.AccelerationVectorView;

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
 * Draws a two dimensional vector of the acceleration sensors measurements.
 * 
 * @author Kaleb
 * 
 */
public class VectorActivity extends FilterActivity
{
	private AccelerationVectorView view;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_vector);

		textViewXAxis = (TextView) findViewById(R.id.value_x_axis);
		textViewYAxis = (TextView) findViewById(R.id.value_y_axis);
		textViewZAxis = (TextView) findViewById(R.id.value_z_axis);
		textViewHzFrequency = (TextView) findViewById(R.id.value_hz_frequency);

		view = (AccelerationVectorView) findViewById(R.id.vector_acceleration);

		runable = new Runnable()
		{
			@Override
			public void run()
			{
				handler.postDelayed(this, 100);

				updateAccelerationText();
				updateVector();
			}
		};
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_vector, menu);
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
			Intent intent = new Intent(this, FilterConfigActivity.class);
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

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);
		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);

		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		helpDialog.setContentView(getLayoutInflater().inflate(
				R.layout.layout_help_vector, null));

		helpDialog.show();
	}

	private void updateVector()
	{
		if (!lpfLinearAccelEnabled && !imuLaCfOrienationEnabled
				&& !imuLaCfRotationMatrixEnabled && !imuLaCfQuaternionEnabled
				&& !imuLaKfQuaternionEnabled && !androidLinearAccelEnabled)
		{
			view.updatePoint(acceleration[0], acceleration[1]);
		}
		else
		{
			view.updatePoint(linearAcceleration[0], linearAcceleration[1]);
		}
	}
}
