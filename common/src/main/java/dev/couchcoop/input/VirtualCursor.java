package dev.couchcoop.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.couchcoop.CouchCoop;
import dev.couchcoop.config.CouchCoopConfig;
import dev.couchcoop.config.CouchCoopConfig.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import static org.lwjgl.sdl.SDLGamepad.*;

/**
 * A per-window mouse pointer driven by the left stick. It never moves the OS cursor (there is
 * only one of those for all players); instead it feeds events straight to the open screen and
 * {@link dev.couchcoop.mixin.MouseHandlerMixin} reports its position for hover and tooltips.
 */
public final class VirtualCursor {
    /** GUI pixels per second at full deflection, relative to the GUI height. */
    private static final double SPEED_SCREEN_HEIGHTS = 1.1;
    private static final long DOUBLE_CLICK_MS = 250;

    // SDL keycodes Minecraft uses as KeyEvent#shortcutKey for these scancodes.
    private static final int SDLK_ESCAPE = 0x1B;
    private static final int SDLK_RIGHT = 0x4000004F;
    private static final int SDLK_LEFT = 0x40000050;
    private static final int SDLK_DOWN = 0x40000051;
    private static final int SDLK_UP = 0x40000052;

    private Screen screen;
    private double x;
    private double y;
    private int heldButton = -1;
    private int heldModifiers;
    private double scrollRemainder;
    private long lastClickTime;

    public boolean isActive() {
        return screen != null;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    void leaveScreen() {
        screen = null;
        heldButton = -1;
    }

    void frame(Minecraft minecraft, Screen current, Gamepad pad, double deltaSeconds) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (current != screen) {
            screen = current;
            heldButton = -1;
            x = width / 2.0;
            y = height / 2.0;
        }

        CouchCoopConfig config = CouchCoop.config();
        double speed = SPEED_SCREEN_HEIGHTS * height * config.cursorSpeed * deltaSeconds;
        double dx = ControllerInput.curve(pad.axis(SDL_GAMEPAD_AXIS_LEFTX), config.stickDeadzone) * speed;
        double dy = ControllerInput.curve(pad.axis(SDL_GAMEPAD_AXIS_LEFTY), config.stickDeadzone) * speed;
        if (dx != 0 || dy != 0) {
            x = Math.clamp(x + dx, 0, width - 1);
            y = Math.clamp(y + dy, 0, height - 1);
            current.mouseMoved(x, y);
            if (heldButton >= 0) {
                current.mouseDragged(event(heldButton, heldModifiers), dx, dy);
            }
            current.afterMouseMove();
        }

        button(current, pad, config.binding(Action.MENU_CLICK), InputConstants.MOUSE_BUTTON_LEFT, 0);
        button(current, pad, config.binding(Action.MENU_QUICK_MOVE), InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT);
        button(current, pad, config.binding(Action.MENU_RIGHT_CLICK), InputConstants.MOUSE_BUTTON_RIGHT, 0);

        if (pad.pressed(config.binding(Action.MENU_BACK)) || pad.pressed(GamepadInput.OPTIONS)) {
            key(current, InputConstants.KEY_ESCAPE, SDLK_ESCAPE);
        }
        if (pad.pressed(GamepadInput.DPAD_UP)) {
            key(current, InputConstants.KEY_UP, SDLK_UP);
        }
        if (pad.pressed(GamepadInput.DPAD_DOWN)) {
            key(current, InputConstants.KEY_DOWN, SDLK_DOWN);
        }
        if (pad.pressed(GamepadInput.DPAD_LEFT)) {
            key(current, InputConstants.KEY_LEFT, SDLK_LEFT);
        }
        if (pad.pressed(GamepadInput.DPAD_RIGHT)) {
            key(current, InputConstants.KEY_RIGHT, SDLK_RIGHT);
        }

        // Right stick scrolls lists, one notch at a time.
        scrollRemainder -= ControllerInput.curve(pad.axis(SDL_GAMEPAD_AXIS_RIGHTY), config.stickDeadzone) * 12 * deltaSeconds;
        while (Math.abs(scrollRemainder) >= 1) {
            double notch = Math.signum(scrollRemainder);
            scrollRemainder -= notch;
            current.mouseScrolled(x, y, 0, notch);
        }
    }

    private void button(Screen current, Gamepad pad, GamepadInput input, int button, int modifiers) {
        if (pad.pressed(input) && heldButton < 0) {
            long now = System.currentTimeMillis();
            boolean doubleClick = now - lastClickTime < DOUBLE_CLICK_MS;
            lastClickTime = now;
            heldButton = button;
            heldModifiers = modifiers;
            current.afterMouseAction();
            current.mouseClicked(event(button, modifiers), doubleClick);
        } else if (pad.released(input) && heldButton == button && heldModifiers == modifiers) {
            heldButton = -1;
            current.mouseReleased(event(button, modifiers));
        }
    }

    private MouseButtonEvent event(int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }

    private static void key(Screen current, int scancode, int keycode) {
        current.keyPressed(new KeyEvent(scancode, keycode, 0));
    }
}
