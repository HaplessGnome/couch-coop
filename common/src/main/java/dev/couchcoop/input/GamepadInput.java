package dev.couchcoop.input;

import java.util.Locale;

import static org.lwjgl.sdl.SDLGamepad.*;

/**
 * Every bindable control, named after its PlayStation 5 button. Bindings are positional, so an
 * Xbox pad uses the button in the same place: CROSS = A, CIRCLE = B, SQUARE = X, TRIANGLE = Y,
 * CREATE = View, OPTIONS = Menu, PS = Guide, MUTE = Share. Xbox names are accepted when parsing.
 */
public enum GamepadInput {
    CROSS(SDL_GAMEPAD_BUTTON_SOUTH, "Cross", "A"),
    CIRCLE(SDL_GAMEPAD_BUTTON_EAST, "Circle", "B"),
    SQUARE(SDL_GAMEPAD_BUTTON_WEST, "Square", "X"),
    TRIANGLE(SDL_GAMEPAD_BUTTON_NORTH, "Triangle", "Y"),
    CREATE(SDL_GAMEPAD_BUTTON_BACK, "Create", "View", "BACK", "SHARE", "VIEW"),
    PS(SDL_GAMEPAD_BUTTON_GUIDE, "PS", "Guide", "GUIDE", "HOME"),
    OPTIONS(SDL_GAMEPAD_BUTTON_START, "Options", "Menu", "START", "MENU"),
    L3(SDL_GAMEPAD_BUTTON_LEFT_STICK, "L3", "LS"),
    R3(SDL_GAMEPAD_BUTTON_RIGHT_STICK, "R3", "RS"),
    L1(SDL_GAMEPAD_BUTTON_LEFT_SHOULDER, "L1", "LB"),
    R1(SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, "R1", "RB"),
    DPAD_UP(SDL_GAMEPAD_BUTTON_DPAD_UP, "D-pad Up", "D-pad Up"),
    DPAD_DOWN(SDL_GAMEPAD_BUTTON_DPAD_DOWN, "D-pad Down", "D-pad Down"),
    DPAD_LEFT(SDL_GAMEPAD_BUTTON_DPAD_LEFT, "D-pad Left", "D-pad Left"),
    DPAD_RIGHT(SDL_GAMEPAD_BUTTON_DPAD_RIGHT, "D-pad Right", "D-pad Right"),
    TOUCHPAD(SDL_GAMEPAD_BUTTON_TOUCHPAD, "Touchpad", "Touchpad"),
    /** The DualSense microphone button; the Share button on Xbox Series pads. */
    MUTE(SDL_GAMEPAD_BUTTON_MISC1, "Mute", "Share", "MISC1"),
    L2(-1, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, "L2", "LT"),
    R2(-1, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER, "R2", "RT"),
    NONE(-1, -1, "None", "None");

    final int button;
    final int triggerAxis;
    private final String playStationLabel;
    private final String xboxLabel;
    private final String[] aliases;

    GamepadInput(int button, String playStationLabel, String xboxLabel, String... aliases) {
        this(button, -1, playStationLabel, xboxLabel, aliases);
    }

    GamepadInput(int button, int triggerAxis, String playStationLabel, String xboxLabel, String... aliases) {
        this.button = button;
        this.triggerAxis = triggerAxis;
        this.playStationLabel = playStationLabel;
        this.xboxLabel = xboxLabel;
        this.aliases = aliases;
    }

    /** The name printed on this button for the given kind of controller. */
    public String label(boolean playStation) {
        return playStation ? playStationLabel : xboxLabel;
    }

    /** Accepts PlayStation names (CROSS, L1, OPTIONS...) and Xbox names (A, LB, START...). */
    public static GamepadInput parse(String name, GamepadInput fallback) {
        if (name == null) {
            return fallback;
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (GamepadInput input : values()) {
            if (input.name().equals(key) || input.xboxLabel.toUpperCase(Locale.ROOT).equals(key)) {
                return input;
            }
            for (String alias : input.aliases) {
                if (alias.equals(key)) {
                    return input;
                }
            }
        }
        return fallback;
    }
}
