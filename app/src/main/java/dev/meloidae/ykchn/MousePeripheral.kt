package dev.meloidae.ykchn

import android.content.Context
import dev.meloidae.ykchn.BleUtils.Hid.COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.END_COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.INPUT
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_COUNT
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_ID
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_SIZE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_PAGE
import kotlin.experimental.and
import kotlin.experimental.or

class MousePeripheral(
    context: Context
) : HidPeripheral(context, true, false, false, 10) {
    companion object {
        private val TAG = MousePeripheral::class.java.simpleName

        const val BYTE_ZERO = 0.toByte()
    }
    private val REPORT_MAP = byteArrayOf(
        USAGE_PAGE(1),      0x01,         // Generic Desktop
        USAGE(1),           0x02,         // Mouse
        COLLECTION(1),      0x01,         // Application
        USAGE(1),           0x01,         //  Pointer
        COLLECTION(1),      0x00,         //  Physical
        USAGE_PAGE(1),      0x09,         //   Buttons
        USAGE_MINIMUM(1),   0x01,
        USAGE_MAXIMUM(1),   0x03,
        LOGICAL_MINIMUM(1), 0x00,
        LOGICAL_MAXIMUM(1), 0x01,
        REPORT_COUNT(1),    0x03,         //   3 bits (Buttons)
        REPORT_SIZE(1),     0x01,
        INPUT(1),           0x02,         //   Data, Variable, Absolute
        REPORT_COUNT(1),    0x01,         //   5 bits (Padding)
        REPORT_SIZE(1),     0x05,
        INPUT(1),           0x01,         //   Constant
        USAGE_PAGE(1),      0x01,         //   Generic Desktop
        USAGE(1),           0x30,         //   X
        USAGE(1),           0x31,         //   Y
        USAGE(1),           0x38,         //   Wheel
        LOGICAL_MINIMUM(1), 0x81.toByte(),  //   -127
        LOGICAL_MAXIMUM(1), 0x7f,         //   127
        REPORT_SIZE(1),     0x08,         //   Three bytes
        REPORT_COUNT(1),    0x03,
        INPUT(1),           0x06,         //   Data, Variable, Relative
        END_COLLECTION(0),
        END_COLLECTION(0)
    )

    private val lastSent = ByteArray(4)

    override fun getReportMap(): ByteArray {
        return REPORT_MAP
    }

    override fun onOutputReport(outputReport: ByteArray) {
        // Do nothing
    }

    fun movePointer(dx: Int, dy: Int, wheel: Int, leftButton: Boolean, rightButton: Boolean, middleButton: Boolean) {
        val reportDx = when {
            dx > 127 -> 127
            dx < -127 -> -127
            else -> dx
        }
        val reportDy = when {
            dx > 127 -> 127
            dx < -127 -> -127
            else -> dy
        }
        val reportWheel = when {
            wheel > 127 -> 127
            wheel < -127 -> -127
            else -> wheel
        }
        var button = 0.toByte()
        if (rightButton) {
            button = button or 1.toByte()
        }
        if (leftButton) {
            button = button or 2.toByte()
        }
        if (middleButton) {
            button = button or 4.toByte()
        }
        val report = ByteArray(4)
        report[0] = (button and 7.toByte())
        report[1] = reportDx.toByte()
        report[2] = reportDy.toByte()
        report[3] = reportWheel.toByte()

        if (lastSent[0] == BYTE_ZERO && lastSent[1] == BYTE_ZERO &&
            lastSent[2] == BYTE_ZERO && lastSent[3] == BYTE_ZERO &&
            report[0] == BYTE_ZERO && report[1] == BYTE_ZERO &&
            report[2] == BYTE_ZERO && report[3] == BYTE_ZERO) {
            return
        }

        lastSent[0] = report[0]
        lastSent[1] = report[1]
        lastSent[2] = report[2]
        lastSent[3] = report[3]
        addInputReport(report)
    }

}

