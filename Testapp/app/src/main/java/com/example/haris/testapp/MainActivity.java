package com.example.haris.testapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.Thing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends Activity {

    private boolean isTouch = false;
    private OutputStream outputStream;
    private InputStream inStream;
    public int numbytes = 0;


    private static final int DISCOVER_DURATION = 300;
    private static final int REQUEST_BLU = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void sendViaBluetooth(View v) {

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        if(btAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
        } else {
            enableBluetooth();
        }
    }
    public void enableBluetooth() {

        Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

        discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVER_DURATION);

        startActivityForResult(discoveryIntent, REQUEST_BLU);
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
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == DISCOVER_DURATION && requestCode == REQUEST_BLU) {

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            File f = new File(Environment.getExternalStorageDirectory(), "md5sum.txt");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));

            PackageManager pm = getPackageManager();
            List<ResolveInfo> appsList = pm.queryIntentActivities(intent, 0);

            if(appsList.size() > 0) {
                String packageName = null;
                String className = null;
                boolean found = false;

                for(ResolveInfo info : appsList) {
                    packageName = info.activityInfo.packageName;
                    if(packageName.equals("com.android.bluetooth")) {
                        className = info.activityInfo.name;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Toast.makeText(this, "Bluetooth havn't been found",
                            Toast.LENGTH_LONG).show();
                } else {
                    intent.setClassName(packageName, className);
                    startActivity(intent);
                }
            }
        } else {
            Toast.makeText(this, "Bluetooth is cancelled", Toast.LENGTH_LONG)
                    .show();
        }
    }

    public void write(String s) throws IOException {
            outputStream.write(s.getBytes());
        }

        public Action getIndexApiAction() {
            Thing object = new Thing.Builder()
                    .setName("My Page") // TODO: Define a title for the content shown.
                    // TODO: Make sure this auto-generated URL is correct.
                    .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                    .build();
            return new Action.Builder(Action.TYPE_VIEW)
                    .setObject(object)
                    .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                    .build();
        }


    }