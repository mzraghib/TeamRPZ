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
 * An interface for classes that need to manage sample measurements state.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public interface SampleAxisStatable
{
	/**
	 * Add a sample to the measurements.
	 * 
	 * @param sample
	 *            The sample to be added.
	 */
	public void addSample(double sample);

	/**
	 * Get the sample with the largest magnitude.
	 * 
	 * @return The sample with the largest magnitude.
	 */
	public double getSampleMax();

	/**
	 * Get the sample mean.
	 * 
	 * @return The sample mean.
	 */
	public double getSampleMean();

	/**
	 * Get the sample with the smallest magnitude.
	 * 
	 * @return The sample with the smallest magnitude.
	 */
	public double getSampleMin();

	/**
	 * Get the sample RMS, or standard deviation.
	 * 
	 * @return The sample RMS, or standard deviation.
	 */
	public double getSampleRMS();

	/**
	 * Get the sample measurement state key.
	 * 
	 * @return The sample measurement state key.
	 */
	public int getSampleState();

	/**
	 * Determine if sampling is complete.
	 * 
	 * @return True is sampling is complete.
	 */
	public boolean isSampleComplete();

	/**
	 * Determine if samples are being recorded.
	 * 
	 * @return True if samples are being recorded.
	 */
	public boolean isSampling();

	/**
	 * Determine if the sample is valid.
	 * 
	 * @return True if the sample is valid.
	 */
	public boolean isSampleValid();

	/**
	 * Start recording sampling measurements.
	 */
	public void startSample();

	/**
	 * Stop recording sample measurements.
	 */
	public void stopSample();

}
