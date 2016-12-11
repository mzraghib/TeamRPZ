package com.example.haris.testapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;


//package com.example.haris.testapp;


public class MainActivity extends Activity {

    private boolean isTouch = false;
    private OutputStream outputStream;
    private InputStream inStream;
    public int numbytes = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //mLinearLayoutMain = (LinearLayout) findViewById(R.id.layout_main);
        //mLinearLayoutMain.setOnTouchListener(this);
        //TextView txtview;

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int X = (int) event.getX();
        int Y = (int) event.getY();
        int eventaction = event.getAction();

        //if (numbytes==)
        //textView2.text = ("x: " + X + " y: " + Y);

        switch (eventaction) {
            case MotionEvent.ACTION_DOWN:

              //  Toast.makeText(this,)
               // Toast.makeText(this, "ACTION_DOWN AT COORDS "+"X: "+X+" Y: "+Y, Toast.LENGTH_SHORT).show();
                isTouch = true;
                break;

            case MotionEvent.ACTION_MOVE:
                Toast.makeText(this, "MOVE "+"X: "+X+" Y: "+Y, Toast.LENGTH_SHORT).show();
                break;

            case MotionEvent.ACTION_UP:
                Toast.makeText(this, "ACTION_UP "+"X: "+X+" Y: "+Y, Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    }
    private void init() throws IOException {
        BluetoothAdapter blueAdapter = BluetoothAdapter.getDefaultAdapter();
        if (blueAdapter != null) {
            if (blueAdapter.isEnabled()) {
                Set<BluetoothDevice> bondedDevices = blueAdapter.getBondedDevices();

//                if(bondedDevices.size() > 0) {
//                    Object[] devices = (Object []) bondedDevices.toArray();
//                    BluetoothDevice device = (BluetoothDevice) devices[position];
//                    ParcelUuid[] uuids = device.getUuids();
//                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuids[0].getUuid());
//                    socket.connect();
//                    outputStream = socket.getOutputStream();
//                    inStream = socket.getInputStream();
//                }

                Log.e("error", "No appropriate paired devices.");
            } else {
                Log.e("error", "Bluetooth is disabled.");
            }
        }
    }
    public class MyActivity extends Activity {
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);

            setContentView(R.layout.content_layout_id);

            final Button button = (Button) findViewById(R.id.button_id);
            button.setOnClickListener(new View.OnClickListener() {
                public void write(byte[] X) throws IOException  {
                    while (true) {
                        outputStream.write(X);
                    }
                }

            });
        }
    }



    public void run() {
        final int BUFFER_SIZE = 1024;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytes = 0;
        int b = BUFFER_SIZE;

        while (true) {
            try {
                bytes = inStream.read(buffer, bytes, BUFFER_SIZE - bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}