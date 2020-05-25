package dev.meloidae.ykchn

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val TAG_DEVICE_LIST_FRAGMENT = "dev.meloidae.ykchn.TAG_DEVICE_LIST_FRAGMENT"
        private const val TAG_DEVICE_CONTROL_FRAGMENT = "dev.meloidae.ykchn.TAG_DEVICE_TAG_FRAGMENT"
    }

    private val deviceMap = IndexedHashMap<String, BleDeviceHolder>()
    private val deviceListFragment = DeviceListFragment(deviceMap)
    private val deviceControlFragment: DeviceControlFragment by lazy { initializeDeviceControlFragment() }
    private var peripheral: CompositePeripheral? = null
    private var deviceUpdateCount = 0

    private val hidDeviceConnectionListener = object : HidPeripheral.HidDeviceConnectionListener {
        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnected() device: ${device.address}")
            Log.d(TAG, "uuid: ${device.uuids}")
            val activeFragment = supportFragmentManager
                .findFragmentByTag(TAG_DEVICE_LIST_FRAGMENT) as DeviceListFragment?
            if (deviceMap.containsKey(device.address)) {
                // Device already exists, so just change isConnected to true
                deviceMap[device.address]?.isConnected = true
                when {
                    activeFragment == null -> // DeviceListFragment is inactive
                        deviceUpdateCount += 1
                    deviceUpdateCount == 0 -> // notify item change to DeviceListFragment
                        activeFragment.changeItem(deviceMap.getIndexOfKey(device.address))
                    else -> { // notify multiple changes to DeviceListFragment
                        activeFragment.changeDataSet()
                        deviceUpdateCount = 0
                    }
                }
            } else {
                // Newly add device to deviceMap
                deviceMap[device.address] = BleDeviceHolder(device, true)
                when {
                    activeFragment == null -> // DeviceListFragment is inactive
                        deviceUpdateCount += 1
                    deviceUpdateCount == 0 -> // notify item inserted to DeviceListFragment
                        activeFragment.insertItem(deviceMap.getIndexOfKey(device.address))
                    else -> { // notify multiple changes to DeviceListFragment
                        activeFragment.changeDataSet()
                        deviceUpdateCount = 0
                    }
                }
            }
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnected() device: ${device.address}")
            val activeFragment = supportFragmentManager
                .findFragmentByTag(TAG_DEVICE_LIST_FRAGMENT) as DeviceListFragment?
            if (deviceMap.containsKey(device.address)) {
                // Device already exists, so just change isConnected to false
                deviceMap[device.address]?.isConnected = false
                when {
                    activeFragment == null -> // DeviceListFragment is inactive
                        deviceUpdateCount += 1
                    deviceUpdateCount == 0 -> // notify item change to DeviceListFragment
                        activeFragment.changeItem(deviceMap.getIndexOfKey(device.address))
                    else -> { // notify multiple changes to DeviceListFragment
                        activeFragment.changeDataSet()
                        deviceUpdateCount = 0
                    }
                }
            } else {
                // Newly add device to deviceMap (with isConnected of false)
                deviceMap[device.address] = BleDeviceHolder(device, false)
                when {
                    activeFragment == null -> // DeviceListFragment is inactive
                        deviceUpdateCount += 1
                    deviceUpdateCount == 0 -> // notify item inserted to DeviceListFragment
                        activeFragment.insertItem(deviceMap.getIndexOfKey(device.address))
                    else -> { // notify multiple changes to DeviceListFragment
                        activeFragment.changeDataSet()
                        deviceUpdateCount = 0
                    }
                }
            }
        }
    }

    private val hidPeripheralReadyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "hidPeripheralReadyReceiver.onReceive() action: $action")
            if (action == IntentConstants.ACTION_HID_PERIPHERAL_READY) {
                val bcManager = LocalBroadcastManager.getInstance(this@MainActivity)
                bcManager.unregisterReceiver(this)
                // Set  up  deviceMap
                setUpDeviceMap()
                peripheral?.setHidDeviceConnectionListener(hidDeviceConnectionListener)
                peripheral?.startAdvertising()
                deviceListFragment.setDeviceListClickListener(deviceListClickListener)
            }
        }
    }

    private val deviceListClickListener = object : DeviceListAdapter.DeviceListClickListener {
        override fun onClick(view: View, position: Int) {
            when (view.id) {
                R.id.device_item_layout -> {
                    Log.d(TAG, "deviceListClickListener ListItem clicked")
                    val device = deviceMap.getByIndex(position)?.device
                    if (device != null) {
                        peripheral?.setTargetDevice(device)
                        switchFragment(deviceControlFragment, TAG_DEVICE_CONTROL_FRAGMENT)
                    }
                }
                R.id.device_connect_button -> {
                    Log.d(TAG, "deviceListClickListener ConnectButton clicked")
                    val device = deviceMap.getByIndex(position)?.device
                    if (device != null) {
                        // peripheral?.setTargetDevice(device)
                        // peripheral?.connect(device)
                        // peripheral?.disconnect(device)
                        peripheral?.disconnect(device)
                        // peripheral?.stopAdvertising()
                        // peripheral?.mouseMovePointer(50, 50, 0, false, false, false)
                    } else {
                        Log.d(TAG, "Given remote device is null")
                    }
                }
            }
        }
    }

    private val deviceControlTouchListener: MouseGestureListener by lazy {
        object : MouseGestureListener(this@MainActivity) {
            override fun onClick(pointerCount: Int) {
                Log.d(TAG, "onClick() pointerCount: $pointerCount")
                when (pointerCount) {
                    1 -> {
                        peripheral?.mouseMovePointer(0, 0, 0, 0,
                            leftButton = true, rightButton = false, middleButton = false)
                    }
                    2 -> {
                        peripheral?.mouseMovePointer(0, 0, 0, 0,
                            leftButton = false, rightButton = true, middleButton = false)
                    }
                    3 -> {
                        peripheral?.mouseMovePointer(0, 0, 0, 0,
                            leftButton = false, rightButton = false, middleButton = true)
                    }
                }
                peripheral?.mouseMovePointer(0, 0, 0, 0,
                    leftButton = false, rightButton = false, middleButton = false)
            }

            override fun onCursorMove(dx: Int, dy: Int) {
                peripheral?.mouseMovePointer(dx, dy, 0, 0,
                    leftButton = false, rightButton = false, middleButton = false)
            }

            override fun onDoubleClick(pointerCount: Int) {
                Log.d(TAG, "onDoubleClick() pointerCount: $pointerCount")
                onClick(pointerCount)
                onClick(pointerCount)
            }

            override fun onDragStart() {
                Log.d(TAG, "onDragStart()")
                peripheral?.mouseMovePointer(0, 0, 0, 0,
                    leftButton = true, rightButton = false, middleButton = false)
            }

            override fun onDragEnd() {
                Log.d(TAG, "onDragEnd()")
                peripheral?.mouseMovePointer(0, 0, 0, 0,
                    leftButton = false, rightButton = false, middleButton = false)
            }

            override fun onDragMove(dx: Int, dy: Int) {
                peripheral?.mouseMovePointer(dx, dy, 0, 0,
                    leftButton = true, rightButton = false, middleButton = false)
            }

            override fun onScroll(dx: Int, dy: Int) {
                peripheral?.mouseMovePointer(0, 0, dy, dx,
                    leftButton = false, rightButton = false, middleButton = false)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bcManager = LocalBroadcastManager.getInstance(this)
        val filter = IntentFilter(IntentConstants.ACTION_HID_PERIPHERAL_READY)
        bcManager.registerReceiver(hidPeripheralReadyReceiver, filter)
        setUpBluetooth()
        switchFragment(deviceListFragment, TAG_DEVICE_LIST_FRAGMENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        val bcManager = LocalBroadcastManager.getInstance(this)
        bcManager.unregisterReceiver(hidPeripheralReadyReceiver)
        peripheral?.stopAdvertising()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Check request code
        when (requestCode) {
            IntentConstants.REQUEST_BLUETOOTH_ENABLE -> {
                // Make sure the request was successful
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(TAG, "Failed to enable Bluetooth")
                    finish()
                }
                if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
                    Log.d(TAG, "Device doesn't support BLE")
                    finish()
                } else {
                    initializeBlePeripheral()
                }
            }
            IntentConstants.REQUEST_ACCESS_FINE_LOCATION -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "ACCESS_FINE_LOCATION has been enabled")
                } else {
                    Log.d(TAG, "Failed to enable ACCESS_FINE_LOCATION")
                    finish()
                }
            }
        }
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.container, fragment, tag).addToBackStack(null)
        transaction.commit()
    }

    private fun initializeBlePeripheral() {
        peripheral = CompositePeripheral(
            this,
            CompositePeripheral.MOUSE or CompositePeripheral.KEYBOARD,
            10
        )
        peripheral?.mouseReverseVerticalWheelDirection = true
        peripheral?.mouseVerticalWheelScaling = 0.5
    }

    private fun setUpDeviceMap() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val devices = btManager.adapter.bondedDevices
        for (device in devices) {
            deviceMap[device.address] = BleDeviceHolder(device, false)
        }
        deviceListFragment.changeDataSet()
    }

    private fun setUpBluetooth() {

        // Set up runtime permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting permission for ACCESS_FINE_LOCATION")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                IntentConstants.REQUEST_ACCESS_FINE_LOCATION)
        }

        if (!BleUtils.isBluetoothEnabled(this)) {
            BleUtils.enableBluetooth(this)
            return
        }

        if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
            Log.d(TAG, "Device doesn't support BLE")
            finish()
        } else {
            initializeBlePeripheral()
        }
    }

    private fun initializeDeviceControlFragment(): DeviceControlFragment {
        // val detector = GestureDetector(this@MainActivity, deviceControlDoubleTapListener)
        // detector.setOnDoubleTapListener(deviceControlDoubleTapListener)
        // val listener = View.OnTouchListener { v, event -> detector.onTouchEvent(event) }
        // return DeviceControlFragment(listener)
        // return DeviceControlFragment(deviceControlTestListener)
        Log.d(TAG, "Default tapTimeout: ${deviceControlTouchListener.tapTimeout}")
        Log.d(TAG, "Default tapIntervalTimeout: ${deviceControlTouchListener.tapIntervalTimeout}")
        deviceControlTouchListener.tapIntervalTimeout = 100
        return DeviceControlFragment(deviceControlTouchListener)
    }

    init {
    }
}
