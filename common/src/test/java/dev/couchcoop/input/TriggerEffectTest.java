package dev.couchcoop.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TriggerEffectTest {
    @Test
    void reportMatchesSdlsDualSenseLayout() {
        byte[] report = TriggerEffect.report(TriggerEffect.resistance(0, 0.5), TriggerEffect.off());
        assertEquals(47, report.length);
        // Only the two trigger effects are marked as changed.
        assertEquals(0x0C, report[0]);
        assertEquals(0, report[1]);
        // Right trigger effect starts at byte 10, left at byte 21.
        assertEquals(0x05, report[10]);
        assertEquals(0x01, report[21]);
        assertEquals(0, report[22]);
        assertEquals((byte) 128, report[23]);
        // Lights and the rest stay zero.
        for (int i = 32; i < 47; i++) {
            assertEquals(0, report[i], "byte " + i);
        }
    }

    @Test
    void strengthIsClampedToTheByteRange() {
        assertEquals((byte) 255, TriggerEffect.resistance(0, 2).bytes()[2]);
        assertEquals(0, TriggerEffect.resistance(-1, -1).bytes()[1]);
    }

    @Test
    void effectsCompareByContent() {
        assertEquals(TriggerEffect.off(), TriggerEffect.off());
        assertEquals(TriggerEffect.resistance(0, 0.4), TriggerEffect.resistance(0, 0.4));
        assertNotEquals(TriggerEffect.resistance(0, 0.4), TriggerEffect.resistance(0, 0.6));
    }
}
