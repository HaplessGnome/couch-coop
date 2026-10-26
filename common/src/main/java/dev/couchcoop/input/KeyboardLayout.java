package dev.couchcoop.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Key rows for the on-screen keyboard, with letter and symbol pages, plus D-pad movement between
 * keys. Pure layout logic: {@link OnScreenKeyboard} turns key presses into game input.
 */
public final class KeyboardLayout {
    public enum Kind { CHAR, SHIFT, SYMBOLS, SPACE, BACKSPACE, TAB, DONE }

    /**
     * One key. {@code start} and {@code width} are in key units; every row is {@link #ROW_UNITS} wide.
     * {@code shifted} is what a CHAR key types with shift on.
     */
    public record Key(Kind kind, String label, String text, String shifted, int start, int width) {
        public double centre() {
            return start + width / 2.0;
        }
    }

    public static final int ROW_UNITS = 10;

    private static final String[] LETTERS = {
        "1234567890",
        "qwertyuiop",
        "asdfghjkl'",
        "zxcvbnm,.",
    };
    private static final String[] LETTERS_SHIFTED = {
        "!@#$%^&*()",
        "QWERTYUIOP",
        "ASDFGHJKL\"",
        "ZXCVBNM<>",
    };
    private static final String[] SYMBOLS = {
        "1234567890",
        "-_=+[]{}\\|",
        ";:/?~`<>\"'",
        "!@#$%^&*",
    };

    private final List<List<Key>> letters;
    private final List<List<Key>> symbols;

    public KeyboardLayout() {
        letters = build(LETTERS, LETTERS_SHIFTED, false);
        symbols = build(SYMBOLS, SYMBOLS, true);
    }

    public List<List<Key>> rows(boolean symbolPage) {
        return symbolPage ? symbols : letters;
    }

    private static List<List<Key>> build(String[] chars, String[] shifted, boolean symbolPage) {
        List<List<Key>> rows = new ArrayList<>();
        for (int r = 0; r < chars.length; r++) {
            List<Key> row = new ArrayList<>();
            int start = 0;
            if (r == 3) {
                row.add(new Key(Kind.SHIFT, "Shift", "", "", 0, 1));
                start = 1;
            }
            for (int i = 0; i < chars[r].length(); i++) {
                String text = String.valueOf(chars[r].charAt(i));
                row.add(new Key(Kind.CHAR, text, text, String.valueOf(shifted[r].charAt(i)), start + i, 1));
            }
            if (r == 3) {
                int used = start + chars[r].length();
                row.add(new Key(Kind.BACKSPACE, "Del", "", "", used, ROW_UNITS - used));
            }
            rows.add(row);
        }
        rows.add(List.of(
            new Key(Kind.SYMBOLS, symbolPage ? "abc" : "#+=", "", "", 0, 2),
            new Key(Kind.CHAR, "/", "/", "/", 2, 1),
            new Key(Kind.SPACE, "Space", " ", " ", 3, 4),
            // Tab completes commands and player names in chat.
            new Key(Kind.TAB, "Tab", "", "", 7, 1),
            new Key(Kind.DONE, "Done", "", "", 8, 2)
        ));
        return rows;
    }

    /** Position of the highlighted key: row index and key index within that row. */
    public record Cursor(int row, int column) {
    }

    /** Moves one key in a direction, wrapping at the edges and keeping the column across rows. */
    public Cursor move(Cursor from, Navigation.Direction direction, boolean symbolPage) {
        List<List<Key>> rows = rows(symbolPage);
        List<Key> row = rows.get(from.row());
        return switch (direction) {
            case LEFT -> new Cursor(from.row(), Math.floorMod(from.column() - 1, row.size()));
            case RIGHT -> new Cursor(from.row(), Math.floorMod(from.column() + 1, row.size()));
            case UP, DOWN -> {
                int target = Math.floorMod(from.row() + (direction == Navigation.Direction.UP ? -1 : 1), rows.size());
                yield new Cursor(target, columnAt(rows.get(target), row.get(from.column()).centre()));
            }
        };
    }

    /** Keeps a cursor valid after switching pages, where rows can have different lengths. */
    public Cursor clamp(Cursor cursor, boolean symbolPage) {
        List<List<Key>> rows = rows(symbolPage);
        int row = Math.min(cursor.row(), rows.size() - 1);
        return new Cursor(row, Math.min(cursor.column(), rows.get(row).size() - 1));
    }

    private static int columnAt(List<Key> row, double x) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < row.size(); i++) {
            Key key = row.get(i);
            if (x >= key.start() && x < key.start() + key.width()) {
                return i;
            }
            double distance = Math.abs(key.centre() - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    public Key key(Cursor cursor, boolean symbolPage) {
        return rows(symbolPage).get(cursor.row()).get(cursor.column());
    }
}
