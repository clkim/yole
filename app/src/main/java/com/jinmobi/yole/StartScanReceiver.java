package com.jinmobi.yole;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * Repeating alarms broadcast to do ble scan as well as to ensure device continues to do
 * ble advertise in background
 *
 * There seems to be some evidence Nexus 9 will keep advertising in background even after screen off
 * for power save (Radius Networks' QuickBeacon app), but we can't be sure at this time that other
 * devices have same behavior. So we defensively start to advertise ble periodically. We don't stop
 * then re-start advertise ble because that changes the device hardware address seen by others.
 *
 * We skip scan to update the swipe cards UI if screen is off (not ready for interaction).
 *
 * TODO use LocalBroadcastManager
 * TODO try delayed handler posts to do periodic scan if device models all do advertise in background
 *
 * Created by clkim on 12/30/14.
 */
public class StartScanReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = StartScanReceiver.class.getSimpleName();

    private BLeDevicesActivity activity;

    public void setBLeDevicesActivity(BLeDevicesActivity blda) {
        activity = blda;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Log.d(LOG_TAG, "* in onReceive()");
        // if "device is awake and ready to interact with the user..." and not in power save (likely)
        if (pm.isInteractive() && !pm.isPowerSaveMode()) {
            activity.scanLeDevice(true); // method is package-private to allow call from this class
            Log.d(LOG_TAG, "* scan BLE device called");
        } else {
            // just in case advertise could be stopped for some reason, we defensively re-start it
            //  see comment in the ble advertise method about what seems to be a benign
            //  ADVERTISE_FAILED_ALREADY_STARTED error code 3 seen in logcat.
            activity.advertiseLe(true); // method is package-private to allow call from this class
            Log.d(LOG_TAG, "* advertise BLE called");
        }
    }
}
