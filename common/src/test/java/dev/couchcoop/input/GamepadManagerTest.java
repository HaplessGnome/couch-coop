package dev.couchcoop.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GamepadManagerTest {
    private static final String PATH = "HID#VID_054C&PID_0CE6&REV_8111#2&960061B&0&0000";

    @Test
    void keyIsTheDevicePathSoEveryWindowAgrees() {
        assertEquals("path:" + PATH, GamepadManager.stableKey(PATH, "guid", 0));
    }

    @Test
    void noPathUsesGuidAndPosition() {
        assertEquals("guid:abc#1", GamepadManager.stableKey(" ", "abc", 1));
        assertEquals("guid:abc#0", GamepadManager.stableKey(null, "abc", 0));
    }

    @Test
    void bluetoothAddressSerialsAreUsableInAnyFormat() {
        assertEquals("0c27565d9714", GamepadManager.usableSerial("0C:27:56:5D:97:14"));
        assertEquals("0c27565d9714", GamepadManager.usableSerial("0c-27-56-5d-97-14"));
    }

    @Test
    void junkSerialsAreIgnored() {
        // A virtual pad created by game streaming (Sunshine) read back as "10" in one window.
        assertNull(GamepadManager.usableSerial("10"));
        assertNull(GamepadManager.usableSerial(""));
        assertNull(GamepadManager.usableSerial(null));
        assertNull(GamepadManager.usableSerial("00:00:00:00:00:00"));
    }
}
