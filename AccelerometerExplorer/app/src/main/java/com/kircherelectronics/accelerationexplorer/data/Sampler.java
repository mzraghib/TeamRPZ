package com.kircherelectronics.accelerationexplorer.data;

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
 * An interface for classes that need to sample data from a sensor or other
 * input.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public interface Sampler
{
	/**
	 * The state of the measurements.
	 * 
	 * @return The state of the measurements.
	 */
	public int getSampleState();
	
	/**
	 * Determine if the samples are being recorded.
	 * 
	 * @return True is samples are being recorded.
	 */
	public boolean isSampling();

	/**
	 * Indicate if samples are being recorded.
	 * 
	 * @param sampling
	 *            True if samples are being recorded.
	 */
	public void setSampling(boolean sampling);

	/**
	 * Indicate the sample measurement state.
	 * 
	 * @param state
	 *            The sample measurement state.
	 */
	public void setSampleState(int state);
}
