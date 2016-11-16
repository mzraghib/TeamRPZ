package com.rshah.accelerometer;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    protected volatile float[] acceleration = new float[3];
    protected MeanFilterSmoothing meanFilterAccelSmoothing;
    private TextView xText, yText, zText;
    private Sensor mySensor;
    private SensorManager SM;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        meanFilterAccelSmoothing = new MeanFilterSmoothing();

        setContentView(R.layout.activity_main);

        // Create Sensor Manager
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);

        //Accelerometer Sensor
        mySensor = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //Register Sensor Listener
        SM.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_NORMAL);

        //Assign TextView
        xText = (TextView) findViewById(R.id.xText);
        yText = (TextView) findViewById(R.id.yText);
        zText = (TextView) findViewById(R.id.zText);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, acceleration, 0,
                    event.values.length);

            acceleration = meanFilterAccelSmoothing
                    .addSamples(acceleration);
        }
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "AnalysisData.csv";
        String filePath = baseDir + File.separator + fileName;
        File f = new File(filePath );
        CSVWriter writer;
// File exist
        if(f.exists() && !f.isDirectory()){
            mFileWriter = new FileWriter(filePath , true);
            writer = new CSVWriter(mFileWriter);
        }
        else {
            writer = new CSVWriter(new FileWriter(filePath));
        }
        String[] data = {"Ship Name","Scientist Name", "...",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").formatter.format(date)});

        writer.writeNext(data);

        writer.close();

        xText.setText("X: " + String.format("%.2f", acceleration[0]));
        yText.setText("Y: " + String.format("%.2f", acceleration[1]));
        zText.setText("Z: " + String.format("%.2f", acceleration[2]));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Not in use
    }
}


