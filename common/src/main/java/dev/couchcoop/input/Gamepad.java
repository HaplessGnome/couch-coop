package dev.couchcoop.input;

import static org.lwjgl.sdl.SDLGamepad.*;

/**
 * One opened SDL gamepad with per-frame edge detection. Triggers are exposed as digital
 * inputs (with hysteresis) so they can be bound like buttons.
 */
public final class Gamepad {
    private static final float TRIGGER_PRESS = 0.55f;
    private static final float TRIGGER_RELEASE = 0.35f;
    private static final GamepadInput[] INPUTS = GamepadInput.values();

    final int instanceId;
    final long handle;
    private final String key;
    private final String name;
    private final int type;

    private final boolean[] down = new boolean[INPUTS.length];
    private final boolean[] previous = new boolean[INPUTS.length];
    private final float[] axes = new float[SDL_GAMEPAD_AXIS_COUNT];
    private boolean active;

    Gamepad(int instanceId, long handle, String key) {
        this.instanceId = instanceId;
        this.handle = handle;
        this.key = key;
        String sdlName = SDL_GetGamepadName(handle);
        this.name = sdlName == null ? "Controller" : sdlName;
        this.type = SDL_GetGamepadType(handle);
    }

    void poll() {
        System.arraycopy(down, 0, previous, 0, down.length);
        for (int axis = 0; axis < axes.length; axis++) {
            axes[axis] = Math.clamp(SDL_GetGamepadAxis(handle, axis) / 32767f, -1f, 1f);
        }

        active = false;
        for (GamepadInput input : INPUTS) {
            boolean value;
            if (input.triggerAxis >= 0) {
                float t = axes[input.triggerAxis];
                value = previous[input.ordinal()] ? t > TRIGGER_RELEASE : t > TRIGGER_PRESS;
            } else {
                value = input.button >= 0 && SDL_GetGamepadButton(handle, input.button);
            }
            down[input.ordinal()] = value;
            active |= value;
        }
        for (int axis = 0; axis <= SDL_GAMEPAD_AXIS_RIGHTY; axis++) {
            active |= Math.abs(axes[axis]) > 0.3f;
        }
    }

    /** Stable across processes, so several game instances agree on which pad is which. */
    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public boolean isPlayStation() {
        return type == SDL_GAMEPAD_TYPE_PS5 || type == SDL_GAMEPAD_TYPE_PS4 || type == SDL_GAMEPAD_TYPE_PS3;
    }

    /** The name printed on this controller's button, e.g. "Cross" on a DualSense and "A" on an Xbox pad. */
    public String label(GamepadInput input) {
        return input.label(isPlayStation());
    }

    public boolean isDown(GamepadInput input) {
        return down[input.ordinal()];
    }

    public boolean pressed(GamepadInput input) {
        return down[input.ordinal()] && !previous[input.ordinal()];
    }

    public boolean released(GamepadInput input) {
        return !down[input.ordinal()] && previous[input.ordinal()];
    }

    /** Raw axis value in [-1, 1]; stick Y axes are positive when pushed down. */
    public float axis(int sdlAxis) {
        return axes[sdlAxis];
    }

    /** True if anything is held or a stick is deflected this frame. */
    public boolean isActive() {
        return active;
    }

    /** Lights the player number on DualSense pads and sets the light bar colour. */
    public void showPlayer(int playerIndex) {
        SDL_SetGamepadPlayerIndex(handle, playerIndex);
        int rgb = switch (playerIndex) {
            case 0 -> 0x0040FF;
            case 1 -> 0xFF2020;
            case 2 -> 0x20FF40;
            default -> 0xFF40C0;
        };
        SDL_SetGamepadLED(handle, (byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
    }

    public void rumble(float strength, int millis) {
        short s = (short) Math.round(Math.clamp(strength, 0f, 1f) * 0xFFFF);
        SDL_RumbleGamepad(handle, s, s, millis);
    }

    boolean isConnected() {
        return SDL_GamepadConnected(handle);
    }

    void close() {
        SDL_CloseGamepad(handle);
    }
}
