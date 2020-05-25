package dev.meloidae.ykchn

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_BATTERY_LEVEL
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_HID_CONTROL_POINT
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_HID_INFORMATION
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_MANUFACTURER_NAME
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_MODEL_NUMBER
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_PNP_ID
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_PROTOCOL_MODE
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_REPORT
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_REPORT_MAP
import dev.meloidae.ykchn.BleUtils.Hid.CHARACTERISTIC_SERIAL_NUMBER
import dev.meloidae.ykchn.BleUtils.Hid.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION
import dev.meloidae.ykchn.BleUtils.Hid.DESCRIPTOR_REPORT_REFERENCE
import dev.meloidae.ykchn.BleUtils.Hid.DEVICE_INFO_MAX_LENGTH
import dev.meloidae.ykchn.BleUtils.Hid.EMPTY_BYTES
import dev.meloidae.ykchn.BleUtils.Hid.FEATURE_REPORT
import dev.meloidae.ykchn.BleUtils.Hid.INPUT_REPORT
import dev.meloidae.ykchn.BleUtils.Hid.OUTPUT_REPORT
import dev.meloidae.ykchn.BleUtils.Hid.RESPONSE_HID_INFORMATION
import dev.meloidae.ykchn.BleUtils.Hid.RESPONSE_PNP_ID
import dev.meloidae.ykchn.BleUtils.Hid.SERVICE_BATTERY
import dev.meloidae.ykchn.BleUtils.Hid.SERVICE_BLE_HID
import dev.meloidae.ykchn.BleUtils.Hid.SERVICE_DEVICE_INFORMATION
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.timerTask

