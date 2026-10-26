package dev.couchcoop.craft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickCraftTest {
    @Test
    void shortListsNeverScroll() {
        assertEquals(0, QuickCraft.scrollFor(3, 5, 8));
    }

    @Test
    void selectionStaysNearTheMiddle() {
        assertEquals(6, QuickCraft.scrollFor(10, 30, 8));
    }

    @Test
    void scrollStopsAtBothEnds() {
        assertEquals(0, QuickCraft.scrollFor(1, 30, 8));
        assertEquals(22, QuickCraft.scrollFor(29, 30, 8));
    }
}
