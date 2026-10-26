package dev.couchcoop.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.couchcoop.CouchCoop;
import dev.couchcoop.CouchCoopClient;
import dev.couchcoop.config.CouchCoopConfig;
import dev.couchcoop.config.CouchCoopConfig.Action;
import dev.couchcoop.craft.QuickCraft;
import dev.couchcoop.mixin.AbstractContainerScreenAccessor;
import dev.couchcoop.profile.ProfileScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.sdl.SDLGamepad.*;

/**
 * A per-window mouse pointer driven by the left stick, with D-pad jumps between slots and buttons. It never moves the OS cursor (there is
 * only one of those for all players); instead it feeds events straight to the open screen and
 * {@link dev.couchcoop.mixin.MouseHandlerMixin} reports its position for hover and tooltips.
 */
public final class VirtualCursor {
    /** GUI pixels per second at full deflection, relative to the GUI height. */
    private static final double SPEED_SCREEN_HEIGHTS = 1.1;
    private static final long DOUBLE_CLICK_MS = 250;

    // SDL keycodes Minecraft uses as KeyEvent#shortcutKey for these scancodes.
    private static final int SDLK_ESCAPE = 0x1B;
    private static final int SDLK_RETURN = 0x0D;
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
    private final HeldRepeat dpadRepeat = new HeldRepeat();

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

        if (pad.pressed(GamepadInput.OPTIONS) && current instanceof ProfileScreen) {
            // On the profile form Options means "next / done", as it does with the keyboard up.
            key(current, InputConstants.KEY_RETURN, SDLK_RETURN);
        } else if (pad.pressed(config.binding(Action.MENU_BACK)) || pad.pressed(GamepadInput.OPTIONS)) {
            key(current, InputConstants.KEY_ESCAPE, SDLK_ESCAPE);
        }
        Navigation.Direction direction = dpadDirection(pad);
        if (dpadRepeat.step(direction, System.currentTimeMillis())) {
            navigate(current, direction);
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

    private static Navigation.Direction dpadDirection(Gamepad pad) {
        if (pad.isDown(GamepadInput.DPAD_UP)) {
            return Navigation.Direction.UP;
        }
        if (pad.isDown(GamepadInput.DPAD_DOWN)) {
            return Navigation.Direction.DOWN;
        }
        if (pad.isDown(GamepadInput.DPAD_LEFT)) {
            return Navigation.Direction.LEFT;
        }
        if (pad.isDown(GamepadInput.DPAD_RIGHT)) {
            return Navigation.Direction.RIGHT;
        }
        return null;
    }

    /**
     * Inventories have no keyboard navigation, so the D-pad jumps the cursor between slots and
     * buttons there. Elsewhere the screen's own arrow-key focus handles it (including scrolling
     * lists) and the cursor follows whatever got focus, so pressing click activates it.
     */
    private void navigate(Screen current, Navigation.Direction direction) {
        if (current instanceof AbstractContainerScreen<?> container) {
            Navigation.Target target = Navigation.next(targets(container), x, y, direction);
            if (target != null) {
                moveTo(current, target.centreX(), target.centreY());
                return;
            }
        }
        switch (direction) {
            case UP -> key(current, InputConstants.KEY_UP, SDLK_UP);
            case DOWN -> key(current, InputConstants.KEY_DOWN, SDLK_DOWN);
            case LEFT -> key(current, InputConstants.KEY_LEFT, SDLK_LEFT);
            case RIGHT -> key(current, InputConstants.KEY_RIGHT, SDLK_RIGHT);
        }
        GuiEventListener focused = OnScreenKeyboard.focusedLeaf(current);
        if (focused != null) {
            ScreenRectangle area = focused.getRectangle();
            if (area.width() > 0 && area.height() > 0) {
                moveTo(current, area.left() + area.width() / 2.0, area.top() + area.height() / 2.0);
            }
        }
    }

    private static List<Navigation.Target> targets(AbstractContainerScreen<?> container) {
        List<Navigation.Target> targets = new ArrayList<>();
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
        int left = accessor.couchcoop$getLeftPos();
        int top = accessor.couchcoop$getTopPos();
        for (Slot slot : container.getMenu().slots) {
            if (slot.isActive()) {
                // Slots draw 16x16 inside an 18x18 cell.
                targets.add(new Navigation.Target(left + slot.x - 1, top + slot.y - 1, 18, 18));
            }
        }
        addWidgets(container, targets);
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && client.quickCraft().isActive()) {
            QuickCraft.Layout panel = client.quickCraft().layout();
            int shown = Math.min(panel.rows(), client.quickCraft().entries().size());
            for (int i = 0; i < shown; i++) {
                targets.add(new Navigation.Target(panel.left(), panel.top() + panel.firstRow() + i * panel.rowHeight(), panel.width(), panel.rowHeight()));
            }
        }
        return targets;
    }

    private static void addWidgets(ContainerEventHandler parent, List<Navigation.Target> targets) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.visible && widget.active) {
                    targets.add(new Navigation.Target(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
                }
            } else if (child instanceof ContainerEventHandler nested) {
                addWidgets(nested, targets);
            }
        }
    }

    private void moveTo(Screen current, double newX, double newY) {
        x = newX;
        y = newY;
        current.mouseMoved(x, y);
        current.afterMouseMove();
    }

    private MouseButtonEvent event(int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }

    private static void key(Screen current, int scancode, int keycode) {
        current.keyPressed(new KeyEvent(scancode, keycode, 0));
    }
}
