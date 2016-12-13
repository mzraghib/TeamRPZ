package com.kircherelectronics.accelerationexplorer.filter;

import android.hardware.SensorManager;
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
 * ImuLaCf stands for inertial movement unit linear acceleration complementary
 * filter. Rotation Matrix is added because the filter applies the complementary
 * filter to the rotation matrices from the gyroscope and acceleration/magnetic
 * sensors, respectively.
 * 
 * The complementary filter is a frequency domain filter. In its strictest
 * sense, the definition of a complementary filter refers to the use of two or
 * more transfer functions, which are mathematical complements of one another.
 * Thus, if the data from one sensor is operated on by G(s), then the data from
 * the other sensor is operated on by I-G(s), and the sum of the transfer
 * functions is I, the identity matrix.
 * 
 * ImuLaCfRotationMatrix attempts to fuse magnetometer, gravity and gyroscope
 * sensors together to produce an accurate measurement of the rotation of the
 * device.
 * 
 * The magnetometer and acceleration sensors are used to determine one of the
 * two orientation estimations of the device. This measurement is subject to the
 * constraint that the device must not be accelerating and hard and soft-iron
 * distortions are not present in the local magnetic field..
 * 
 * The gyroscope is used to determine the second of two orientation estimations
 * of the device. The gyroscope can have a shorter response time and is not
 * effected by linear acceleration or magnetic field distortions, however it
 * experiences drift and has to be compensated periodically by the
 * acceleration/magnetic sensors to remain accurate.
 * 
 * Rotation matrices are used to integrate the measurements of the gyroscope and
 * apply the rotations to each sensors measurements via complementary filter.
 * This is not ideal because rotation matrices suffer from singularities known
 * as gimbal lock.
 * 
 * The rotation matrix for the magnetic/acceleration sensor is only needed to
 * apply the weighted rotation to the gyroscopes weighted rotation via
 * complementary filter to produce the fused rotation. No integrations are
 * required.
 * 
 * The gyroscope provides the angular rotation speeds for all three axes. To
 * find the orientation of the device, the rotation speeds must be integrated
 * over time. This can be accomplished by multiplying the angular speeds by the
 * time intervals between sensor updates. The calculation produces the rotation
 * increment. Integrating these values again produces the absolute orientation
 * of the device. Small errors are produced at each iteration causing the gyro
 * to drift away from the true orientation.
 * 
 * To eliminate both the drift and noise from the orientation, the gyroscope
 * measurements are applied only for orientation changes in short time
 * intervals. The magnetometer/acceleration fusion is used for long time
 * intervals. This is equivalent to low-pass filtering of the accelerometer and
 * magnetic field sensor signals and high-pass filtering of the gyroscope
 * signals.
 * 
 * @author Kaleb
 * @version %I%, %G%
 * @see http 
 *      ://developer.android.com/reference/android/hardware/SensorEvent.html#
 *      values
 * 
 */

public class ImuLaCfRotationMatrix implements ImuLinearAccelerationInterface
{
	private static final String tag = ImuLaCfRotationMatrix.class
			.getSimpleName();

	public static final float EPSILON = 0.000000001f;

	// private static final float NS2S = 1.0f / 10000.0f;
	// Nano-second to second conversion
	private static final float NS2S = 1.0f / 1000000000.0f;

	private boolean hasOrientation = false;

	// The coefficient for the filter... 0.5 = means it is averaging the two
	// transfer functions (rotations from the gyroscope and
	// acceleration/magnetic, respectively).
	public float filterCoefficient = 0.5f;

	private float dT = 0;

	private float omegaMagnitude = 0;

	private float thetaOverTwo = 0;
	private float sinThetaOverTwo = 0;
	private float cosThetaOverTwo = 0;

	private float[] components = new float[3];

	// angular speeds from gyro
	private float[] gyroscope = new float[3];

	// rotation matrix from gyro data
	private float[] gyroMatrix = new float[9];

	// magnetic field vector
	private float[] magnetic = new float[3];

	// accelerometer vector
	private float[] acceleration = new float[3];

	// final orientation angles from sensor fusion
	private float[] fusedOrientation = new float[3];

	// accelerometer and magnetometer based rotation matrix
	private float[] rotationMatrix = new float[9];

	private float[] linearAcceleration = new float[3];

	// copy the new gyro values into the gyro array
	// convert the raw gyro data into a rotation vector
	private float[] deltaVector = new float[4];

	// convert rotation vector into rotation matrix
	private float[] deltaMatrix = new float[9];

	private long timeStamp;

