package dev.couchcoop.input;

import java.util.List;

/**
 * Picks where a D-pad press should move the menu cursor: the nearest target whose centre lies in
 * that direction, preferring targets in line with the cursor over ones off to the side.
 */
public final class Navigation {
    public enum Direction {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** Something the cursor can land on, in GUI pixels. */
    public record Target(double x, double y, double width, double height) {
        public double centreX() {
            return x + width / 2;
        }

        public double centreY() {
            return y + height / 2;
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** Sideways distance counts this much more than distance in the pressed direction. */
    private static final double SIDEWAYS_WEIGHT = 4;

    private Navigation() {
    }

    /** The best target in {@code direction} from (x, y), or null if there is nothing that way. */
    public static Target next(List<Target> targets, double x, double y, Direction direction) {
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        for (Target target : targets) {
            if (target.contains(x, y)) {
                continue;
            }
            double offsetX = target.centreX() - x;
            double offsetY = target.centreY() - y;
            double along = offsetX * direction.dx + offsetY * direction.dy;
            // Sideways distance to the target's nearest edge, so wide buttons are easy to reach.
            double sideways = direction.dx != 0
                ? Math.max(0, Math.abs(offsetY) - target.height() / 2)
                : Math.max(0, Math.abs(offsetX) - target.width() / 2);
            // Must actually be ahead, and within 45 degrees of straight ahead.
            if (along < 1 || sideways > along) {
                continue;
            }
            double score = along + sideways * SIDEWAYS_WEIGHT;
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }
}
