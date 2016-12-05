
package com.example.BluetoothRemote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.RemoteCommand;
import com.example.RemoteValues;
import com.example.android.IntentIntegrator;
import com.example.android.IntentResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * This is the main Activity that displays the current command session.
 */
public class BluetoothRemote extends Activity implements SensorEventListener {
    // Debugging
    private static final String TAG = "BluetoothRemote";
    private static final boolean D = false;

    // Preferences filename - used to store previously connected device
    public static final String PREFS_NAME = "BluetoothRemotePrefsFile";

    // Message types sent from the BluetoothCommandService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_DEVICE_ADDRESS = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothCommandService Handler
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_QR_CODE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_BOOKMARK = 4;

    // Layout Views
    private RelativeLayout myLayout;
    private EditText textBox;
    private Button enterButton;
    private Button deleteButton;

    // Address of the connected device
    private String mConnectedDeviceAddress = null;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the command services
    private BluetoothCommandService mCommandService = null;

    // File to save bookmarks to
    private String filename = "bookmarks.txt";
    private Sensor mySensor;
    private SensorManager SM;
    public int dx;
    public int dy;
    private int mPreviousX;
    private int mPreviousY;
    public float parameter1 = 0;
    public float parameter2 = 0;
    //public byte[] buffer;
    //private static final long serialVersionUID = 5172852401L;
    protected volatile float[] acceleration = new float[3];
    protected volatile float[] abc = new float[3];

    protected MeanFilterSmoothing meanFilterAccelSmoothing;

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("002b8631-0000-1000-8000-00805f9b34fb");

    // Member fields
    private BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private BluetoothSocket mSocket;
    private OutputStream mOutStream;


    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);

        myLayout = (RelativeLayout) findViewById(R.id.rlayout);
        myLayout.setOnTouchListener(new RelativeLayout.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getPointerCount() == 1)
                    mCommandService.handleTouch(event); // Added 11/26 - Accelerometer values intergrated to only the one touch method
                else if(event.getPointerCount() == 2){
                    mCommandService.handleMultiTouch(event);
                }
                return true;
            }
        });

        textBox = (EditText) findViewById(R.id.textBox);
        textBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                mCommandService.handleText(textBox.getText().toString());
                textBox.setText("");
                return false;
            }
        });

        deleteButton = (Button) findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mCommandService.handleDelete();

            }
        });

        enterButton = (Button) findViewById(R.id.enterButton);
        enterButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCommandService.handleText(textBox.getText().toString());
                textBox.setText("");
                mCommandService.handleEnter();
            }
        });

        meanFilterAccelSmoothing = new MeanFilterSmoothing();
        SM = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mySensor = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
