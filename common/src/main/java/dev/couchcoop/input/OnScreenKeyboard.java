package dev.couchcoop.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.couchcoop.CouchCoop;
import dev.couchcoop.config.CouchCoopConfig.Action;
import dev.couchcoop.input.KeyboardLayout.Cursor;
import dev.couchcoop.input.KeyboardLayout.Key;
import dev.couchcoop.profile.ProfileScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

import static org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX;
import static org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY;

/**
 * Typing with a controller. Shows whenever a text box has focus (chat, signs, books, anvils,
 * search boxes, profile names) and types into it through the screen's normal key events, so
 * it works in any window, focused or not.
 */
public final class OnScreenKeyboard {
    // SDL keycodes Minecraft uses as KeyEvent#shortcutKey for these scancodes.
    private static final int SDLK_BACKSPACE = 0x08;
    private static final int SDLK_TAB = 0x09;
    private static final int SDLK_RETURN = 0x0D;
    private static final int SDLK_RIGHT = 0x4000004F;
    private static final int SDLK_LEFT = 0x40000050;
    private static final float STICK_THRESHOLD = 0.6f;

    private final KeyboardLayout layout = new KeyboardLayout();
    private final HeldRepeat repeat = new HeldRepeat();
    private Cursor cursor = new Cursor(1, 0);
    private boolean symbols;
    private boolean shift;
    private boolean visible;
    private boolean atTop;
    /** The screen and focus seen last frame, to notice the player moving focus. */
    private Screen seenScreen;
    private GuiEventListener lastFocus;
    /** The player asked to type: a typing screen opened, or they went to a text box themselves. */
    private boolean armed;
    /** A click happened last frame; if it landed on a text box, that box is meant for typing. */
    private boolean clickPending;

    public boolean isVisible() {
        return visible;
    }

    /** Whether to draw at the top of the screen, so the text box being typed in stays visible. */
    public boolean atTop() {
        return atTop;
    }

    public List<List<Key>> rows() {
        return layout.rows(symbols);
    }

    public Cursor cursor() {
        return cursor;
    }

    public boolean shift() {
        return shift;
    }

    /**
     * Decides whether the keyboard shows this frame. A text box that merely starts out focused,
     * like the world list's search bar, doesn't open it: only typing screens (chat, signs, books,
     * the profile form) do, or the player going to a text box by clicking it or moving onto it.
     * {@code cursorX}/{@code cursorY} are the menu cursor, to tell where a click landed.
     */
    public boolean update(Screen screen, Gamepad pad, double cursorX, double cursorY) {
        GuiEventListener focus = focusedLeaf(screen);
        if (screen != seenScreen) {
            seenScreen = screen;
            lastFocus = focus;
            armed = isTypingScreen(screen);
            clickPending = false;
        } else {
            if (clickPending && focus != null && contains(focus.getRectangle(), cursorX, cursorY)) {
                armed = true;
            }
            if (focus != lastFocus) {
                // Focus moved (D-pad, a click, Tab): show for a text box the player went to.
                armed = focus != null;
                lastFocus = focus;
            }
            clickPending = false;
        }
        boolean show = armed && wantsText(screen, focus);
        if (!show && pad.pressed(CouchCoop.config().binding(Action.MENU_CLICK))) {
            // The menu cursor handles this click after us; check next frame what it hit.
            clickPending = true;
        }
        if (show && !visible) {
            ScreenRectangle area = focus == null ? null : focus.getRectangle();
            atTop = area != null && area.height() > 0 && area.top() + area.height() / 2 > screen.height / 2;
        }
        visible = show;
        return visible;
    }

    private static boolean isTypingScreen(Screen screen) {
        return screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen
            || screen instanceof BookEditScreen || screen instanceof ProfileScreen;
    }

    private static boolean contains(ScreenRectangle area, double x, double y) {
        return x >= area.left() && x < area.left() + area.width() && y >= area.top() && y < area.top() + area.height();
    }

