package com.jinmobi.yole;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
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
            activity.scanLeDevice(true);
            Log.d(LOG_TAG, "* scan BLE device called");
        } else {
            // just in case advertise could be stopped for some reason, we start it
            activity.advertiseLe(false); // needed: probably most of time already doing advertise
            activity.advertiseLe(true);
            Log.d(LOG_TAG, "* advertise BLE called");
        }
    }
}