//        mHandler = handler;
        mSocket = null;
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            toast("Bluetooth is not available");
            finish();
        }

        // Launch the qr code scanner for initial connection
        //IntentIntegrator integrator = new IntentIntegrator(this);
        //integrator.initiateScan();

        // Restore previously connected device from preferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        mConnectedDeviceAddress = settings.getString("deviceAddress", null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupCommand() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        // otherwise set up the command service
        else {
            if (mCommandService==null)
                setupCommand();
        }
    }



    /**
     * Set the current state of the command connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
//        mHandler.obtainMessage(BluetoothRemote.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the command service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        mSocket = null;

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        mSocket = null;

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
//        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_DEVICE_NAME);
//        Bundle bundle = new Bundle();
//        bundle.putString(BluetoothRemote.DEVICE_NAME, device.getName());
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);
//
//        // Send the address of the connected device back to the UI Activity
//        msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_DEVICE_ADDRESS);
//        bundle = new Bundle();
//        bundle.putString(BluetoothRemote.DEVICE_ADDRESS, device.getAddress());
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
//        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString(BluetoothRemote.TOAST, "Unable to connect device");
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
//        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString(BluetoothRemote.TOAST, "Device connection was lost");
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);

        // Set the state back to STATE_LISTEN
        setState(STATE_LISTEN);


        // Start the service over to restart listening mode
        this.start();
    }

    class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = "Secure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(
                        MY_UUID_SECURE);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mSocket.connect();
            } catch (Exception e) {
                // Close the socket
                try {
                    mSocket.close();
                } catch (Exception e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mSocket.close();
                mSocket = null;

            } catch (Exception e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    class ConnectedThread extends Thread {
        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mSocket = socket;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket output stream
            try {
                tmpOut = socket.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mOutStream.write(buffer);
                mOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
                connectionLost();
            }
        }

        public void cancel() {
            try {
                mSocket.close();
                mSocket = null;
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
//
//    public byte[] getByteArray() {
//        byte[] retval = null;
//        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
//        ObjectOutput out = null;
//        try {
//            out = new ObjectOutputStream(bos);
//            out.writeObject(this);
//            return bos.toByteArray();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                out.close();
//                bos.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        return retval;
//    }

    public float X;
    public float Y;
//    public float Z;
//    public int command = 0;

    @Override
    public void onSensorChanged(SensorEvent event) {
        abc[0]= (float) (event.values[0]*2.5);
        abc[1]= (float) (event.values[1]*2.5);
        abc[2]= (float) (event.values[2]*2.5);


        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(abc, 0, acceleration, 0,
                    event.values.length);

            acceleration = meanFilterAccelSmoothing
                    .addSamples(acceleration);
        }

        X = abc[0];
        Y = abc[1];
//        Z = event.values[2];
//        X*=2.5;
//        Y*=2.5;
//        Toast.makeText(this,String.valueOf(d),Toast.LENGTH_LONG).show();
        RemoteCommand rcm = new RemoteCommand();


//        dx = (int) X - mPreviousX;
//        dy = (int) Y - mPreviousY;

        rcm.command = RemoteValues.MOVE_MOUSE_BY;
        byte[] buffer;
//        parameter1 = dx;
//        parameter2 = dy;
          rcm.parameter1 = (int) -X;
          rcm.parameter2 = (int) -Y;

        // re-init
//        rcm.parameter1 = parameter1;
//        rcm.parameter2 = parameter2;
//        rcm.parameter1 = parameter1;
//        rcm.parameter2 = parameter2;
//      Context context = getApplicationContext();
//      String text = String.valueOf(parameter1);
//      int duration = Toast.LENGTH_LONG;
//      Toast toast = Toast.makeText(context, text, duration);
//      toast.show();
//        Toast.makeText(this,String.valueOf(100),Toast.LENGTH_LONG).show();

        buffer = rcm.getByteArray();
        write(buffer);

        Log.v("X in Handle Touch",String.valueOf(rcm.parameter1));
//        Message msg = mHandler.obtainMessage(BluetoothRemote.MESSAGE_TOAST);
//        Bundle bundle = new Bundle();
//        bundle.putString(BluetoothRemote.TOAST, String.valueOf(parameter1));
//        msg.setData(bundle);
//        mHandler.sendMessage(msg);
        //timeLastSend = System.currentTimeMillis() / 10;
//        parameter1= 0;
//        parameter2= 0;
//        mPreviousX = (int) X;
//        mPreviousY = (int) Y;
        Log.v("X in onSensorChanged",String.valueOf(X));
    }

    static class MeanFilterSmoothing{
//        private static final String tag = MeanFilterSmoothing.class.getSimpleName();

        private float timeConstant = 1;
        private float startTime = 0;
        private float timestamp = 0;
        private float hz = 0;

        private int count = 0;
        // The size of the mean filters rolling window.
        private int filterWindow = 20;

        private boolean dataInit;

        private ArrayList<LinkedList<Number>> dataLists;

        /**
         * Initialize a new MeanFilter object.
         */
        MeanFilterSmoothing()
        {
            dataLists = new ArrayList<>();
            dataInit = false;
        }

        public void setTimeConstant(float timeConstant)
        {
            this.timeConstant = timeConstant;
        }

        public void reset()
        {
            startTime = 0;
            timestamp = 0;
            count = 0;
            hz = 0;
        }

        /**
         * Filter the data.
         *
         *            contains input the data.
         * @return the filtered output data.
         */
        float[] addSamples(float[] data)
        {
            // Initialize the start time.
            if (startTime == 0)
            {
                startTime = System.nanoTime();
            }

            timestamp = System.nanoTime();

            // Find the sample period (between updates) and convert from
            // nanoseconds to seconds. Note that the sensor delivery rates can
            // individually vary by a relatively large time frame, so we use an
            // averaging technique with the number of sensor updates to
            // determine the delivery rate.
            hz = (count++ / ((timestamp - startTime) / 1000000000.0f));

            filterWindow = (int) (hz * timeConstant);

            for (int i = 0; i < data.length; i++)
            {
                // Initialize the data structures for the data set.
                if (!dataInit)
                {
                    dataLists.add(new LinkedList<Number>());
                }

                dataLists.get(i).addLast(data[i]);

                if (dataLists.get(i).size() > filterWindow)
                {
                    dataLists.get(i).removeFirst();
                }
            }

            dataInit = true;

            float[] means = new float[dataLists.size()];

            for (int i = 0; i < dataLists.size(); i++)
            {
                means[i] = (float) getMean(dataLists.get(i));
            }

            return means;
        }

        /**
         * Get the mean of the data set.
         *
         * @param data
         *            the data set.
         * @return the mean of the data set.
         */
        private float getMean(List<Number> data)
        {
            float m = 0;
            float count = 0;

            for (int i = 0; i < data.size(); i++)
            {
                m += data.get(i).floatValue();
                count++;
            }

            if (count != 0)
            {
                m = m / count;
            }

            return m;
        }

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");
        SM.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_NORMAL);


        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mCommandService != null) {
            // Forces update of the connection's state
            checkConnection();
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mCommandService.getState() == STATE_NONE) {
                // Start the Bluetooth command services
                start();
            }
            // Attempt to connect to the last connected device
            if(mConnectedDeviceAddress != null &&
                    getState() == STATE_LISTEN){
                connectDevice(mConnectedDeviceAddress);
            }
        }
    }

    public void checkConnection() {
        RemoteCommand rcm = new RemoteCommand();
        byte[] buffer;

        rcm.command = RemoteValues.CHECK_CONNECTION;

        buffer = rcm.getByteArray();
        write(buffer);
    }

    private void setupCommand() {
        if(D)if(D)Log.d(TAG, "setupCommand()");

        // Initialize the BluetoothCommandService to perform bluetooth connections
//        mCommandService = new BluetoothCommandService(this, mHandler);
        mCommandService = new BluetoothCommandService(this);

    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");

        // Save previously connected device address to preferences for next time if not null
        if(mConnectedDeviceAddress != null){
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("deviceAddress", mConnectedDeviceAddress);

            // Commit the edits
            editor.commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth command services
        if (mCommandService != null) stop();
        SM.unregisterListener(this);
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }



    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }


    // The Handler that gets information back from the BluetoothCommandService
