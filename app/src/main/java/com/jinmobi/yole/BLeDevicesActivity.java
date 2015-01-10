package com.jinmobi.yole;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.lorentzos.flingswipe.SwipeFlingAdapterView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class BLeDevicesActivity extends Activity {

    private static final int    MAX_VISIBLE = 2;          // so at most 2 cards are 'Empty'
    private static final int    REQUEST_ENABLE_BT = 11;   // arbitrary request id code
    private static final long   PERIOD_SCAN = 11100;      // scanning ms, avoid overlap advertising
    private static final int    PERIOD_ADVERTISE = 50000; // advertising time in ms
    private static final int    TTS_REQUEST_CODE = 22;    // arbitrary request id code
    private static final String ACTION_SCAN_LE_DEVICE = "ACTION_SCAN_LE_DEVICE";    // for receiver
    private static final String YOBLE = "Yoh!!";                                    // what we say
    private static final String LOG_TAG = BLeDevicesActivity.class.getSimpleName(); // for logcat
    private static String       EMPTY;               // text to show on swipe card with no device
    private static String       ERR_ADVERTISE_FAIL;  // error message for advertise start failure
    private static String       ERR_SCAN_FAIL;       // error message for scan start failed
    private static ProgressBar  PROGRESS_BAR;        // used by scan menu item in toolbar
//    private static int          COUNT_IN_THIS_SCAN;  // number of results returned in current scan
    private List<Pair<String, String>>
                                  al;                // list to store found devices <name, address>
//    private List<Pair<String, String>>
//                                  alCopy;            // back up of list of found devices
    private ArrayAdapter<Pair<String, String>>
                                  arrayAdapter;      // for SwipeFlingAdapterView
    private BluetoothAdapter      mBluetoothAdapter; // the local Bluetooth adapter
    private Handler               mHandler;          // handler to post runnable on the main thread
    private MenuItem              miScanMenuItem;    // menu item to scan for (app in) BLE device
    private AlarmManager          alarmManager;      // to set repeating alarms to scan ble devices
    private PendingIntent         pendingIntent;     // used in setting repeating alarm
    private BluetoothLeScanner    bluetoothLeScanner;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private StartScanReceiver     receiver;          // broadcast receiver called by repeating alarm
    private BluetoothGatt         bgConnectedGatt;   // for central role
    private BluetoothGattServer   mGattServer;       // for peripheral role
    private TextToSpeech          ttsTextToSpeech;
    private boolean               inOnPause;         // used to not show EMPTY dialog if in onPause


    /**
     *  We use the excellent Swipecards library which is Copyright 2014 Dionysis Lorentzos.
     *  Licensed under the Apache License, Version 2.0.
     *
     *  Downloaded from https://github.com/Diolor/Swipecards
     */
    @InjectView(R.id.frame) SwipeFlingAdapterView flingContainer;

    @InjectView(R.id.toolbar) Toolbar toolbar;


    private static class ViewHolder {
        TextView textView;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ble_devices);
        ButterKnife.inject(this); // inject the annotated view objects

        EMPTY = getResources().getString(R.string.empty);
        ERR_ADVERTISE_FAIL = getResources().getString(R.string.error_le_advertise_start_failure);
        ERR_SCAN_FAIL = getResources().getString(R.string.error_le_scan_failed);
        PROGRESS_BAR = new ProgressBar(this);

//        alCopy = new ArrayList<>();

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

        // clk: Checks if LE Advertising is supported, true only for API 21+ and Nexus 6+/9+
        //  we allow Nexus 4/5/7 in Central role to do ble scan for nearby devices with this app
        //  in that case, no Gatt Server is instantiated,
        //  and we check for null Gatt Server as well as null BLE Advertiser
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, R.string.error_le_peripheral_role_not_supported,Toast.LENGTH_LONG)
                    .show();

        } else {

            // Gatt Server to perform Peripheral role
            mGattServer = bluetoothManager
                    .openGattServer(getApplicationContext(), mGattServerCallback);
        }

        // BLE Scanner
        bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // BLE Advertiser
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();


        // set up SwipeFlingAdapterView

        flingContainer.setMaxVisible(MAX_VISIBLE);
        //flingContainer.setAdapter(arrayAdapter); // done in onResume()
        flingContainer.setFlingListener(new SwipeFlingAdapterView.onFlingListener() {
            @Override
            public void removeFirstObjectInAdapter() {
                // this is the simplest way to delete an object from the Adapter (/AdapterView)

                // defensive check against index 0 size 0 IndexOutOfBoundsException seen in testing
                if (al != null && al.size() > 0) {
                    // add first object to end before removing, so we effect a circular list
                    al.add(al.size(), al.get(0));
                    al.remove(0);
                    arrayAdapter.notifyDataSetChanged();
                    Log.d("LIST", "removed object!");
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
                //Log.d("LIST", "notified");
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

        // add an OnItemClickListener
        flingContainer.setOnItemClickListener(new SwipeFlingAdapterView.OnItemClickListener() {
            @Override @SuppressWarnings("unchecked")
            public void onItemClicked(int itemPosition, Object dataObject) {
                if (EMPTY.equals(((Pair<String, String>)dataObject).first))
                    makeToast(BLeDevicesActivity.this, "Clicked on Empty!");
                else {
                    String address = ((Pair<String, String>) dataObject).second;
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    bgConnectedGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
                    makeToast(BLeDevicesActivity.this, "Clicked!");
                }
            }
        });

        // Set a repeating 'wakeup' alarm mostly so app is in background and advertising goes on;
        // also to do scans for 'recognizable' BLE devices. (Cancel repeating alarm in onDestroy.)
        Intent intent = new Intent(ACTION_SCAN_LE_DEVICE);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        // Alarm period should include scan time plus desired advertising time
        long alarmPeriodMs = PERIOD_SCAN + PERIOD_ADVERTISE;
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + alarmPeriodMs,
                alarmPeriodMs,
                pendingIntent);

        // Set up broadcast receiver to have access to activity object
        receiver = new StartScanReceiver();
        // Inject this activity into receiver in order to call method to scan BLE devices
        receiver.setBLeDevicesActivity(this); // this is the key step
        // Register receiver programmatically here instead of specifying in manifest
        IntentFilter intentFilter = new IntentFilter(ACTION_SCAN_LE_DEVICE);
        registerReceiver(receiver, intentFilter);


        // for use in scanning
        mHandler = new Handler();


        // Text to Speech
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // check there is data for language installed, for TTS
                Intent checkTtsIntent = new Intent();
                checkTtsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                startActivityForResult(checkTtsIntent, TTS_REQUEST_CODE);
                // do setup in onActivityResult(), look for the request code
            }
        }, 100); // to avoid NPE MenuItem.setActionView(View) in onResume's delayed post: scanLeScan
    }

    static void makeToast(Context ctx, String s){
        makeToast(ctx, s, Toast.LENGTH_SHORT);
    }

    static void makeToast(Context ctx, String s, int toastLength) {
        Toast.makeText(ctx, s, toastLength).show();
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
        arrayAdapter = new ArrayAdapter<Pair<String, String>>(this, 0, 0, al) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Pair<String, String> pair = getItem(position);
                ViewHolder viewHolder; // private static nested class
                if (convertView == null) {
                    viewHolder = new ViewHolder();
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item, parent, false);
                    viewHolder.textView = (TextView) convertView.findViewById(R.id.itemText);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }
                viewHolder.textView.setText(pair.first);
                return convertView;
            }
        };
        flingContainer.setAdapter(arrayAdapter);


        // do first scan in this life-cycle for recognizable BLE devices
        // the method also starts BLE advertise after scan is stopped
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanLeDevice(true); // underlying arrayAdapter is new at this time
            }
        }, 50); // saw NPE; so wait for creation of menu item, used in indeterminate progress icon


        // initialize the Gatt Server
        BluetoothGattService service =new BluetoothGattService(DeviceProfile.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        if (mGattServer != null)  // null if no BLE peripheral role e.g. Nexus 4,5,7
            mGattServer.addService(service);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        // Text to speech setup
        if (requestCode == TTS_REQUEST_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                //  seems need to use getApplicationContext() instead of mContext to avoid Error log: ...MainActivity has leaked ServiceConnection android.speech.tts.TextToSpeech$Connection@41e93920 that was originally bound here
                //  http://stackoverflow.com/questions/19653223/texttospeech-and-memory-leak
                ttsTextToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        if (i == TextToSpeech.SUCCESS) {
                            ttsTextToSpeech.setLanguage(Locale.getDefault());
                        } else {
                            Log.e(TextToSpeech.OnInitListener.class.getSimpleName(), "Snap! Text-to-speech onInit returned FAIL");
                        }
                    }
                });

            } else {
                // missing data for language, so install it
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // clear any pending stop scan
        mHandler.removeCallbacks(rStopScanLeDevice);
        // stop any ongoing scan
        inOnPause = true;
        scanLeDevice(false);

        al.clear();
//        alCopy.clear();

        inOnPause = false;

        // don't stop advertise here because we want that to continue in background, do in onDestroy

        // don't close Gatt Server here because we want to get connections in background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // stop any ongoing advertise
        advertiseLe(false);

        if (mGattServer != null) // null if no BLE peripheral role e.g. Nexus 4,5,7
            mGattServer.close();

        if (alarmManager != null)
            alarmManager.cancel(pendingIntent);

        unregisterReceiver(receiver);

        // Text to Speech
        if (ttsTextToSpeech != null) {
            ttsTextToSpeech.stop();
            // release resources
            ttsTextToSpeech.shutdown();
        }
    }

    /**
     * Start or stop BLE scanning for nearby recognizable BLE devices;
     * also start BLE advertising of this device after stopping scan
     *
     * Although device cannot both scan and advertise at same time, we should not stop advertising
     * before starting scan because of reason stated in the comment in the ble advertise method.
     * We initially start advertising after scan is first stopped, and defensively re-start
     * advertising on subsequent stopping of scan; see comment in the ble advertise method.
     *
     * Method is called by a broadcast receiver (activated by repeating alarms), so must be
     * package-private.
     *
     *
     * @param enable  Set true to start and false to stop scan.
     */
    void scanLeDevice(final boolean enable) {
        if (enable) {
            // post delayed job to stop scanning after a pre-defined scan period
            mHandler.postDelayed(rStopScanLeDevice, PERIOD_SCAN);

            // don't stop advertise here; that changes device hardware (MAC) address seen by others

            // copy current items in adapter for restoring later if no results found in this scan
//            alCopy.clear();
//            for (Pair<String, String> pair : al) {
//                // don't want to cache any EMPTY card
//                if (!EMPTY.equals(pair.first))
//                    alCopy.add(pair);
//            }

            // clear array in adapter
            al.clear();

//            COUNT_IN_THIS_SCAN = 0;
            // now start scan
            bluetoothLeScanner.startScan(scScanCallback);
            // set scan menu icon indicator on
            if (miScanMenuItem != null) // defensive, seen NPE if called without delay from onResume
                miScanMenuItem.setActionView(PROGRESS_BAR);

        } else {
            // stop scan
            bluetoothLeScanner.stopScan(scScanCallback);
            // set scan menu icon indicator off
            if (miScanMenuItem != null) // needed, saw NPE when doing rapid orientation change test
                miScanMenuItem.setActionView(null);

//            if (COUNT_IN_THIS_SCAN == 0) {
//                // restore array contents in adapter, MUST do element-copy and not pairList = copy
//                //  because then clearing copy is same as clearing pairList
//                for (Pair<String, String> pair : alCopy) {
//                    al.add(pair);
//                }
//            }

            if (al.size() == 0 && !inOnPause) {
                // no ble devices found and screen is not going away because we are in onPause()
                // TODO show dialog to ask a nearby friend with Lollipop device to download app with QR code
                makeToast(getBaseContext(), getResources().getString(R.string.empty),
                        Toast.LENGTH_LONG);
            }

            // assume stopScan is synchronous, so can start advertising
            advertiseLe(true);
        }
    }

    private Runnable rStopScanLeDevice = new Runnable() {
        @Override
        public void run() {
            scanLeDevice(false);
        }
    };

    // Device scan callback.
    private ScanCallback scScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);

            // get result to put in array adapter for swipe cards
            BluetoothDevice device = result.getDevice();
            final String resultDeviceName;
            if (device.getName() != null)
                resultDeviceName = device.getName();
            else
                resultDeviceName = ""+Math.abs(device.getAddress().hashCode());

            // check whether it's already in array adapter
            final Pair<String, String> pair = new Pair<>(resultDeviceName, device.getAddress());
            if (al.contains(pair))
                return;

            // ok add to array adapter
            // moved as much processing as possible up here to do first, and not in UI thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // defensive check against NPE seen in testing with orientation change
                    if (arrayAdapter != null) {
                        // update the arraylist and notify adapter of change
                        al.add(pair);
                        arrayAdapter.notifyDataSetChanged();
//                        COUNT_IN_THIS_SCAN++;
                        Log.d(LOG_TAG, "added " + resultDeviceName);
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
            Toast.makeText(getBaseContext(), ERR_SCAN_FAIL+" [ec="+errorCode+"]", Toast.LENGTH_LONG)
                    .show();
        }
    };


    /**
     * Start or stop advertising this device to other nearby BLE devices
     *
     * We should not stop advertise before starting scan because doing so has been observed
     * to change the device hardware (MAC) address seen by others doing scan for BLE devices.
     *
     * When we defensively re-start advertising, we see error code 3 in logcat
     * (ADVERTISE_FAILED_ALREADY_STARTED) returned by onStartFailure in the advertise callback,
     * but it seems to be benign.
     *
     * We start, and defensively re-start, advertising after we stop ble scan, and also in the
     * code where we periodically start ble scan.
     *
     * Method is called by a broadcast receiver (activated by repeating alarms), so must be
     * package-private.
     *
     *
     * @param enable  Set true to start and false to stop advertising.
     */
    void advertiseLe(final boolean enable) {
        if (mBluetoothLeAdvertiser == null) return;

        if (enable) {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setConnectable(true)
                    .setTimeout(0) // no timeout needed, advertise stopped by periodic scanning
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build();

            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(new ParcelUuid(DeviceProfile.SERVICE_UUID))
                    .build();

            mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);

        } else {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    /*
     * Callback handles events from the framework describing
     * if we were successful in starting the advertisement requests.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(LOG_TAG, "* Peripheral Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(LOG_TAG, "*** Peripheral Advertise Failed: "+errorCode);
            Toast.makeText(getBaseContext(), ERR_ADVERTISE_FAIL+" [ec="+errorCode+"]", Toast.LENGTH_LONG)
                    .show();
        }
    };


    /*
     * Callback handles GATT client events, such as results from connecting
     * (or reading or writing a characteristic value on the server).
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(LOG_TAG, "BluetoothGattCallback onConnectionStateChange "
                    + " status:" + status + " newState:" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //gatt.discoverServices(); // normally called here, but not needed by our use case
                bgConnectedGatt.disconnect(); // note: gatt==bgConnectedGatt (same reference)

            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // cannot do gatt.close() after disconnect() in STATE_CONNECTED if-branch
                //  otherwise see NPE BluetoothGattCallback.onConnectionStateChange
                bgConnectedGatt.close();
            }
        }
    };


    /*
     * Callback handles all incoming requests from GATT clients.
     * From connections to read/write requests.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(LOG_TAG, "BluetoothGattServerCallback onConnectionStateChange "
                    + " status:" + status + " newState:" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                ttsTextToSpeech.speak(YOBLE, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                // TODO send custom notification if possible when in background with screen off??
                Log.d(LOG_TAG, "***** YOBLE from "+"device address:"+device.getAddress());

                // Need to cancel connection explicitly here, otherwise see disconnect take 30-60s
                //  even though gatt client calls gatt.disconnect() once connection is established;
                //  cancelling allows us to do another device.connectGatt() almost immediately
                mGattServer.cancelConnection(device);
                Log.d(LOG_TAG, "* cancelConnection to device");
            }
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ble_devices, menu);
        miScanMenuItem = menu.findItem(R.id.action_ble_scan);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_ble_scan:
                scanLeDevice(true);
                return true;
            case R.id.action_finish_app:
                finish();
            case R.id.action_settings:
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        // After app is launched from Home or Recents, want Back to cause app pause and not destroy
        //  so we handle this callback ourselves and simply go to Home screen
        startActivity(new Intent (Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
