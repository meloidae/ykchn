package dev.meloidae.ykchn


import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.meloidae.ykchn.IntentConstants.REQUEST_BLUETOOTH_ENABLE

import java.util.*
import kotlin.experimental.and


object BleUtils {
    object Hid {
        const val INPUT_REPORT = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
        const val OUTPUT_REPORT = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        const val FEATURE_REPORT = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE

        /**
         * Device Information Service
         */
        val SERVICE_DEVICE_INFORMATION = BleUtils.Uuid.fromShort(0x180A)
        val CHARACTERISTIC_MANUFACTURER_NAME = BleUtils.Uuid.fromShort(0x2A29)
        val CHARACTERISTIC_MODEL_NUMBER = BleUtils.Uuid.fromShort(0x2A24)
        val CHARACTERISTIC_SERIAL_NUMBER = BleUtils.Uuid.fromShort(0x2A25)
        val CHARACTERISTIC_PNP_ID = BleUtils.Uuid.fromShort(0x2A50)
        val DEVICE_INFO_MAX_LENGTH = 20

        /**
         * Battery Service
         */
        val SERVICE_BATTERY = BleUtils.Uuid.fromShort(0x180F)
        val CHARACTERISTIC_BATTERY_LEVEL = BleUtils.Uuid.fromShort(0x2A19)

        /**
         * HID Service
         */
        val SERVICE_BLE_HID = BleUtils.Uuid.fromShort(0x1812)
        val CHARACTERISTIC_HID_INFORMATION = BleUtils.Uuid.fromShort(0x2A4A)
        val CHARACTERISTIC_REPORT_MAP = BleUtils.Uuid.fromShort(0x2A4B)
        val CHARACTERISTIC_HID_CONTROL_POINT = BleUtils.Uuid.fromShort(0x2A4C)
        val CHARACTERISTIC_REPORT = BleUtils.Uuid.fromShort(0x2A4D)
        val CHARACTERISTIC_PROTOCOL_MODE = BleUtils.Uuid.fromShort(0x2A4E)

        /**
         * Gatt Characteristic Descriptor
         */
        val DESCRIPTOR_REPORT_REFERENCE = BleUtils.Uuid.fromShort(0x2908)
        val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUtils.Uuid.fromShort(0x2902)

        /**
         * onCharacteristicRead reponse
         */
        val EMPTY_BYTES: ByteArray = byteArrayOf()
        val RESPONSE_HID_INFORMATION: ByteArray = byteArrayOf(0x11, 0x01, 0x00, 0x03)
        val RESPONSE_PNP_ID: ByteArray = byteArrayOf(0x02, 0x02, 0xe5.toByte(), 0x11, 0xa1.toByte(), 0x10, 0x02)

        /**
         * Main items
         */
        fun INPUT(size: Int): Byte {
            return (0x80 or size).toByte()
        }

        fun OUTPUT(size: Int): Byte {
            return (0x90 or size).toByte()
        }

        fun COLLECTION(size: Int): Byte {
            return (0xA0 or size).toByte()
        }

        fun FEATURE(size: Int): Byte {
            return (0xB0 or size).toByte()
        }

        fun END_COLLECTION(size: Int): Byte {
            return (0xC0 or size).toByte()
        }

        /**
         * Global items
         */
        fun USAGE_PAGE(size: Int): Byte {
            return (0x04 or size).toByte()
        }
        fun LOGICAL_MINIMUM(size: Int): Byte {
            return (0x14 or size).toByte()
        }
        fun LOGICAL_MAXIMUM(size: Int): Byte {
            return (0x24 or size).toByte()
        }
        fun PHYSICAL_MINIMUM(size: Int): Byte {
            return (0x34 or size).toByte()
        }
        fun PHYSICAL_MAXIMUM(size: Int): Byte {
            return (0x44 or size).toByte()
        }
        fun UNIT_EXPONENT(size: Int): Byte {
            return (0x54 or size).toByte()
        }
        fun UNIT(size: Int): Byte {
            return (0x64 or size).toByte()
        }
        fun REPORT_SIZE(size: Int): Byte {
            return (0x74 or size).toByte()
        }
        fun REPORT_ID(size: Int): Byte {
            return (0x84 or size).toByte()
        }
        fun REPORT_COUNT(size: Int): Byte {
            return (0x94 or size).toByte()
        }