abstract class HidPeripheral(
    context: Context,
    needInputReport: Boolean,
    needOutputReport: Boolean,
    needFeatureReport: Boolean,
    sendDataRate: Int
) {
    companion object {
        private val TAG = HidPeripheral::class.java.simpleName
    }

    private var manufacturer = "meloidae.dev"
    private var deviceName = "BLE HID"
    private var serialNumber = "12345678"

    private val context: Context = context.applicationContext
    protected val handler: Handler = Handler(this.context.mainLooper)
    private var btManager: BluetoothManager? = null
    private val btLeAdvertiser: BluetoothLeAdvertiser?
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null
    protected var gattServer: BluetoothGattServer? = null
    protected val btDevicesMap: MutableMap<String, BluetoothDevice> = mutableMapOf()
    private val inputReportQueue: Queue<ByteArray> = ConcurrentLinkedQueue()
    private val servicesToAdd: Queue<BluetoothGattService> = LinkedBlockingQueue()
    private var deviceConnectionCallback: HidDeviceConnectionListener? = null
    // private var targetAddress: String = ""
    private var targetDevice: BluetoothDevice? = null

    /**
     * Callback for BLE connection
     * nothing to do.
     */
    private val advertiseCallback = object : AdvertiseCallback() {}

    /**
     * Callback for gattServer
     */
    private val gattServerCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                if (gattServer == null) {
                    return
                }
                if (characteristic == null) {
                    Log.d(TAG, "onCharacteristicReadRequest characteristic is null")
                } else {
                    Log.d(
                        TAG,
                        "onCharacteristicReadRequest characteristic: ${characteristic.uuid}" + ", offset: $offset"
                    )
                }
                handler.post {
                    val characteristicUuid = characteristic?.uuid ?: BleUtils.Uuid.DEADBEEF
                    if (BleUtils.Uuid.matches(CHARACTERISTIC_HID_INFORMATION, characteristicUuid)) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            RESPONSE_HID_INFORMATION
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_REPORT_MAP,
                            characteristicUuid
                        )
                    ) {
                        Log.d(TAG, "onCharacteristicReadRequest offset: $offset")
                        if (offset == 0) {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                getReportMap()
                            )
                        } else {
                            val remainLength = getReportMap().size - offset
                            Log.d(
                                TAG,
                                "onCharacteristicReadRequest getReportMap().size: ${getReportMap().size}"
                            )
                            Log.d(TAG, "onCharacteristicReadRequest remainLength: $remainLength")
                            if (remainLength > 0) {
                                val data = ByteArray(remainLength)
                                System.arraycopy(getReportMap(), offset, data, 0, remainLength)
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    offset,
                                    data
                                )
                            } else {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    offset,
                                    null
                                )
                            }
                        }
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_HID_CONTROL_POINT,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            byteArrayOf(1)
                        )
                    } else if (BleUtils.Uuid.matches(CHARACTERISTIC_REPORT, characteristicUuid)) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            EMPTY_BYTES
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_MANUFACTURER_NAME,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            manufacturer.toByteArray(Charsets.UTF_8)
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_SERIAL_NUMBER,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            serialNumber.toByteArray(Charsets.UTF_8)
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_MODEL_NUMBER,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            deviceName.toByteArray(Charsets.UTF_8)
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_PNP_ID,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            RESPONSE_PNP_ID
                        )
                    } else if (BleUtils.Uuid.matches(
                            CHARACTERISTIC_BATTERY_LEVEL,
                            characteristicUuid
                        )
                    ) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            getBatteryStatus()
                        )
                    } else {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            characteristic?.value
                        )
                    }
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                Log.d(
                    TAG, "onCharacteristicWriteRequest characteristic: ${characteristic?.uuid}" +
                            ", value: ${value.toString()}"
                )
                if (gattServer == null || value == null) {
                    return
                }
                val characteristicUuid = characteristic?.uuid ?: BleUtils.Uuid.DEADBEEF
                if (responseNeeded) {
                    if (BleUtils.Uuid.matches(CHARACTERISTIC_REPORT, characteristicUuid)) {
                        if (characteristic?.properties == OUTPUT_REPORT) {
                            // Output report
                            onOutputReport(value)
                        }
                        // Send empty response
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            EMPTY_BYTES
                        )
                    }
                }
            }

            override fun onConnectionStateChange(
                device: BluetoothDevice?,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(device, status, newState)
                Log.d(TAG, "onConnectionStateChange status: $status, newState: $newState")
                when (newState) {
                    BluetoothProfile.STATE_CONNECTING -> {
                        Log.d(
                            TAG,
                            "BluetoothProfile.STATE_CONNECTED bondState: ${device?.bondState}"
                        )
                    }
                    BluetoothProfile.STATE_CONNECTED -> {
                        // Check bond status
                        Log.d(
                            TAG,
                            "BluetoothProfile.STATE_CONNECTED bondState: ${device?.bondState}"
                        )
                        if (device?.bondState == BluetoothDevice.BOND_NONE) {
                            val bcReceiver = object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    val action = intent.action
                                    Log.d(TAG, "onReceive action: $action")
                                    if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                                        val state = intent.getIntExtra(
                                            BluetoothDevice.EXTRA_BOND_STATE,
                                            BluetoothDevice.ERROR
                                        )
                                        if (state == BluetoothDevice.BOND_BONDED) {
                                            val bondedDevice =
                                                intent.getParcelableExtra<BluetoothDevice>(
                                                    BluetoothDevice.EXTRA_DEVICE
                                                )
                                            // Successfully bonded, so unregister receiver
                                            Log.d(TAG, "Successfully bonded")
                                            context.unregisterReceiver(this)
                                            handler.post {
                                                if (gattServer != null) {
                                                    // gattServer?.connect(device, true)
                                                    // Log.d(
                                                    //     TAG,
                                                    //     "onReceive() Connected to device $bondedDevice"
                                                    // )
                                                    synchronized(btDevicesMap) {
                                                        btDevicesMap[bondedDevice.address] =
                                                            bondedDevice
                                                    }
                                                    deviceConnectionCallback?.onDeviceConnected(
                                                        bondedDevice
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            context.registerReceiver(
                                bcReceiver,
                                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                            )
                        } else if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                            handler.post {
                                // if (gattServer != null) {
                                //     gattServer?.connect(device, true)
                                //     Log.d(TAG, "Connected to device $device")
                                // }
                            }
                            synchronized(btDevicesMap) {
                                btDevicesMap[device.address] = device
                            }
                            deviceConnectionCallback?.onDeviceConnected(device)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(
                            TAG,
                            "BluetoothProfile.STATE_DISCONNECTED bondState: ${device?.bondState}"
                        )
                        if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                            // Try to reconnect immediately
                            handler.post {
                                if (gattServer != null) {
                                    gattServer?.connect(device, true)
                                    Log.d(TAG, "Connected to device $device")
                                }
                            }
                            // Remove device from map
                            Log.d(TAG, "Removing device $device")
                            synchronized(btDevicesMap) {
                                btDevicesMap.remove(device.address)
                            }
                            deviceConnectionCallback?.onDeviceDisconnected(device)
                        }
                    }
                }
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor?
            ) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor)
                Log.d(
                    TAG, "onDescriptorReadRequest requestId: $requestId" +
                            ", offset: $offset, descriptor: ${descriptor?.uuid}"
                )
                if (gattServer == null) {
                    return
                }
                handler.post {
                    val descriptorUuid = descriptor?.uuid ?: BleUtils.Uuid.DEADBEEF
                    if (BleUtils.Uuid.matches(DESCRIPTOR_REPORT_REFERENCE, descriptorUuid)) {
                        Log.d(
                            TAG,
                            "DESCRIPTOR_REPORT_REFERENCE properties: ${descriptor?.characteristic?.properties}"
                        )
                        when (descriptor?.characteristic?.properties) {
                            INPUT_REPORT -> {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    byteArrayOf(0x0, 0x1)
                                )
                            }
                            OUTPUT_REPORT -> {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    byteArrayOf(0x0, 0x2)
                                )
                            }
                            FEATURE_REPORT -> {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    byteArrayOf(0x0, 0x3)
                                )
                            }
                            else -> {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    0,
                                    EMPTY_BYTES
                                )
                            }
                        }
                    } else {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            EMPTY_BYTES
                        )
                    }
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onDescriptorWriteRequest(
                    device,
                    requestId,
                    descriptor,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                Log.d(
                    TAG, "onDescriptorWriteRequest descriptor: ${descriptor?.uuid}" +
                            ", value: ${BleUtils.byteArrayToHexString(
                                value ?: BleUtils.DEADBEAF_ARRAY
                            )}, responseNeeded: $responseNeeded" +
                            ", preparedWrite: $preparedWrite"
                )
                descriptor?.value = value
                if (responseNeeded) {
                    val descriptorUuid = descriptor?.uuid ?: BleUtils.Uuid.DEADBEEF
                    if (BleUtils.Uuid.matches(
                            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                            descriptorUuid
                        )
                    ) {
                        // Send empty response
                        if (gattServer != null) {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                EMPTY_BYTES
                            )
                        }
                    }
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                super.onServiceAdded(status, service)
                Log.d(TAG, "onServiceAdd status: $status, service: ${service?.uuid}")
                if (status != 0) {
                    Log.d(TAG, "onServiceAdd Adding service failed")
                }
                if (servicesToAdd.peek() != null) {
                    addService(servicesToAdd.remove())
                } else {
                    // Finished adding all services. Broadcast that peripheral is ready
                    val bcManager = LocalBroadcastManager.getInstance(context)
                    val intent = Intent(IntentConstants.ACTION_HID_PERIPHERAL_READY)
                    bcManager.sendBroadcast(intent)
                }
            }

        }

    /**
     * Represents Report Map byte array
     * @return Report Map data
     */
    protected abstract fun getReportMap(): ByteArray

    /**
     * Battery Status in byte array
     */
    protected open fun getBatteryStatus(): ByteArray {
        // Return 100%
        val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return byteArrayOf(batteryLevel.toByte())
    }

    /**
     * HID Input Report
     */
    protected fun addInputReport(inputReport: ByteArray?) {
        if (inputReport == null) {
            Log.d(TAG, "addInputReport inputReport is null")
        } else if (inputReport.isNotEmpty()) {
            // Log.d(TAG, "Adding inputReport: ${BleUtils.byteArrayToHexString(inputReport)}")
            inputReportQueue.offer(inputReport)
        }
    }

    /**
     * HID Output Report
     *
     * @param outputReport the report data
     */
    protected abstract fun onOutputReport(outputReport: ByteArray)

    /**
     * Add GATT service to gattServer
     *
     * @param service the service
     */
    private fun addService(service: BluetoothGattService) {
        var serviceAdded = false
        while (!serviceAdded) {
            try {
                Thread.sleep(500)
                serviceAdded = gattServer?.addService(service) ?: false
            } catch (e: Exception) {
                Log.d(TAG, "Adding Service failed", e)
            }
        }
        Log.d(TAG, "Service: ${service.uuid} added.")
    }

    /**
     * Setup Device Information Service
     *
     * @return the service
     */
    private fun setUpDeviceInformationService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_DEVICE_INFORMATION,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        // Add Manufacturer Name
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_MANUFACTURER_NAME,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            service.addCharacteristic(characteristic)
        }
        // Add Model Number
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_MODEL_NUMBER,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            service.addCharacteristic(characteristic)
        }
        // Add Serial Number
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_SERIAL_NUMBER,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            service.addCharacteristic(characteristic)
        }

        // Add PnP ID
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_PNP_ID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            service.addCharacteristic(characteristic)
        }

        return service
    }

    /**
     * Setup Battery Service
     *
     * @return the service
     */
    private fun setUpBatteryService(): BluetoothGattService {
        val service =
            BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Battery Level
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_BATTERY_LEVEL,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )

        val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        clientCharacteristicConfigurationDescriptor.value =
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)

        service.addCharacteristic(characteristic)

        return service
    }

    /**
     * Setup HID Service
     *
     * @param needInputReport true: serves 'Input Report' BLE characteristic
     * @param needOutputReport true: serves 'Output Report' BLE characteristic
     * @param needFeatureReport true: serves 'Feature Report' BLE characteristic
     * @return the service
     */
    private fun setUpHidService(
        needInputReport: Boolean,
        needOutputReport: Boolean,
        needFeatureReport: Boolean
    ): BluetoothGattService {
        val service =
            BluetoothGattService(SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_INFORMATION,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )

            service.addCharacteristic(characteristic)
        }

        // Report Map
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )

            service.addCharacteristic(characteristic)
        }

        // Protocol Mode
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            service.addCharacteristic(characteristic)
        }

        // HID Control Point
        run {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            service.addCharacteristic(characteristic)
        }

        // Input Report
        if (needInputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )

            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            //  | BluetoothGattDescriptor.PERMISSION_WRITE
            clientCharacteristicConfigurationDescriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)

            val reportReferenceDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                        or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(reportReferenceDescriptor)

            service.addCharacteristic(characteristic)
            inputReportCharacteristic = characteristic
        }

        // Output Report
        if (needOutputReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)

            service.addCharacteristic(characteristic)
        }

        // Feature Report
        if (needFeatureReport) {
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )

            val descriptor = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                        BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            characteristic.addDescriptor(descriptor)

            service.addCharacteristic(characteristic)
        }

        return service
    }

    /**
     * Try to connect to a given remote device
     */
    fun connect(device: BluetoothDevice) {
        // Log.d(TAG, "Number of connected devices: ${btManager?.getConnectedDevices(BluetoothProfile.GATT)}")
        // gattServer?.connect(device, true)
        // device.createBond()
    }

    fun disconnect(device: BluetoothDevice) {
        handler.post {
            try {
                btLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (e: IllegalStateException) {
                Log.d(TAG, "BluetoothAdapter is not turned on")
            }
            gattServer?.cancelConnection(device)
        }
    }

    /**
     * Starts advertising
     */
    fun startAdvertising() {
        handler.post {
            // Set up advertising setting
            val advertiseSettings = AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build()

            // Set up advertising data
            val advertiseData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
                .build()

            // set up scan result
            val scanResult = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
                .build()

            Log.d(TAG, "advertiseData: $advertiseData, scanResult: $scanResult")
            btLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseData,
                scanResult,
                advertiseCallback
            )
        }
    }

    /**
     * Stops Advertising
     */
    fun stopAdvertising() {
        handler.post {
            try {
                btLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (e: IllegalStateException) {
                Log.d(TAG, "BluetoothAdapter is not turned on")
            }
            try {
                if (gattServer != null) {
                    val devices = getDevices()
                    for (device in devices.values) {
                        gattServer?.cancelConnection(device)
                    }
                    gattServer?.close()
                    gattServer = null
                }
            } catch (e: IllegalStateException) {
                Log.d(TAG, "BluetoothAdapter is not turned on")
            }
        }
    }

    /**
     * Obtains isConnected Bluetooth devices
     *
     * @return the isConnected Bluetooth devices
     */
    private fun getDevices(): Map<String, BluetoothDevice> {
        val deviceMap: MutableMap<String, BluetoothDevice> = mutableMapOf()
        synchronized(btDevicesMap) {
            deviceMap.putAll(btDevicesMap)
        }
        return Collections.unmodifiableMap(deviceMap)
    }

    /**
     * Set the manufacturer name
     *
     * @param newManufacturer the name
     */
    fun setManufacturer(newManufacturer: String) {
        // length check
        val manufacturerBytes = newManufacturer.toByteArray(Charsets.UTF_8)
        if (manufacturerBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(manufacturerBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH)
            manufacturer = String(bytes, Charsets.UTF_8)
            return
        }
        manufacturer = newManufacturer
    }

    /**
     * Set the device name
     *
     * @param newDeviceName the name
     */
    fun setDeviceName(newDeviceName: String) {
        // length check
        val deviceNameBytes = newDeviceName.toByteArray(Charsets.UTF_8)
        if (deviceNameBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH)
            deviceName = String(bytes, Charsets.UTF_8)
            return
        }
        deviceName = newDeviceName
    }

    /**
     * Set the serial number
     *
     * @param newSerialNumber the number
     */
    fun setSerialNumber(newSerialNumber: String) {
        // length check
        val deviceNameBytes = newSerialNumber.toByteArray(Charsets.UTF_8)
        if (deviceNameBytes.size > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            val bytes = ByteArray(DEVICE_INFO_MAX_LENGTH)
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH)
            serialNumber = String(bytes, Charsets.UTF_8)
            return
        }
        serialNumber = newSerialNumber
    }


    fun setHidDeviceConnectionListener(callback: HidDeviceConnectionListener) {
        this.deviceConnectionCallback = callback
    }

    // fun setTargetDeviceAddress(deviceAddress: String) {
    //     synchronized(targetAddress) {
    //         targetAddress = deviceAddress
    //     }
    // }
    fun setTargetDevice(device: BluetoothDevice) {
        targetDevice = device
    }

    fun sendLocalBroadcast(intent: Intent) {
        val bcManager = LocalBroadcastManager.getInstance(context)
        bcManager.sendBroadcast(intent)
    }

    init {

        // Check for runtime permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw UnsupportedOperationException("Permission NOT granted for ACCESS_FINE_LOCATION")
        }

        btManager = this.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter: BluetoothAdapter? = btManager?.adapter
        if (btAdapter == null) {
            // Device doesn't support bluetooth
            throw UnsupportedOperationException("Device doesn't support Bluetooth")
        }

        if (!btAdapter.isEnabled) {
            // Bluetooth is disabled
            throw UnsupportedOperationException("Bluetooth is disabled")
        }

        if (!btAdapter.isMultipleAdvertisementSupported) {
            throw UnsupportedOperationException("Device doesn't support Multiple Advertisement")
        }

        this.btLeAdvertiser = btAdapter.bluetoothLeAdvertiser
        if (btLeAdvertiser == null) {
            throw UnsupportedOperationException("Device doesn't support Bluetooth Advertising")
        } else {
            Log.d(TAG, "BluetoothAdvertiser: $btLeAdvertiser")
        }

        // Initialize gattServer
        this.gattServer = btManager?.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            throw UnsupportedOperationException("gattServer is null, check if Bluetooth is ON")
        }

        // Set up services
        servicesToAdd.add(setUpDeviceInformationService())
        servicesToAdd.add(setUpBatteryService())
        addService(setUpHidService(needInputReport, needOutputReport, needFeatureReport))

        // Send report every sendDataRate if data is available
        Timer().scheduleAtFixedRate(timerTask {
            val polled = inputReportQueue.poll()
            if (polled != null && inputReportCharacteristic != null) {
                // Log.d(TAG, "Timer().scheduleAtFixedRate() polled")
                inputReportCharacteristic?.value = polled
                handler.post {
                    // var address = ""
                    // synchronized(targetAddress) {
                    //     address += targetAddress
                    // }
                    // val device = getDevices()[address]
                    val device = targetDevice
                    if (device != null) {
                        try {
                            if (gattServer != null) {
                                gattServer?.notifyCharacteristicChanged(
                                    device,
                                    inputReportCharacteristic,
                                    false
                                )
                            }
                        } catch (ignored: Throwable) {
                            Log.d(TAG, "Error on gattServer.notifyCharacteristicChanged")
                        }
                    }
                }
            }
        }, 0.toLong(), sendDataRate.toLong())
    }

    interface HidDeviceConnectionListener {
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected(device: BluetoothDevice)
    }
}