	/**
	 * Initialize a singleton instance.
	 * 
	 * @param gravitySubject
	 *            the gravity subject.
	 * @param gyroscopeSubject
	 *            the gyroscope subject.
	 * @param magneticSubject
	 *            the magnetic subject.
	 */
	public ImuLaCfRotationMatrix()
	{
		super();

		// Initialize gyroMatrix with identity matrix
		gyroMatrix[0] = 1.0f;
		gyroMatrix[1] = 0.0f;
		gyroMatrix[2] = 0.0f;
		gyroMatrix[3] = 0.0f;
		gyroMatrix[4] = 1.0f;
		gyroMatrix[5] = 0.0f;
		gyroMatrix[6] = 0.0f;
		gyroMatrix[7] = 0.0f;
		gyroMatrix[8] = 1.0f;
	}

	/**
	 * Get the linear acceleration of the device. This method can be called
	 * *only* after setAcceleration(), setMagnetic() and getGyroscope() have
	 * been called.
	 * 
	 * @return float[] an array containing the linear acceleration of the device
	 *         where [0] = x, [1] = y and [2] = z with respect to the Android
	 *         coordinate system.
	 */
	public float[] getLinearAcceleration()
	{
		// values[0]: azimuth, rotation around the Z axis.
		// values[1]: pitch, rotation around the X axis.
		// values[2]: roll, rotation around the Y axis.

		// Find the gravity component of the X-axis
		// = g*-cos(pitch)*sin(roll);
		components[0] = (float) (SensorManager.GRAVITY_EARTH
				* -Math.cos(fusedOrientation[1]) * Math
				.sin(fusedOrientation[2]));

		// Find the gravity component of the Y-axis
		// = g*-sin(pitch);
		components[1] = (float) (SensorManager.GRAVITY_EARTH * -Math
				.sin(fusedOrientation[1]));

		// Find the gravity component of the Z-axis
		// = g*cos(pitch)*cos(roll);
		components[2] = (float) (SensorManager.GRAVITY_EARTH
				* Math.cos(fusedOrientation[1]) * Math.cos(fusedOrientation[2]));

		// Subtract the gravity component of the signal
		// from the input acceleration signal to get the
		// tilt compensated output.
		linearAcceleration[0] = (this.acceleration[0] - components[0]);
		linearAcceleration[1] = (this.acceleration[1] - components[1]);
		linearAcceleration[2] = (this.acceleration[2] - components[2]);

		return linearAcceleration;
	}

	/**
	 * The acceleration of the device. Presumably from Sensor.TYPE_ACCELERATION.
	 * 
	 * @param acceleration
	 *            The acceleration of the device.
	 */
	public void setAcceleration(float[] acceleration)
	{
		// Get a local copy of the raw magnetic values from the device sensor.
		System.arraycopy(acceleration, 0, this.acceleration, 0,
				acceleration.length);

		// We fuse the rotation of the magnetic and acceleration sensor based
		// on acceleration sensor updates. It could be done when the magnetic
		// sensor updates or when they both have updated if you want to spend
		// the resources to make the checks.
		calculateRotationAccelMag();
	}
	
	/**
	 * The complementary filter coefficient, a floating point value between 0-1,
	 * exclusive of 0, inclusive of 1.
	 * 
	 * @param filterCoefficient
	 */
	public void setFilterCoefficient(float filterCoefficient)
	{
		this.filterCoefficient = filterCoefficient;
	}
	
	/**
	 * Set the gyroscope rotation. Presumably from Sensor.TYPE_GYROSCOPE
	 * 
	 * @param gyroscope
	 *            the rotation of the device.
	 * @param timeStamp
	 *            the time the measurement was taken.
	 */
	public void setGyroscope(float[] gyroscope, long timeStamp)
	{
		// don't start until first accelerometer/magnetometer orientation has
		// been acquired
		if (!hasOrientation)
		{
			return;
		}

		if (this.timeStamp != 0)
		{
			dT = (timeStamp - this.timeStamp) * NS2S;

			System.arraycopy(gyroscope, 0, this.gyroscope, 0, 3);
			getRotationVectorFromGyro(dT);
		}

		// measurement done, save current time for next interval
		this.timeStamp = timeStamp;

		// Get the rotation matrix from the gyroscope
		SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

		// Apply the new rotation interval on the gyroscope based rotation
		// matrix to form a composite rotation matrix. The product of two
		// rotation matricies is a rotation matrix...
		// Multiplication of rotation matrices corresponds to composition of
		// rotations... Which in this case are the rotation matrix from the
		// fused orientation and the rotation matrix from the current gyroscope
		// outputs.
		gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

		calculateFusedOrientation();
	}

