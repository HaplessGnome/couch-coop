package dev.couchcoop.input;

import dev.couchcoop.input.KeyboardLayout.Cursor;
import dev.couchcoop.input.KeyboardLayout.Key;
import dev.couchcoop.input.KeyboardLayout.Kind;
import dev.couchcoop.input.Navigation.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyboardLayoutTest {
    private final KeyboardLayout layout = new KeyboardLayout();

    @Test
    void everyRowFillsTheFullWidthWithoutGaps() {
        for (boolean symbols : new boolean[] {false, true}) {
            for (List<Key> row : layout.rows(symbols)) {
                int next = 0;
                for (Key key : row) {
                    assertEquals(next, key.start(), "gap before " + key.label());
                    next += key.width();
                }
                if (row.size() > 4) {
                    assertEquals(KeyboardLayout.ROW_UNITS, next, "row ending with " + row.getLast().label());
                }
            }
        }
    }

    @Test
    void movingDownKeepsTheColumn() {
        Cursor e = new Cursor(1, 2);
        assertEquals("e", layout.key(e, false).text());
        assertEquals("d", layout.key(layout.move(e, Direction.DOWN, false), false).text());
    }

    @Test
    void movingIntoTheBottomRowLandsOnTheKeyUnderneath() {
        Cursor b = new Cursor(3, 5);
        assertEquals("b", layout.key(b, false).text());
        assertEquals(Kind.SPACE, layout.key(layout.move(b, Direction.DOWN, false), false).kind());
    }

    @Test
    void leftAndRightWrap() {
        assertEquals(new Cursor(0, 9), layout.move(new Cursor(0, 0), Direction.LEFT, false));
        assertEquals(new Cursor(0, 0), layout.move(new Cursor(0, 9), Direction.RIGHT, false));
    }

    @Test
    void upFromTheTopWrapsToTheBottomRow() {
        assertEquals(4, layout.move(new Cursor(0, 4), Direction.UP, false).row());
    }

    @Test
    void shiftedLettersAreCapitals() {
        Key q = layout.key(new Cursor(1, 0), false);
        assertEquals("q", q.text());
        assertEquals("Q", q.shifted());
    }

    @Test
    void clampKeepsCursorOnAKeyAfterSwitchingPages() {
        Cursor last = new Cursor(3, 10);
        Cursor clamped = layout.clamp(last, true);
        assertEquals(layout.rows(true).get(3).size() - 1, clamped.column());
    }
}