//    private final Handler mHandler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            switch (msg.what) {
//            case MESSAGE_STATE_CHANGE:
//                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
//                switch (msg.arg1) {
//                case BluetoothCommandService.STATE_CONNECTED:
//                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
//                    break;
//                case BluetoothCommandService.STATE_CONNECTING:
//                    setStatus(R.string.title_connecting);
//                    break;
//                case BluetoothCommandService.STATE_LISTEN:
//                    setStatus(R.string.title_not_connected);
//                    break;
//                case BluetoothCommandService.STATE_NONE:
//                    setStatus(R.string.title_not_connected);
//                    break;
//                }
//                break;
//            case MESSAGE_DEVICE_NAME:
//                // save the connected device's name
//                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
//                toast("Connected to " + mConnectedDeviceName);
//                break;
//            case MESSAGE_DEVICE_ADDRESS:
//                mConnectedDeviceAddress = msg.getData().getString(DEVICE_ADDRESS);
//                    break;
//            case MESSAGE_TOAST:
//                toast(msg.getData().getString(TOAST));
//                break;
//            }
//        }
//    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a command session
                    setupCommand();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    if(D)Log.d(TAG, "BT not enabled");
                    toast(getResources().getString(R.string.bt_not_enabled_leaving));
                    finish();
                }
                break;
            case REQUEST_BOOKMARK:
                // When the request to open a bookmark returns
                if(resultCode == Activity.RESULT_OK){
                    // Get the URL
                    String url;
                    url = data.getExtras()
                            .getString(BookmarkListActivity.EXTRA_BOOKMARK_URL);
                    // Get boolean to tell whether to open or delete bookmark
                    boolean open;
                    open = data.getExtras()
                            .getBoolean(BookmarkListActivity.EXTRA_BOOKMARK_OPEN);
                    // If the request was to open a bookmark
                    if(open){
                        // Open the bookmark
                        openBookmark(url);
                    }
                    // Else if the request was to delete a bookmark
                    else {
                        deleteBookmark(url);
                    }

                }
                break;
            case REQUEST_QR_CODE:
                IntentResult scanResult = IntentIntegrator.parseActivityResult(
                        requestCode, resultCode, data);
                if (scanResult != null) {
                    if(D)Log.d(TAG, scanResult.toString());
                    String address = formatAddress(scanResult.getContents());
                    connectDevice(address);
                }
            default:
                if(D)Log.d(TAG, "Incorrect activity result returned");
        }
    }

    private String formatAddress(String tmp){
        try{
            String[] strings = tmp.split("");
            for(int i = 2; i < tmp.length()-1 ; i += 2){
                strings[i] = strings[i].replace(strings[i], (strings[i]+":"));
            }
            StringBuilder builder = new StringBuilder();
            for(String s : strings) {
                builder.append(s);
            }
            return builder.toString();
        } catch(NullPointerException e){
            toast("No QR code received");
            if(D)Log.d(TAG, "No QR code received");
        }
        return "";
    }

    private void connectDevice(String address) {
        try{
            // Get the BluetoothDevice object
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            connect(device);
        } catch(IllegalArgumentException e){
            if(D)Log.d(TAG, "Incorrect bluetooth address received from QR code");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent;
        switch (item.getItemId()) {
            case R.id.reconnect:
                // Attempt to reconnect to previously connected device
                if(mConnectedDeviceAddress != null &&
                        getState() == STATE_LISTEN){
                    connectDevice(mConnectedDeviceAddress);
                    return true;
                }
                else if(getState() != STATE_LISTEN){
                    toast("There is already an active connection with " + mConnectedDeviceName);
                    return false;
                }
                else{
                    toast("Error connecting to device");
                    return false;
                }

            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            case R.id.new_tab:
                // Open a new browser tab
                mCommandService.handleNewTab();
                return true;
            case R.id.add_bookmark:
                // Open a dialog to add a bookmark
                addBookmark();
                return true;
            case R.id.open_bookmark:
                // Launch the BookmarkListActivity to see all bookmarks
                serverIntent = new Intent(this, BookmarkListActivity.class);
                startActivityForResult(serverIntent, REQUEST_BOOKMARK);
                return true;
            case R.id.scan_qr_code:
                // Launch the qr code scanner
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.initiateScan();
                return true;
        }
        return false;
    }

    /**
     * Prompts the user to add a bookmark, and then calls {@link #saveBookmarkToFile(String)}
     * to save the url to bookmarks.txt.
     * @see #saveBookmarkToFile(String)
     * @see #deleteBookmark(String)
     */
    private void addBookmark() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Enter Bookmark URL");
        alert.setMessage("ex: \"http://www.google.com\"");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String url = input.getText().toString();
                saveBookmarkToFile(url);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    /**
     * Adds a line to the end of bookmarks.txt containing the url given.
     * @param url The url to add
     * @return True if the bookmark was added, False otherwise
     * @see #addBookmark()
     */
    private boolean saveBookmarkToFile(String url){
        boolean success = true;
        try {
            FileOutputStream fos = openFileOutput(filename, Context.MODE_APPEND);
            fos.write(url.getBytes());
            fos.write(System.getProperty("line.separator").getBytes());
            fos.close();
        }
        catch (Exception e) {
            success = false;
            toast("Error saving bookmark");
        }
        if(success) {
            toast("Bookmark successfully saved");
        }
        return success;
    }

    /**
     * Sends commands to the connected {@link #mCommandService BluetoothCommandService}
     * to open the URL as a new tab on the server.
     * @param url the URL to open on the server
     */
    public void openBookmark(String url){
        mCommandService.handleNewTab();
        mCommandService.handleText(url);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCommandService.handleEnter();
    }

    /**
     * Finds the line in bookmarks.txt which corresponds to the {@link String}
     * url given, and calls {@link #removeBookmarkFromFile(int)} to delete
     * the line from bookmarks.txt.
     * @param url The url to delete
     * @return True if the bookmark was deleted, False otherwise
     * @see #removeBookmarkFromFile(int)
     * @see #addBookmark()
     */
    private boolean deleteBookmark(String url){
        boolean found = false;
        int line = 1;
        try {
            FileInputStream fis = openFileInput(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String bookmark;
            bookmark = reader.readLine();
            while (bookmark != null){
                if(bookmark.equals(url)){
                    found = true;
                    removeBookmarkFromFile(line);
                    break;
                }
                bookmark = reader.readLine();
                line += 1;
            }
            reader.close();
            fis.close();
        } catch (Exception e) {
            toast("Error deleting bookmark");
        }
        if(found){
            toast("Bookmark successfully deleted");
        }
        return found;
    }

    /**
     * Removes the given line from bookmarks.txt.
     * @param lineNumber The line to delete from the file
     * @throws IOException
     * @see #deleteBookmark(String)
     */
    private void removeBookmarkFromFile(int lineNumber) {
        FileOutputStream tmp;
        FileInputStream fis;
        try {
            tmp = openFileOutput("tmp", Context.MODE_APPEND);
            fis = openFileInput(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(tmp));

            for (int i = 1; i < lineNumber; i++)
                bw.write(String.format("%s%n", br.readLine()));

            br.readLine();

            String line;
            while (null != (line = br.readLine()))
                bw.write(String.format("%s%n", line));

            br.close();
            bw.close();

            Context context = getApplicationContext();
            File oldFile = context.getFileStreamPath(filename);
            File newFile = context.getFileStreamPath("tmp");
            newFile.renameTo(oldFile);
        } catch (IOException e) {
            if(D)Log.e(TAG, e.getLocalizedMessage());
        }

    }

    private void toast(String text){
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        toast.show();
    }
}