    public void frame(Screen screen, Gamepad pad) {
        Navigation.Direction direction = direction(pad);
        if (repeat.step(direction, System.currentTimeMillis())) {
            cursor = layout.move(cursor, direction, symbols);
        }

        if (pad.pressed(GamepadInput.CROSS)) {
            press(screen, layout.key(cursor, symbols));
        }
        if (pad.pressed(GamepadInput.SQUARE)) {
            key(screen, InputConstants.KEY_BACKSPACE, SDLK_BACKSPACE);
        }
        if (pad.pressed(GamepadInput.TRIANGLE)) {
            type(screen, " ");
        }
        if (pad.pressed(GamepadInput.L1)) {
            shift = !shift;
        }
        if (pad.pressed(GamepadInput.R1)) {
            toggleSymbols();
        }
        if (pad.pressed(GamepadInput.L2)) {
            key(screen, InputConstants.KEY_LEFT, SDLK_LEFT);
        }
        if (pad.pressed(GamepadInput.R2)) {
            key(screen, InputConstants.KEY_RIGHT, SDLK_RIGHT);
        }
        if (pad.pressed(GamepadInput.R3)) {
            key(screen, InputConstants.KEY_TAB, SDLK_TAB);
        }
        if (pad.pressed(GamepadInput.OPTIONS)) {
            key(screen, InputConstants.KEY_RETURN, SDLK_RETURN);
        }
        if (pad.pressed(GamepadInput.CIRCLE)) {
            // Hidden until the player clicks the text box or moves focus again.
            armed = false;
            visible = false;
        }
    }

    /** Button hints, in the controller's own button names. */
    public String[] help(Gamepad pad) {
        return new String[] {
            pad.label(GamepadInput.CROSS) + " type   " + pad.label(GamepadInput.SQUARE) + " delete   "
                + pad.label(GamepadInput.TRIANGLE) + " space   " + pad.label(GamepadInput.OPTIONS) + " enter",
            pad.label(GamepadInput.L1) + " shift   " + pad.label(GamepadInput.R1) + " symbols   "
                + pad.label(GamepadInput.L2) + "/" + pad.label(GamepadInput.R2) + " move   " + pad.label(GamepadInput.R3) + " tab   "
                + pad.label(GamepadInput.CIRCLE) + " hide",
        };
    }

    private void press(Screen screen, Key key) {
        switch (key.kind()) {
            case CHAR, SPACE -> {
                type(screen, shift ? key.shifted() : key.text());
                shift = false;
            }
            case SHIFT -> shift = !shift;
            case SYMBOLS -> toggleSymbols();
            case BACKSPACE -> key(screen, InputConstants.KEY_BACKSPACE, SDLK_BACKSPACE);
            case TAB -> key(screen, InputConstants.KEY_TAB, SDLK_TAB);
            case DONE -> key(screen, InputConstants.KEY_RETURN, SDLK_RETURN);
        }
    }

    private void toggleSymbols() {
        symbols = !symbols;
        cursor = layout.clamp(cursor, symbols);
    }

    private static void type(Screen screen, String text) {
        text.codePoints().forEach(codepoint -> screen.charTyped(new CharacterEvent(codepoint)));
    }

    private static void key(Screen screen, int scancode, int keycode) {
        screen.keyPressed(new KeyEvent(scancode, keycode, 0));
    }

    private static Navigation.Direction direction(Gamepad pad) {
        float x = pad.axis(SDL_GAMEPAD_AXIS_LEFTX);
        float y = pad.axis(SDL_GAMEPAD_AXIS_LEFTY);
        if (pad.isDown(GamepadInput.DPAD_UP) || y < -STICK_THRESHOLD) {
            return Navigation.Direction.UP;
        }
        if (pad.isDown(GamepadInput.DPAD_DOWN) || y > STICK_THRESHOLD) {
            return Navigation.Direction.DOWN;
        }
        if (pad.isDown(GamepadInput.DPAD_LEFT) || x < -STICK_THRESHOLD) {
            return Navigation.Direction.LEFT;
        }
        if (pad.isDown(GamepadInput.DPAD_RIGHT) || x > STICK_THRESHOLD) {
            return Navigation.Direction.RIGHT;
        }
        return null;
    }

    private static boolean wantsText(Screen screen, GuiEventListener focus) {
        if (screen instanceof AbstractSignEditScreen) {
            return true;
        }
        if (focus instanceof EditBox box) {
            return box.canConsumeInput();
        }
        if (focus instanceof MultiLineEditBox box) {
            return box.isFocused() && box.visible;
        }
        return false;
    }

    static GuiEventListener focusedLeaf(ContainerEventHandler parent) {
        GuiEventListener focused = parent.getFocused();
        while (focused instanceof ContainerEventHandler nested && nested.getFocused() != null) {
            focused = nested.getFocused();
        }
        return focused;
    }
}