        /**
         * Local items
         */
        fun USAGE(size: Int): Byte {
            return (0x08 or size).toByte()
        }
        fun USAGE_MINIMUM(size: Int): Byte {
            return (0x18 or size).toByte()
        }
        fun USAGE_MAXIMUM(size: Int): Byte {
            return (0x28 or size).toByte()
        }

        fun LSB(value: Int): Byte {
            return (value and 0xff).toByte()
        }
        fun MSB(value: Int): Byte {
            return (value shr 8 and 0xff).toByte()
        }


        fun PUSH(size: Int): Byte {
            return (0xa4 or size).toByte()
        }

        fun POP(size: Int): Byte {
            return (0xb4 or size).toByte()
        }
    }

    object Uuid {
        private const val BASE_PREFIX = "0000"
        private const val BASE_POSTFIX = "0000-1000-8000-00805F9B34FB"
        val DEADBEEF: UUID = UUID.fromString("DEADBEEF-0000-0000-0000-000000000000")

        fun fromShort(uuidShort: Int): UUID {
            val shortStr = "%04X".format(uuidShort and 0xffff)
            return UUID.fromString("${BASE_PREFIX}$shortStr-${BASE_POSTFIX}")
        }

        fun isShort(src: UUID): Boolean {
            // -0xffff_0000_0001L == 0xffff_0000_ffff_ffffL
            return src.mostSignificantBits and -0xffff_0000_0001 == 0L && src.leastSignificantBits == 0L
        }

        fun matches(src: UUID, dst: UUID): Boolean {
            if (isShort(src) || isShort(dst)) {
                val srcShortUuid = src.mostSignificantBits and 0x0000_ffff_0000_0000L
                val dstShortUuid = dst.mostSignificantBits and 0x0000_ffff_0000_0000L
                return srcShortUuid == dstShortUuid
            }
            return src == dst
        }
    }

    val DEADBEAF_ARRAY = byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
    private val HEX_CHAR_ARRAY = "0123456789ABCDEF".toCharArray()


    fun byteArrayToHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val byteInt = (bytes[i] and 0xff.toByte()).toInt()
            hexChars[i * 2] = HEX_CHAR_ARRAY[byteInt ushr 4]
            hexChars[i * 2 + 1] = HEX_CHAR_ARRAY[byteInt and 0x0f]
        }
        return String(hexChars)
    }

    /**
     * Check if Bluetooth LE device supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    fun isBleSupported(context: Context): Boolean {
        try {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return false
            }

            val btManager =  context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

            val btAdapter = btManager.adapter

            if (btAdapter != null) {
                return true
            }
        } catch (ignored: Throwable) {
            // ignore exception
        }
        return false
    }

    /**
     * Check if Bluetooth LE Peripheral mode supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    fun isBlePeripheralSupported(context: Context): Boolean {
        val btAdapter =  (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?:
        return false

        return btAdapter.isMultipleAdvertisementSupported
    }

    /**
     * Check if bluetooth function enabled
     *
     * @param context the context
     * @return true if bluetooth enabled
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        val btManager =  context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter = btManager.adapter ?: return false

        return btAdapter.isEnabled
    }

    /**
     * Enables bluetooth function.<br />
     * the Activity may implement the `onActivityResult` method with the request code `REQUEST_CODE_BLUETOOTH_ENABLE`.
     *
     * @param activity the activity
     */
    fun enableBluetooth(activity: Activity) {
        activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_BLUETOOTH_ENABLE)
    }

}