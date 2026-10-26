package dev.couchcoop.input;

/** Turns a held direction into repeated steps: one at once, then more after a delay, like a held key. */
public final class HeldRepeat {
    private static final long DELAY_MS = 350;
    private static final long RATE_MS = 90;

    private Object held;
    private long nextAt;

    /** Feed the currently held direction (or null) each frame; true when a step should happen. */
    public boolean step(Object direction, long nowMs) {
        if (direction == null) {
            held = null;
            return false;
        }
        if (!direction.equals(held)) {
            held = direction;
            nextAt = nowMs + DELAY_MS;
            return true;
        }
        if (nowMs >= nextAt) {
            nextAt = nowMs + RATE_MS;
            return true;
        }
        return false;
    }
}
