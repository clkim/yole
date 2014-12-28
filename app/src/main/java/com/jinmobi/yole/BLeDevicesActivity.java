package com.jinmobi.yole;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.Toolbar;

import com.lorentzos.flingswipe.SwipeFlingAdapterView;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class BLeDevicesActivity extends Activity {

    private static final int    MAX_VISIBLE = 2;       // so at most 2 cards are 'Empty'
    private static final int    REQUEST_ENABLE_BT = 1; // arbitrary request code
    private static final long   SCAN_PERIOD = 10000;   // time in ms to do scanning
    private static final String LOG_TAG = BLeDevicesActivity.class.getSimpleName();

    private static String        EMPTY;             // text to show on swipe card with no device
    private ArrayList<String>    al;                // array list to store found device names
    private ArrayAdapter<String> arrayAdapter;      // array adapter for the SwipeFlingAdapterView
    private BluetoothAdapter     mBluetoothAdapter; // the local Bluetooth adapter; scan BLE devices
    private Handler              mHandler;          // handler to post runnable on the main thread

    /**
     *  We use the excellent Swipecards library which is Copyright 2014 Dionysis Lorentzos.
     *  Licensed under the Apache License, Version 2.0.
     *
     *  Downloaded from https://github.com/Diolor/Swipecards
     */
    @InjectView(R.id.frame) SwipeFlingAdapterView flingContainer;


    @InjectView(R.id.toolbar) Toolbar toolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_devices);
        ButterKnife.inject(this); // inject the annotated view objects

        EMPTY = getResources().getString(R.string.empty);

        // for navigation button set in layout
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //no op for now
            }
        });
        toolbar.setNavigationContentDescription("No Op");

        setActionBar(toolbar);


        // Bluetooth LE

        // Use this check to determine whether BLE is supported on the device.
        // clk: reportedly should not be needed if has following in manifest
        //  <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }
        // clk: Checks if LE Advertising is supported, true only for API 21+
/*        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, R.string.error_le_peripheral_role_not_supported,Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }*/


        // set up SwipeFlingAdapterView

        flingContainer.setMaxVisible(MAX_VISIBLE);
        //flingContainer.setAdapter(arrayAdapter); // done in onResume()
        flingContainer.setFlingListener(new SwipeFlingAdapterView.onFlingListener() {
            @Override
            public void removeFirstObjectInAdapter() {
                // this is the simplest way to delete an object from the Adapter (/AdapterView)
                Log.d("LIST", "removed object!");
                // defensive check against index 0 size 0 IndexOutOfBoundsException seen in testing
                if (al != null && al.size() > 0) {
                    al.add(al.size(), al.get(0)); // clk: add first object to end before removing
                    al.remove(0);
                    arrayAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onLeftCardExit(Object dataObject) {
                //Do something on the left!
                //You also have access to the original object.
                //If you want to use it just cast it (String) dataObject
                //makeToast(MyActivity.this, "Left!");
            }

            @Override
            public void onRightCardExit(Object dataObject) {
                //makeToast(MyActivity.this, "Right!");
            }

            @Override
            public void onAdapterAboutToEmpty(int itemsInAdapter) {
                // Ask for more data here

                // clk: For now, a hack around seen current behavior of the library so that
                // it would show a second card underneath first if itemsInAdapter is 0;
                // also see related hack in ScanCallBack.onScanResult().
                // TODO: look at fixing the library if possible
                if (itemsInAdapter == 0 && al != null) { // defensive check for possible NPE
                    al.add(EMPTY);
                    Log.d("LIST", "notified 0");
                }

                // add an 'Empty' card
                if (al != null && arrayAdapter != null) { // defensive check for possible NPE
                    al.add(EMPTY);
                    arrayAdapter.notifyDataSetChanged();
                    Log.d("LIST", "notified");
                }
            }

            @Override
            public void onScroll(float scrollProgressPercent) {
                View view = flingContainer.getSelectedView();
                if (view == null) return; // clk: avoid NPE seen in card speed-flinging stress test
                view.findViewById(R.id.item_swipe_right_indicator)
                        .setAlpha(scrollProgressPercent < 0 ? -scrollProgressPercent : 0);
                view.findViewById(R.id.item_swipe_left_indicator)
                        .setAlpha(scrollProgressPercent > 0 ? scrollProgressPercent : 0);
            }
        });

        // Optionally add an OnItemClickListener
        flingContainer.setOnItemClickListener(new SwipeFlingAdapterView.OnItemClickListener() {
            @Override
            public void onItemClicked(int itemPosition, Object dataObject) {
                if ("Empty".equals(dataObject))
                    makeToast(BLeDevicesActivity.this, "Clicked on Empty!");
                else
                    makeToast(BLeDevicesActivity.this, "Clicked!");
            }
        });

        // for use in scanning
        mHandler = new Handler();
    }

    static void makeToast(Context ctx, String s){
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializes the array adapter for the SwipeFlingAdapterView
        al = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, R.layout.item, R.id.itemText, al);
        flingContainer.setAdapter(arrayAdapter);

        // scan for nearby BLE
        scanLeDevice(true); // underlying arrayAdapter is new at this time
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop any ongoing scan
        scanLeDevice(false);

        al.clear();
        arrayAdapter = null;
    }

    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothLeScanner.stopScan(scScanCallback);
                }
            }, SCAN_PERIOD);

            bluetoothLeScanner.startScan(scScanCallback);
        } else {
            bluetoothLeScanner.stopScan(scScanCallback);
        }
    }

    // Device scan callback.
    private ScanCallback scScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            // clk: a hack to remove any extraneous EMPTY cards
            //  related to hack in SwipeFlingAdapterView fling listener's onAdapterAboutToEmpty()
            while ( al.size()==2 &&
                    (EMPTY.equals(al.get(0)) || EMPTY.equals(al.get(1))) ) {
                if(EMPTY.equals(al.get(1))) {
                    al.remove(1); // remove first element at 0 index last
                    Log.d(LOG_TAG, "removed get(1) EMPTY");
                }
                if(EMPTY.equals(al.get(0))) {
                    al.remove(0);
                    Log.d(LOG_TAG, "removed get(0) EMPTY");
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String resultDeviceName = result.getDevice().getName();
                    if (resultDeviceName == null) resultDeviceName = "null Device";
                    // defensive check against NPE seen in testing with orientation change
                    if (al != null && arrayAdapter != null) {
                        // update the arraylist and notify adapter of change
                        if (!al.contains(resultDeviceName)) {
                            al.add(resultDeviceName);
                            arrayAdapter.notifyDataSetChanged();
                            Log.d(LOG_TAG, "added " + resultDeviceName);
                        }
                    }
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(LOG_TAG,
                    "*** unexpected BluetoothLeScanner callback: onBatchScanResults(), no op done");

        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(LOG_TAG,
                    "*** BluetoothLeScanner: onScanFailed() called, error code: " + errorCode);
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ble_devices, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
