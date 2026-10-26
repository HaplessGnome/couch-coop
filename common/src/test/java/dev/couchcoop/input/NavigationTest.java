package dev.couchcoop.input;

import dev.couchcoop.input.Navigation.Direction;
import dev.couchcoop.input.Navigation.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NavigationTest {
    /** A 9x3 inventory grid of 18px slots, like the player inventory. */
    private static List<Target> grid() {
        List<Target> slots = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slots.add(new Target(col * 18, row * 18, 16, 16));
            }
        }
        return slots;
    }

    private static Target slot(int col, int row) {
        return new Target(col * 18, row * 18, 16, 16);
    }

    @Test
    void movesToTheNeighbouringSlot() {
        List<Target> slots = grid();
        Target middle = slot(4, 1);
        double x = middle.centreX();
        double y = middle.centreY();
        assertEquals(slot(5, 1), Navigation.next(slots, x, y, Direction.RIGHT));
        assertEquals(slot(3, 1), Navigation.next(slots, x, y, Direction.LEFT));
        assertEquals(slot(4, 0), Navigation.next(slots, x, y, Direction.UP));
        assertEquals(slot(4, 2), Navigation.next(slots, x, y, Direction.DOWN));
    }

    @Test
    void stopsAtTheEdge() {
        Target corner = slot(8, 0);
        assertNull(Navigation.next(grid(), corner.centreX(), corner.centreY(), Direction.RIGHT));
        assertNull(Navigation.next(grid(), corner.centreX(), corner.centreY(), Direction.UP));
    }

    @Test
    void prefersInLineOverCloserDiagonal() {
        Target ahead = new Target(100, 0, 16, 16);
        Target diagonal = new Target(30, 30, 16, 16);
        assertEquals(ahead, Navigation.next(List.of(ahead, diagonal), 8, 8, Direction.RIGHT));
    }

    @Test
    void reachesAWideButtonOffset() {
        // Moving down from the grid to a wide button below its left half.
        Target button = new Target(0, 80, 100, 20);
        List<Target> targets = new ArrayList<>(grid());
        targets.add(button);
        Target bottomRight = slot(7, 2);
        assertEquals(button, Navigation.next(targets, bottomRight.centreX(), bottomRight.centreY(), Direction.DOWN));
    }

    @Test
    void freeCursorBetweenSlotsSnapsToNearest() {
        assertEquals(slot(2, 0), Navigation.next(grid(), 20, 8, Direction.RIGHT));
    }
}
