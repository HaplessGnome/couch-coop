package dev.couchcoop.profile;

import dev.couchcoop.input.Gamepad;
import dev.couchcoop.input.GamepadInput;

import java.util.List;

import static org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY;

/**
 * The "who's playing?" list shown when a controller asks to join. It is drawn as an overlay and
 * driven only by the joining controller, so whoever is already playing in the window keeps control.
 */
public final class ProfilePicker {
    public enum Kind { PROFILE, NEW, GUEST }

    public record Entry(Kind kind, Profile profile, String label) {
    }

    public enum Result { NONE, CHOOSE, EDIT, CANCEL }

    private static final long REPEAT_DELAY_MS = 350;
    private static final long REPEAT_RATE_MS = 120;
    private static final float STICK_THRESHOLD = 0.6f;

    private final Gamepad pad;
    private final List<Entry> entries;
    private int selected;
    private int heldDirection;
    private long nextRepeat;

    public ProfilePicker(Gamepad pad, List<Entry> entries) {
        this.pad = pad;
        this.entries = List.copyOf(entries);
    }

    public Gamepad pad() {
        return pad;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int selectedIndex() {
        return selected;
    }

    public Entry selected() {
        return entries.get(selected);
    }

    public Result update() {
        move();
        if (pad.pressed(GamepadInput.CROSS)) {
            return Result.CHOOSE;
        }
        if (pad.pressed(GamepadInput.TRIANGLE) && selected().kind() == Kind.PROFILE) {
            return Result.EDIT;
        }
        if (pad.pressed(GamepadInput.CIRCLE)) {
            return Result.CANCEL;
        }
        return Result.NONE;
    }

    /** D-pad or left stick, with key-repeat while held. */
    private void move() {
        float stick = pad.axis(SDL_GAMEPAD_AXIS_LEFTY);
        int direction = 0;
        if (pad.isDown(GamepadInput.DPAD_UP) || stick < -STICK_THRESHOLD) {
            direction = -1;
        } else if (pad.isDown(GamepadInput.DPAD_DOWN) || stick > STICK_THRESHOLD) {
            direction = 1;
        }

        long now = System.currentTimeMillis();
        if (direction == 0) {
            heldDirection = 0;
            return;
        }
        if (direction != heldDirection) {
            heldDirection = direction;
            nextRepeat = now + REPEAT_DELAY_MS;
        } else if (now >= nextRepeat) {
            nextRepeat = now + REPEAT_RATE_MS;
        } else {
            return;
        }
        selected = Math.floorMod(selected + direction, entries.size());
    }

    /** Button hints for the bottom of the list, in this controller's own button names. */
    public String help() {
        boolean ps = pad.isPlayStation();
        String text = GamepadInput.CROSS.label(ps) + " choose   " + GamepadInput.CIRCLE.label(ps) + " cancel";
        return selected().kind() == Kind.PROFILE ? text + "   " + GamepadInput.TRIANGLE.label(ps) + " edit" : text;
    }
}
