package dev.meloidae.ykchn

import android.content.Context
import android.util.Log
import dev.meloidae.ykchn.BleUtils.Hid.COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.END_COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.INPUT
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.OUTPUT
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_COUNT
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_SIZE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_PAGE
import dev.meloidae.ykchn.KeyboardUtils.getKeyCode
import dev.meloidae.ykchn.KeyboardUtils.getModifier

class KeyboardPeripheral(
    context: Context
): HidPeripheral(context, true, true, false, 20) {
    companion object {
        private val TAG = KeyboardPeripheral::class.java.simpleName


        private const val KEY_PACKET_MODIFIER_KEY_INDEX = 0
        private const val KEY_PACKET_KEY_INDEX = 2

        private val EMPTY_REPORT = ByteArray(8)

    }

    private val REPORT_MAP = byteArrayOf(
        USAGE_PAGE(1),      0x01,       // Generic Desktop Ctrls
        USAGE(1),           0x06,       // Keyboard
        COLLECTION(1),      0x01,       // Application
        USAGE_PAGE(1),      0x07,       //   Kbrd/Keypad
        USAGE_MINIMUM(1),   0xE0.toByte(),
        USAGE_MAXIMUM(1),   0xE7.toByte(),
        LOGICAL_MINIMUM(1), 0x00,
        LOGICAL_MAXIMUM(1), 0x01,
        REPORT_SIZE(1),     0x01,       //   1 byte (Modifier)
        REPORT_COUNT(1),    0x08,
        INPUT(1),           0x02,       //   Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position
        REPORT_COUNT(1),    0x01,       //   1 byte (Reserved)
        REPORT_SIZE(1),     0x08,
        INPUT(1),           0x01,       //   Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position
        REPORT_COUNT(1),    0x05,       //   5 bits (Num lock, Caps lock, Scroll lock, Compose, Kana)
        REPORT_SIZE(1),     0x01,
        USAGE_PAGE(1),      0x08,       //   LEDs
        USAGE_MINIMUM(1),   0x01,       //   Num Lock
        USAGE_MAXIMUM(1),   0x05,       //   Kana
        OUTPUT(1),          0x02,       //   Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile
        REPORT_COUNT(1),    0x01,       //   3 bits (Padding)
        REPORT_SIZE(1),     0x03,
        OUTPUT(1),          0x01,       //   Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile
        REPORT_COUNT(1),    0x06,       //   6 bytes (Keys)
        REPORT_SIZE(1),     0x08,
        LOGICAL_MINIMUM(1), 0x00,
        LOGICAL_MAXIMUM(1), 0x65,       //   101 keys
        USAGE_PAGE(1),      0x07,       //   Keyboard/Keypad
        USAGE_MINIMUM(1),   0x00,
        USAGE_MAXIMUM(1),   0x65,
        INPUT(1),           0x00,       //   Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position
        END_COLLECTION(0)
    )

    override fun getReportMap(): ByteArray {
        return REPORT_MAP
    }

    override fun onOutputReport(outputReport: ByteArray) {
        Log.i(TAG, "onOutputReport data:  $outputReport")
    }

    /**
     * Send text to Central device
     * @param text the text to send
     */
    fun sendKeys(text: String) {
        // var lastKey: String? = null
        for (i in text.indices) {
            val key = text.substring(i, i + 1)
            val report = ByteArray(8)
            val modifier = getModifier(key)
            val keyCode = getKeyCode(key)
            Log.d(TAG, "sendKeys() key: $keyCode, modifier: $modifier")
            report[KEY_PACKET_MODIFIER_KEY_INDEX] = getModifier(key)
            report[KEY_PACKET_KEY_INDEX] = getKeyCode(key)

            // if (key == lastKey) {
            //     sendKeyUp()
            // }
            addInputReport(report)
            // lastKey = key
            sendKeyUp()
        }
        sendKeyUp()
    }

    /**
     * Send Key Down Event
     * @param modifier modifier key
     * @param keyCode key code
     */
    fun sendKeyDown(modifier: Byte, keyCode: Byte) {
        val report = ByteArray(8)
        report[KEY_PACKET_MODIFIER_KEY_INDEX] = modifier
        report[KEY_PACKET_KEY_INDEX] = keyCode

        addInputReport(report)
    }

    /**
     * Send Key Up Event
     */
    fun sendKeyUp() {
        addInputReport(EMPTY_REPORT)
    }

}

