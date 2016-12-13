package com.kircherelectronics.accelerationexplorer.data;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.kircherelectronics.accelerationexplorer.activity.DiagnosticActivity;
import com.kircherelectronics.accelerationexplorer.statistics.Variance;

import android.util.Log;

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
 * A implementation that manages sample measurement state.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class SampleAxisState implements SampleAxisStatable
{
	// Indicate if the positive, or negative, axis is being sampled
	private boolean positiveAxis;
	// Indicate that sampling has completed
	private boolean sampleComplete = false;
	// Indicate that samples are being recorded
	private boolean sampling = false;
	// Indicate the sample is valid
	private boolean sampleValid = false;

	// Get a local copy of the sample size
	private int sampleSize = DiagnosticActivity.getSampleSize();

	// Get a local copy of the thresholds
	private double sampleThreshold = DiagnosticActivity.getSampleThreshold();
	private double sampleGravityThresholdMax;
	private double sampleGravityThresholdMin;

	// The acceleration sample measurements
	private double[] acceleration = new double[sampleSize];

	// The statistical calculations
	private double sampleMax;
	private double sampleMean;
	private double sampleRMS;
	private double sampleMin;

	private int sampleCount = 0;
	private int sampleState;

	private Sampler sampler;
	private Variance variance;

	/**
	 * Create a new instance.
	 * 
	 * @param sampler
	 *            The sampler that owns the state.
	 * @param sampleState
	 *            The sample state key.
	 * @param positiveAxis
	 *            Indicates a positive or negative axis.
	 */
	public SampleAxisState(Sampler sampler, int sampleState,
			boolean positiveAxis)
	{
		super();

		this.positiveAxis = positiveAxis;
		this.sampler = sampler;
		this.sampleState = sampleState;

		this.variance = new Variance();

		// Setup the gravity thresholds
		if (this.positiveAxis)
		{
			sampleGravityThresholdMax = DiagnosticActivity
					.getGravityThresholdMax();
			sampleGravityThresholdMin = DiagnosticActivity
					.getGravityThresholdMin();
		}
		else
		{
			sampleGravityThresholdMax = -DiagnosticActivity
					.getGravityThresholdMin();
			sampleGravityThresholdMin = -DiagnosticActivity
					.getGravityThresholdMax();
		}

	}

	/**
	 * Add a sample to the measurements.
	 * 
	 * @param sample
	 *            The sample to be added.
	 */
	@Override
	public void addSample(double sample)
	{
		if (sampleCount < sampleSize)
		{
			if (this.variance.addSample(sample) < sampleThreshold)
			{
				if (sample < sampleGravityThresholdMax
						&& sample > sampleGravityThresholdMin)
				{
					acceleration[sampleCount] = sample;

					sampleCount++;
				}

				sampleValid = true;
			}
			else
			{
				sampleValid = false;
			}

			sampling = true;
		}
		else if (sampleCount == sampleSize)
		{
			sampleComplete = true;
		}
		else
		{
			sampling = false;
		}
	}

	/**
	 * Get the sample with the largest magnitude.
	 * 
	 * @return The sample with the largest magnitude.
	 */
	public double getSampleMax()
	{
		return sampleMax;
	}

	/**
	 * Get the sample mean.
	 * 
	 * @return The sample mean.
	 */
	public double getSampleMean()
	{
		return sampleMean;
	}

	/**
	 * Get the sample with the smallest magnitude.
	 * 
	 * @return The sample with the smallest magnitude.
	 */
	public double getSampleMin()
	{
		return sampleMin;
	}

	/**
	 * Get the sample RMS, or standard deviation.
	 * 
	 * @return The sample RMS, or standard deviation.
	 */
	public double getSampleRMS()
	{
		return sampleRMS;
	}

	@Override
	public int getSampleState()
	{
		return sampleState;
	}

	/**
	 * Determine if samples are being recorded.
	 * 
	 * @return True if samples are being recorded.
	 */
	@Override
	public boolean isSampling()
	{
		return sampling;
	}

	/**
	 * Determine if sampling is complete.
	 * 
	 * @return True is sampling is complete.
	 */
	@Override
	public boolean isSampleComplete()
	{
		return sampleComplete;
	}

	/**
	 * Determine if the sample is valid.
	 * 
	 * @return True if the sample is valid.
	 */
	@Override
	public boolean isSampleValid()
	{
		return sampleValid;
	}

	/**
	 * Start recording sampling measurements.
	 */
	@Override
	public void startSample()
	{
		sampler.setSampleState(sampleState);
	}

	/**
	 * Stop recording sample measurements.
	 */
	@Override
	public void stopSample()
	{
		sampler.setSampling(false);

		DescriptiveStatistics stats = new DescriptiveStatistics();

		// Add the data from the array
		for (int i = 0; i < acceleration.length; i++)
		{
			stats.addValue(acceleration[i]);
		}

		sampleRMS = stats.getStandardDeviation();

		sampleMean = StatUtils.mean(acceleration);

		sampleMax = StatUtils.max(acceleration);
		sampleMin = StatUtils.min(acceleration);

	}

}
