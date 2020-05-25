package dev.meloidae.ykchn

import android.bluetooth.BluetoothDevice

data class BleDeviceHolder(
    var device: BluetoothDevice,
    var isConnected: Boolean
) {
    var address: String? = device.address
        get() = device.address
        private set
    var name: String? = device.name
        get() = device.name
        private set
}