	/**
	 * Set the magnetic field... presumably from Sensorr.TYPE_MAGNETIC_FIELD.
	 * 
	 * @param magnetic
	 *            the magnetic field
	 */
	public void setMagnetic(float[] magnetic)
	{
		// Get a local copy of the raw magnetic values from the device sensor.
		System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);

	}

	/**
	 * Calculate the fused orientation. We apply the complementary filter to the
	 * respective rotations of the gyroscope and accelerometer/magnetic.
	 */
	private void calculateFusedOrientation()
	{

		// Create our scalar matrix for the gyroscope
		float[] alphaGyro = new float[]
		{ filterCoefficient, 0, 0, 0, filterCoefficient, 0, 0, 0,
				filterCoefficient };

		float oneMinusCoeff = (1.0f - filterCoefficient);

		// Create our scalar matrix for the acceleration/magnetic
		float[] alphaRotation = new float[]
		{ oneMinusCoeff, 0, 0, 0, oneMinusCoeff, 0, 0, 0, oneMinusCoeff };

		// Apply the complementary filter. We multiply each rotation by their
		// coefficients (scalar matrices) and then add the two rotations
		// together.
		// output[0] = alpha * output[0] + (1 - alpha) * input[0];
		gyroMatrix = matrixAddition(
				matrixMultiplication(gyroMatrix, alphaGyro),
				matrixMultiplication(rotationMatrix, alphaRotation));

		// Finally, we get the fused orientation
		SensorManager.getOrientation(gyroMatrix, fusedOrientation);
	}

	/**
	 * Calculates orientation angles from accelerometer and magnetometer output.
	 */
	private void calculateRotationAccelMag()
	{
		// To get the orientation vector from the acceleration and magnetic
		// sensors, we let Android do the heavy lifting. This call will
		// automatically compensate for the tilt of the compass and fail if the
		// magnitude of the acceleration is not close to 9.82m/sec^2. You could
		// perform these steps yourself, but in my opinion, this is the best way
		// to do it.
		SensorManager.getRotationMatrix(rotationMatrix, null, acceleration,
				magnetic);

		if (!hasOrientation)
		{
			gyroMatrix = rotationMatrix;
		}

		hasOrientation = true;
	}

	/**
	 * Calculates a rotation vector from the gyroscope angular speed values.
	 * 
	 * @param gyroValues
	 * @param deltaRotationVector
	 * @param timeFactor
	 * @see http://developer.android
	 *      .com/reference/android/hardware/SensorEvent.html#values
	 */
	private void getRotationVectorFromGyro(float timeFactor)
	{

		// Calculate the angular speed of the sample
		omegaMagnitude = (float) Math.sqrt(Math.pow(gyroscope[0], 2)
				+ Math.pow(gyroscope[1], 2) + Math.pow(gyroscope[2], 2));

		// Normalize the rotation vector if it's big enough to get the axis
		if (omegaMagnitude > EPSILON)
		{
			gyroscope[0] /= omegaMagnitude;
			gyroscope[1] /= omegaMagnitude;
			gyroscope[2] /= omegaMagnitude;
		}

		// Integrate around this axis with the angular speed by the timestep
		// in order to get a delta rotation from this sample over the timestep
		// We will convert this axis-angle representation of the delta rotation
		// into a quaternion before turning it into the rotation matrix.
		thetaOverTwo = omegaMagnitude * timeFactor / 2.0f;
		sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
		cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

		deltaVector[0] = sinThetaOverTwo * gyroscope[0];
		deltaVector[1] = sinThetaOverTwo * gyroscope[1];
		deltaVector[2] = sinThetaOverTwo * gyroscope[2];
		deltaVector[3] = cosThetaOverTwo;
	}

	/**
	 * Multiply A by B.
	 * 
	 * @param A
	 * @param B
	 * @return A*B
	 */
	private float[] matrixMultiplication(float[] A, float[] B)
	{
		float[] result = new float[9];

		result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
		result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
		result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

		result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
		result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
		result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

		result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
		result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
		result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

		return result;
	}

	/**
	 * Add A by B.
	 * 
	 * @param A
	 * @param B
	 * @return A+B
	 */
	private float[] matrixAddition(float[] A, float[] B)
	{
		float[] result = new float[9];

		result[0] = A[0] + B[0];
		result[1] = A[1] + B[1];
		result[2] = A[2] + B[2];
		result[3] = A[3] + B[3];
		result[4] = A[4] + B[4];
		result[5] = A[5] + B[5];
		result[6] = A[6] + B[6];
		result[7] = A[7] + B[7];
		result[8] = A[8] + B[8];

		return result;
	}

}
