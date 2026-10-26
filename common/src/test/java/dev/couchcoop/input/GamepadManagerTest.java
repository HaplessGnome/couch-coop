package dev.couchcoop.input;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamepadManagerTest {
    private static final String USB_PATH = "HID#VID_054C&PID_0CE6&MI_03#7&2b491f3b";
    private static final String BT_PATH = "HID#{00001124-0000-1000-8000-00805f9b34fb}_VID&0002054c";

    @Test
    void serialWinsOverPathSoUsbAndBluetoothMatch() {
        String usb = GamepadManager.stableKey("0C:27:56:5D:97:14", USB_PATH, "guid", 0, Set.of());
        String bluetooth = GamepadManager.stableKey("0c-27-56-5d-97-14", BT_PATH, "guid", 0, Set.of());
        assertEquals("serial:0c27565d9714", usb);
        assertEquals(usb, bluetooth);
    }

    @Test
    void blankOrZeroSerialFallsBackToPath() {
        assertEquals("path:" + USB_PATH, GamepadManager.stableKey(null, USB_PATH, "guid", 0, Set.of()));
        assertEquals("path:" + USB_PATH, GamepadManager.stableKey("", USB_PATH, "guid", 0, Set.of()));
        assertEquals("path:" + USB_PATH, GamepadManager.stableKey("00:00:00:00:00:00", USB_PATH, "guid", 0, Set.of()));
    }

    @Test
    void duplicateSerialFallsBackToPath() {
        Set<String> inUse = Set.of("serial:123456");
        assertEquals("path:" + BT_PATH, GamepadManager.stableKey("123456", BT_PATH, "guid", 1, inUse));
    }

    @Test
    void noSerialOrPathUsesGuidAndPosition() {
        assertEquals("guid:abc#1", GamepadManager.stableKey(null, " ", "abc", 1, Set.of()));
    }
}
