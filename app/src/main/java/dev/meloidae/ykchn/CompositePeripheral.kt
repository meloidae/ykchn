package dev.meloidae.ykchn


import android.content.Context
import android.util.Log
import dev.meloidae.ykchn.BleUtils.Hid.COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.END_COLLECTION
import dev.meloidae.ykchn.BleUtils.Hid.FEATURE
import dev.meloidae.ykchn.BleUtils.Hid.INPUT
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.LOGICAL_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.OUTPUT
import dev.meloidae.ykchn.BleUtils.Hid.PHYSICAL_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.PHYSICAL_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.POP
import dev.meloidae.ykchn.BleUtils.Hid.PUSH
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_COUNT
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_ID
import dev.meloidae.ykchn.BleUtils.Hid.REPORT_SIZE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MAXIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_MINIMUM
import dev.meloidae.ykchn.BleUtils.Hid.USAGE_PAGE
import dev.meloidae.ykchn.KeyboardUtils.getKeyCode
import dev.meloidae.ykchn.KeyboardUtils.getModifier
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.roundToInt

class CompositePeripheral(context: Context, deviceType: Int, sendDataRate: Int): HidPeripheral(
    context,
    (MOUSE or KEYBOARD or JOYSTICK) and deviceType != 0x00,
    KEYBOARD and deviceType != 0x00,
    true, // For mouse
    sendDataRate
) {
    companion object {
        private val TAG = CompositePeripheral::class.java.simpleName

        const val MOUSE = 0x01
        const val KEYBOARD = 0x02
        const val JOYSTICK = 0x04

        private const val MOUSE_REPORT_ID = 0x01.toByte()
        private const val KEYBOARD_REPORT_ID = 0x02.toByte()
        private const val JOYSTICK_REPORT_ID = 0x03.toByte()

        private const val REPORT_ID_INDEX = 0x00

        private const val MOUSE_BUTTON_INDEX = 0x01
        private const val MOUSE_X_INDEX = 0x02
        private const val MOUSE_Y_INDEX = 0x03
        private const val MOUSE_VERTICAL_WHEEL_INDEX = 0x04
        private const val MOUSE_HORIZONTAL_WHEEL_INDEX = 0x05

        private const val KEYBOARD_MODIFIER_INDEX = 0x01
        private const val KEYBOARD_KEY_INDEX = 0x03

        private const val JOYSTICK_BUTTON_INDEX = 0x01
        private const val JOYSTICK_XL_INDEX = 0x03
        private const val JOYSTICK_YL_INDEX = 0x04
        private const val JOYSTICK_XR_INDEX = 0x05
        private const val JOYSTICK_YR_INDEX = 0x06

    }

    // private val REPORT_MAP_MOUSE = byteArrayOf(
    //     USAGE_PAGE(1),      0x01,         // Generic Desktop
    //     USAGE(1),           0x02,         // Mouse
    //     COLLECTION(1),      0x01,         // Application
    //     REPORT_ID(1),       MOUSE_REPORT_ID,
    //     USAGE(1),           0x01,         //  Pointer
    //     COLLECTION(1),      0x00,         //  Physical
    //     USAGE_PAGE(1),      0x09,         //  Buttons
    //     USAGE_MINIMUM(1),   0x01,         //  Button 1
    //     USAGE_MAXIMUM(1),   0x03,         //  Button 3
    //     LOGICAL_MINIMUM(1), 0x00,
    //     LOGICAL_MAXIMUM(1), 0x01,
    //     REPORT_COUNT(1),    0x03,         //   3 bits (Buttons)
    //     REPORT_SIZE(1),     0x01,
    //     INPUT(1),           0x02,         //   Data, Variable, Absolute
    //     REPORT_COUNT(1),    0x01,
    //     REPORT_SIZE(1),     0x05,         //   5 bits (Padding)
    //     INPUT(1),           0x01,         //   Constant
    //     USAGE_PAGE(1),      0x01,         //   Generic Desktop
    //     USAGE(1),           0x30,         //   X
    //     USAGE(1),           0x31,         //   Y
    //     USAGE(1),           0x38,         //   Wheel
    //     LOGICAL_MINIMUM(1), 0x81.toByte(),  //   -127
    //     LOGICAL_MAXIMUM(1), 0x7f,         //   127
    //     REPORT_SIZE(1),     0x08,         //   Three bytes
    //     REPORT_COUNT(1),    0x03,
    //     INPUT(1),           0x06,         //   Data, Variable, Relative
    //     END_COLLECTION(0),
    //     END_COLLECTION(0)
    // )


    private val REPORT_MAP_KEYBOARD = byteArrayOf(
        USAGE_PAGE(1),      0x01,       // Generic Desktop Ctrls
        USAGE(1),           0x06,       // Keyboard
        COLLECTION(1),      0x01,       // Application
        REPORT_ID(1),       KEYBOARD_REPORT_ID,
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

    //
    // Wheel Mouse - simplified version
    //
    // Input report - 5 bytes
    //
    //     Byte | D7      D6      D5      D4      D3      D2      D1      D0
    //    ------+---------------------------------------------------------------------
    //      0   |  0       0       0    Forward  Back    Middle  Right   Left (Button)
    //      1   |                             X
    //      2   |                             Y
    //      3   |                       Vertical Wheel
    //      4   |                    Horizontal (Tilt) Wheel
    //
    // Feature report - 1 byte
    //
    //     Byte | D7      D6      D5      D4   |  D3      D2  |   D1      D0
    //    ------+------------------------------+--------------+----------------
    //      0   |  0       0       0       0   |  Horizontal  |    Vertical
    //                                             (Resolution multiplier)
    //
    // Reference
    //    Wheel.docx in "Enhanced Wheel Support in Windows Vista" on MS WHDC
    //    http://www.microsoft.com/whdc/device/input/wheel.mspx
    //
    private val REPORT_MAP_MOUSE = byteArrayOf(
        USAGE_PAGE(1), 0x01,                   // USAGE_PAGE (Generic Desktop)
        USAGE(1), 0x02,                        // USAGE (Mouse)
        COLLECTION(1), 0x01,                   // COLLECTION (Application)
        REPORT_ID(1),       MOUSE_REPORT_ID,
        USAGE(1), 0x02,                        //   USAGE (Mouse)
        COLLECTION((1)), 0x02,                      //   COLLECTION (Logical)
        USAGE(1), 0x01,                        //     USAGE (Pointer)
        COLLECTION(1), 0x00,                   //     COLLECTION (Physical)
        // --------------------------------------------------------- Buttons
        USAGE_PAGE(1), 0x09,                   //       USAGE_PAGE (Button)
        USAGE_MINIMUM(1), 0x01,                //       USAGE_MINIMUM (Button 1)
        USAGE_MAXIMUM(1), 0x05,                //       USAGE_MAXIMUM (Button 5)
        LOGICAL_MINIMUM(1), 0x00,              //       LOGICAL_MINIMUM (0)
        LOGICAL_MAXIMUM(1), 0x01,              //       LOGICAL_MAXIMUM (1)
        REPORT_SIZE(1), 0x01,                  //       REPORT_SIZE (1)
        REPORT_COUNT(1), 0x05,                 //       REPORT_COUNT (5)
        INPUT(1), 0x02,                        //       INPUT (Data, Var, Abs)
        // --------------------------------------------------------- Padding
        REPORT_SIZE(1), 0x03,                  //       REPORT_SIZE (3)
        REPORT_COUNT(1), 0x01,                 //       REPORT_COUNT (1)
        INPUT(1), 0x03,                        //       INPUT (Const, Var, Abs)
        // --------------------------------------------------------- X, Y position
        USAGE_PAGE(1), 0x01,                   //       USAGE_PAGE (Generic Desktop)
        USAGE(1), 0x30,                        //       USAGE (X)
        USAGE (1), 0x31,                       //       USAGE (Y)
        LOGICAL_MINIMUM(1), 0x81.toByte(),     //       LOGICAL_MINIMUM (-127)
        LOGICAL_MAXIMUM(1), 0x7f,              //       LOGICAL_MAXIMUM (127)
        REPORT_SIZE(1), 0x08,                  //       REPORT_SIZE (8)
        REPORT_COUNT(1), 0x02,                 //       REPORT_COUNT (2)
        INPUT(1), 0x06,                        //       INPUT (Data, Var, Rel)
        COLLECTION(1), 0x02,                   //       COLLECTION (Logical)
        // --------------------------------------------------------- Vertical wheel res multiplier
        USAGE(1), 0x48,                        //         USAGE (Resolution Multiplier)
        LOGICAL_MINIMUM(1), 0x00,              //         LOGICAL_MINIMUM (0)
        LOGICAL_MAXIMUM(1), 0x01,              //         LOGICAL_MAXIMUM (1)
        PHYSICAL_MINIMUM(1), 0x01,             //         PHYSICAL_MINIMUM (1)
        PHYSICAL_MAXIMUM(1), 0x04,             //         PHYSICAL_MAXIMUM (4)
        REPORT_SIZE(1), 0x02,                  //         REPORT_SIZE (2)
        REPORT_COUNT(1), 0x01,                 //         REPORT_COUNT (1)
        PUSH(0),                               //         PUSH
        FEATURE(1), 0x02,                      //         FEATURE (Data, Var, Abs)
        // --------------------------------------------------------- Vertical wheel
        USAGE(1), 0x38,                        //         USAGE (wheel)
        LOGICAL_MINIMUM(1), 0x81.toByte(),     //         LOGICAL_MINIMUM (-127)
        LOGICAL_MAXIMUM(1), 0x7f,              //         LOGICAL_MAXIMUM (127)
        PHYSICAL_MINIMUM(1), 0x00,             //         PHYSICAL_MINIMUM (0)
        PHYSICAL_MAXIMUM(1), 0x00,             //         PHYSICAL_MAXIMUM (0)
        REPORT_SIZE(1), 0x08,                  //         REPORT_SIZE (8)
        INPUT(1), 0x06,                        //         INPUT (6)
        END_COLLECTION(0),                     //       END_COLLECTION
        COLLECTION(1), 0x02,                   //       COLLECTION (Logical)
        // --------------------------------------------------------- Horizontal wheel res multiplier
        USAGE(1), 0x48,                        //         USAGE (Resolution Multiplier)
        POP(0),                                //         POP
        FEATURE(1), 0x02,                      //         FEATURE (Data, Var, Abs)
        // --------------------------------------------------------- Padding for feature report
        PHYSICAL_MINIMUM(1), 0x00,             //         PHYSICAL_MINIMUM (0) - reset physical
        PHYSICAL_MAXIMUM(1), 0x00,             //         PHYSICAL_MAXIMUM (0)
        REPORT_SIZE(1), 0x04,                  //         REPORT_SIZE (4)
        FEATURE(1), 0x03,                      //         FEATURE (Cnst, Var, Abs)
        // --------------------------------------------------------- Horizontal wheel
        USAGE_PAGE(1), 0x0c,                   //         USAGE_PAGE (Consumer Devices)
        USAGE(2), 0x38, 0x02,                  //         USAGE (AC Pan)
        LOGICAL_MINIMUM(1), 0x81.toByte(),     //         LOGICAL_MINIMUM (-127)
        LOGICAL_MAXIMUM(1), 0x7f,              //         LOGICAL_MAXIMUM (127)
        REPORT_SIZE(1), 0x08,                  //         REPORT_SIZE (8)
        INPUT(1), 0x06,                        //         INPUT (Data, Var, Rel)
        END_COLLECTION(0),                     //       END_COLLECTION
        END_COLLECTION(0),                     //     END_COLLECTION
        END_COLLECTION(0),                     //   END_COLLECTION
        END_COLLECTION(0)                      // END_COLLECTION
    )

    private val REPORT_MAP_JOYSTICK = byteArrayOf(
        USAGE_PAGE(1),      0x01,// Generic Desktop
        USAGE(1),           0x04,// Joystick
        COLLECTION(1),      0x01,// Application
        REPORT_ID(1), JOYSTICK_REPORT_ID,
        COLLECTION(1),      0x00,//  Physical
        USAGE_PAGE(1),      0x09,//   Buttons
        USAGE_MINIMUM(1),   0x01,// USAGE_MINIMUM (Button 1)
        USAGE_MAXIMUM(1),   0x10,// USAGE_MAXIMUM (Button 16)
        LOGICAL_MINIMUM(1), 0x00,// LOGICAL_MINIMUM (0)
        LOGICAL_MAXIMUM(1), 0x01,// LOGICAL_MAXIMUM (1)
        REPORT_COUNT(1),    0x10,// REPORT_COUNT (16)
        REPORT_SIZE(1),     0x01,// REPORT_SIZE (1)
        INPUT(1),           0x02,// INPUT (Data,Var,Abs)
        USAGE_PAGE(1),      0x01,// USAGE_PAGE (Generic Desktop)
        USAGE(1), 0x30, // USAGE (X)
        USAGE(1), 0x31, // USAGE (Y)
        USAGE(1), 0x32, // USAGE (Z)
        USAGE(1), 0x33, // USAGE (Rx)
        LOGICAL_MINIMUM(1), 0x81.toByte(),// LOGICAL_MINIMUM (-127)
        LOGICAL_MAXIMUM(1), 0x7f,// LOGICAL_MAXIMUM (127)
        REPORT_SIZE(1),     0x08,// REPORT_SIZE (8)
        REPORT_COUNT(1),    0x04,// REPORT_COUNT (4)
        INPUT(1),           0x02,// INPUT (Data,Var,Abs)
        END_COLLECTION(0),
        END_COLLECTION(0)
    )


    private val REPORT_MAP: ByteArray

    private val mouseLastSent = ByteArray(5)
    private val joystickLastSent = ByteArray(6)

    var mouseReverseVerticalWheelDirection = false
    var mouseReverseHorizontalWheelDirection = false
    var mouseVerticalWheelScaling = 1.0
    var mouseHorizontalWheelScaling = 1.0

    override fun getReportMap(): ByteArray {
        return REPORT_MAP
    }

    override fun onOutputReport(outputReport: ByteArray) {}

    private fun isZero(report: ByteArray): Boolean {
        for (i in report) {
            if (i != 0x00.toByte()) {
                return false
            }
        }
        return true
    }

    fun mouseMovePointer(dx: Int, dy: Int, verticalWheel: Int, horizontalWheel: Int, leftButton: Boolean, rightButton: Boolean, middleButton: Boolean) {

        val reportDx = when {
            dx > 127 -> 127
            dx < -127 -> -127
            else -> dx
        }
        val reportDy = when {
            dy > 127 -> 127
            dy < -127 -> -127
            else -> dy
        }
        var reportVerticalWheel = (verticalWheel * mouseVerticalWheelScaling).roundToInt()
        reportVerticalWheel = when {
            reportVerticalWheel > 127 -> 127
            reportVerticalWheel < -127 -> -127
            else -> reportVerticalWheel
        }
        if (mouseReverseVerticalWheelDirection) {
            reportVerticalWheel *= -1
        }
        var reportHorizontalWheel = (verticalWheel * mouseHorizontalWheelScaling).roundToInt()
        reportHorizontalWheel = when {
            reportHorizontalWheel > 127 -> 127
            reportHorizontalWheel < -127 -> -127
            else -> reportHorizontalWheel
        }
        if (mouseReverseHorizontalWheelDirection) {
            reportHorizontalWheel *= -1
        }
        var button = 0.toByte()
        if (rightButton) {
            button = button or 2.toByte()
        }
        if (leftButton) {
            button = button or 1.toByte()
        }
        if (middleButton) {
            button = button or 4.toByte()
        }

        // val report = ByteArray(5)
        val report = ByteArray(6)
        report[REPORT_ID_INDEX] = MOUSE_REPORT_ID
        report[MOUSE_BUTTON_INDEX] = (button and 7.toByte())
        report[MOUSE_X_INDEX] = reportDx.toByte()
        report[MOUSE_Y_INDEX] = reportDy.toByte()
        report[MOUSE_VERTICAL_WHEEL_INDEX] = reportVerticalWheel.toByte()
        report[MOUSE_HORIZONTAL_WHEEL_INDEX] = reportHorizontalWheel.toByte()
        if (isZero(mouseLastSent) && isZero(report.slice(1 until report.size).toByteArray())) {
            return
        }

        mouseLastSent[MOUSE_BUTTON_INDEX - 1] = report[MOUSE_BUTTON_INDEX]
        mouseLastSent[MOUSE_X_INDEX - 1] = report[MOUSE_X_INDEX]
        mouseLastSent[MOUSE_Y_INDEX - 1] = report[MOUSE_Y_INDEX]
        mouseLastSent[MOUSE_VERTICAL_WHEEL_INDEX - 1] = report[MOUSE_VERTICAL_WHEEL_INDEX]
        mouseLastSent[MOUSE_HORIZONTAL_WHEEL_INDEX - 1] = report[MOUSE_HORIZONTAL_WHEEL_INDEX]
        addInputReport(report)
    }

    fun keyboardSendKeys(text: String) {
        // var lastKey: String? = null
        for (i in text.indices) {
            val key = text.substring(i, i + 1)
            val report = ByteArray(8)
            val modifier = getModifier(key)
            val keyCode = getKeyCode(key)
            Log.d(TAG, "sendKeys() key: $keyCode, modifier: $modifier")
            keyboardSendKeyDown(modifier, keyCode)
            // if (key == lastKey) {
            //     sendKeyUp()
            // }
            addInputReport(report)
            // lastKey = key
            keyboardSendKeyUp()
        }
        keyboardSendKeyUp()
    }

    fun keyboardSendKeyDown(modifier: Byte, keyCode: Byte) {
        val report = ByteArray(9)
        report[REPORT_ID_INDEX] = KEYBOARD_REPORT_ID
        report[KEYBOARD_MODIFIER_INDEX] = modifier
        report[KEYBOARD_KEY_INDEX] = keyCode

        addInputReport(report)
    }

    fun keyboardSendKeyUp() {
        val emptyReport = ByteArray(9)
        emptyReport[REPORT_ID_INDEX] = KEYBOARD_REPORT_ID
        addInputReport(emptyReport)
    }

    fun joystickMovePointer(dxLeft: Int, dyLeft: Int, dxRight: Int, dyRight: Int,
                            button1: Boolean, button2: Boolean, button3: Boolean, button4: Boolean,
                            button5: Boolean, button6: Boolean, button7: Boolean, button8: Boolean,
                            button9: Boolean, button10: Boolean, button11: Boolean, button12: Boolean,
                            button13: Boolean, button14: Boolean, button15: Boolean, button16: Boolean) {
        val reportDxLeft = when {
            dxLeft > 127 -> 127
            dxLeft < -127 -> -127
            else -> dxLeft
        }
        val reportDyLeft = when {
            dyLeft > 127 -> 127
            dyLeft < -127 -> -127
            else -> dyLeft
        }
        val reportDxRight = when {
            dxRight > 127 -> 127
            dxRight < -127 -> -127
            else -> dxRight
        }
        val reportDyRight = when {
            dyRight > 127 -> 127
            dyRight < -127 -> -127
            else -> dyRight
        }

        var buttons = byteArrayOf(0x00, 0x00)
        if (button1) {
            buttons[0] = buttons[0] or 1.toByte()
        }
        if (button2) {
            buttons[0] = buttons[0] or 2.toByte()
        }
        if (button3) {
            buttons[0] = buttons[0] or 4.toByte()
        }
        if (button4) {
            buttons[0] = buttons[0] or 8.toByte()
        }
        if (button5) {
            buttons[0] = buttons[0] or 16.toByte()
        }
        if (button6) {
            buttons[0] = buttons[0] or 32.toByte()
        }
        if (button7) {
            buttons[0] = buttons[0] or 64.toByte()
        }
        if (button8) {
            buttons[0] = buttons[0] or 128.toByte()
        }
        if (button9) {
            buttons[1] = buttons[1] or 1.toByte()
        }
        if (button10) {
            buttons[1] = buttons[1] or 2.toByte()
        }
        if (button11) {
            buttons[1] = buttons[1] or 4.toByte()
        }
        if (button12) {
            buttons[1] = buttons[1] or 8.toByte()
        }
        if (button13) {
            buttons[1] = buttons[1] or 16.toByte()
        }
        if (button14) {
            buttons[1] = buttons[1] or 32.toByte()
        }
        if (button15) {
            buttons[1] = buttons[1] or 64.toByte()
        }
        if (button16) {
            buttons[1] = buttons[1] or 128.toByte()
        }

        val report = ByteArray(7)
        report[REPORT_ID_INDEX] = JOYSTICK_REPORT_ID
        report[JOYSTICK_BUTTON_INDEX] = (buttons[0] and 255.toByte())
        report[JOYSTICK_BUTTON_INDEX + 1] = (buttons[1] and 255.toByte())
        report[JOYSTICK_XL_INDEX] = reportDxLeft.toByte()
        report[JOYSTICK_YL_INDEX] = reportDyLeft.toByte()
        report[JOYSTICK_XR_INDEX] = reportDxRight.toByte()
        report[JOYSTICK_YR_INDEX] = reportDyRight.toByte()

        if (isZero(joystickLastSent) &&
            isZero(report.slice(1 until report.size).toByteArray())) {
            return
        }

        joystickLastSent[JOYSTICK_BUTTON_INDEX - 1] = report[JOYSTICK_BUTTON_INDEX]
        joystickLastSent[JOYSTICK_XL_INDEX - 1] = report[JOYSTICK_XL_INDEX]
        joystickLastSent[JOYSTICK_YL_INDEX - 1] = report[JOYSTICK_YL_INDEX]
        joystickLastSent[JOYSTICK_XR_INDEX - 1] = report[JOYSTICK_XR_INDEX]
        joystickLastSent[JOYSTICK_YR_INDEX - 1] = report[JOYSTICK_YR_INDEX]
        addInputReport(report)
    }

    init {
        var reportMap = byteArrayOf()
        if (deviceType and MOUSE != 0x00) {
            reportMap += REPORT_MAP_MOUSE
        }
        if (deviceType and KEYBOARD != 0x00) {
            reportMap += REPORT_MAP_KEYBOARD
        }
        if (deviceType and JOYSTICK != 0x00) {
            reportMap += REPORT_MAP_JOYSTICK
        }
        REPORT_MAP = reportMap
    }
}

