package dev.meloidae.ykchn

object KeyboardUtils {

    /**
     * Keyboard constants
     */
    const val MODIFIER_KEY_NONE = 0
    const val MODIFIER_KEY_CTRL = 1
    const val MODIFIER_KEY_SHIFT = 2
    const val MODIFIER_KEY_ALT = 4

    const val KEY_F1 = 0x3a
    const val KEY_F2 = 0x3b
    const val KEY_F3 = 0x3c
    const val KEY_F4 = 0x3d
    const val KEY_F5 = 0x3e
    const val KEY_F6 = 0x3f
    const val KEY_F7 = 0x40
    const val KEY_F8 = 0x41
    const val KEY_F9 = 0x42
    const val KEY_F10 = 0x43
    const val KEY_F11 = 0x44
    const val KEY_F12 = 0x45

    const val KEY_PRINT_SCREEN = 0x46
    const val KEY_SCROLL_LOCK = 0x47
    const val KEY_CAPS_LOCK = 0x39
    const val KEY_NUM_LOCK = 0x53
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4a
    const val KEY_PAGE_UP = 0x4b
    const val KEY_PAGE_DOWN = 0x4e

    const val KEY_RIGHT_ARROW = 0x4f
    const val KEY_LEFT_ARROW = 0x50
    const val KEY_DOWN_ARROW = 0x51
    const val KEY_UP_ARROW = 0x52
    /**
     * Modifier code for US Keyboard
     *
     * @param aChar String contains one character
     * @return modifier code
     */
    fun getModifier(character: String): Byte {
        when (character[0]) {
            in '!'..'&', in '('..'+', ':', '<', in '>'..'Z', '^', '_', in '{'..'~' -> {
                return MODIFIER_KEY_SHIFT.toByte()
            }
        }
        return MODIFIER_KEY_NONE.toByte()
    }

    /**
     * Key code for US Keyboard
     *
     * @param charStr String contains one character
     * @return keyCode
     */
    fun getKeyCode(charStr: String): Byte {
        when (val character = charStr[0]) {
            in 'a'..'z' -> {
                return (character.toInt() - 'a'.toInt() + 0x04).toByte()
            }
            in 'A'..'Z' -> {
                return (character.toInt() - 'A'.toInt() + 0x04).toByte()
            }
            '!', '1' -> return 0x1e
            '@', '2' -> return 0x1f
            '#', '3' -> return 0x20
            '$', '4' -> return 0x21
            '%', '5' -> return 0x22
            '^', '6' -> return 0x23
            '&', '7' -> return 0x24
            '*', '8' -> return 0x25
            '(', '9' -> return 0x26
            ')', '0' -> return 0x27
            '\n' -> return 0x28 // LF
            '\b' -> return 0x2a // BS
            '\t' -> return 0x2b // TAB
            ' ' -> return 0x2c
            '_', '-' -> return 0x2d
            '+', '=' -> return 0x2e
            '{', '[' -> return 0x2f
            '}', ']' -> return 0x30
            '|', '\\' -> return 0x31
            ':', ';' -> return 0x33
            '"', '\'' -> return 0x34
            '~', '`' -> return 0x35
            '<', ',' -> return 0x36
            '>', '.' -> return 0x37
            '?', '/' -> return 0x38
            else -> return 0
        }
    }

}