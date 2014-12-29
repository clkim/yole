package com.jinmobi.yole;

import java.util.UUID;

/**
 * Constants for service/characteristic for this device's custom BLE peripheral role
 *
 * Created by clkim on 12/29/14.
 */
public class DeviceProfile {
    // Custom unique uuid's generated for this app by Mac command-line utility 'uuidgen' following:
    //  https://developer.apple.com/library/ios/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/PerformingCommonPeripheralRoleTasks/PerformingCommonPeripheralRoleTasks.html

    //Service UUID to expose our app
    static final String SERVICE_UUID_STRING = "554E5BAA-683E-4E1A-A937-770C2583DC0B";
    static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_STRING);
}
