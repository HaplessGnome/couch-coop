package dev.couchcoop.input;

import dev.couchcoop.CouchCoop;
import dev.couchcoop.config.CouchCoopConfig;
import dev.couchcoop.config.CouchCoopConfig.Action;
import dev.couchcoop.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.sdl.SDLGamepad.*;

/**
 * Turns the instance's assigned gamepad into Minecraft input. Buttons drive the real
 * {@link KeyMapping}s, so anything that reads vanilla keybinds (including other mods) sees them.
 * Mappings are only touched on button transitions, which leaves the keyboard usable alongside.
 */
public final class ControllerInput {
    /** Degrees per second at full stick deflection with sensitivity 1.0. */
    private static final double YAW_SPEED = 320.0;
    private static final double PITCH_SPEED = 220.0;
    /** {@link net.minecraft.world.entity.Entity#turn} multiplies its input by this. */
    private static final double TURN_SCALE = 0.15;

    private final VirtualCursor cursor = new VirtualCursor();
    private final OnScreenKeyboard keyboard = new OnScreenKeyboard();
    private final List<Binding> bindings = new ArrayList<>();
    private boolean bindingsBuilt;
    private Vec2 movement = Vec2.ZERO;

    public VirtualCursor cursor() {
        return cursor;
    }

    public OnScreenKeyboard keyboard() {
        return keyboard;
    }

    /** Left stick movement for {@link dev.couchcoop.mixin.KeyboardInputMixin}, or zero. */
    public Vec2 movement() {
        return movement;
    }

    public void frame(Minecraft minecraft, Gamepad pad, double deltaSeconds) {
        if (!bindingsBuilt) {
            buildBindings(minecraft.options);
        }
        if (pad.isActive()) {
            minecraft.getFramerateLimitTracker().onInputReceived();
        }

        if (minecraft.gui.overlay() != null) {
            releaseAll();
            movement = Vec2.ZERO;
            return;
        }
        if (minecraft.gui.screen() != null) {
            releaseAll();
            movement = Vec2.ZERO;
            Screen screen = minecraft.gui.screen();
            if (keyboard.update(screen, pad)) {
                keyboard.frame(screen, pad);
            } else {
                cursor.frame(minecraft, screen, pad, deltaSeconds);
            }
            return;
        }
        cursor.leaveScreen();

        LocalPlayer player = minecraft.player;
        if (player == null) {
            movement = Vec2.ZERO;
            return;
        }

        CouchCoopConfig config = CouchCoop.config();
        movement = readMovement(pad, config);
        look(player, pad, config, deltaSeconds);

        for (Binding binding : bindings) {
            binding.update(pad);
        }

        Inventory inventory = player.getInventory();
        int size = Inventory.getSelectionSize();
        if (pad.pressed(config.binding(Action.HOTBAR_PREV))) {
            inventory.setSelectedSlot((inventory.getSelectedSlot() + size - 1) % size);
        }
        if (pad.pressed(config.binding(Action.HOTBAR_NEXT))) {
            inventory.setSelectedSlot((inventory.getSelectedSlot() + 1) % size);
        }
        // Vanilla takes screenshots straight from the keyboard handler, not through a KeyMapping.
        if (pad.pressed(config.binding(Action.SCREENSHOT))) {
            Screenshot.grab(minecraft, false);
        }
        if (pad.pressed(config.binding(Action.PAUSE))) {
            releaseAll();
            minecraft.pauseGame(false);
        }
    }

    /** Called when the instance loses its pad, so nothing stays held down. */
    public void reset() {
        releaseAll();
        movement = Vec2.ZERO;
        cursor.leaveScreen();
    }

    private Vec2 readMovement(Gamepad pad, CouchCoopConfig config) {
        float x = pad.axis(SDL_GAMEPAD_AXIS_LEFTX);
        float y = pad.axis(SDL_GAMEPAD_AXIS_LEFTY);
        float magnitude = (float) Math.sqrt(x * x + y * y);
        float deadzone = (float) config.stickDeadzone;
        if (magnitude <= deadzone) {
            return Vec2.ZERO;
        }
        // Rescale so movement starts from zero at the deadzone edge and caps at 1.
        float scaled = Math.min(1f, (magnitude - deadzone) / (1f - deadzone)) / magnitude;
        // ClientInput uses (left, forward); SDL's Y axis is positive downwards.
        return new Vec2(-x * scaled, -y * scaled);
    }

    private void look(LocalPlayer player, Gamepad pad, CouchCoopConfig config, double deltaSeconds) {
        double x = curve(pad.axis(SDL_GAMEPAD_AXIS_RIGHTX), config.stickDeadzone);
        double y = curve(pad.axis(SDL_GAMEPAD_AXIS_RIGHTY), config.stickDeadzone);
        if (x == 0 && y == 0) {
            return;
        }
        double speed = config.lookSensitivity * (player.isScoping() ? 0.25 : 1.0);
        double yaw = x * YAW_SPEED * speed * deltaSeconds;
        double pitch = y * PITCH_SPEED * speed * deltaSeconds * (config.invertLookY ? -1 : 1);
        player.turn(yaw / TURN_SCALE, pitch / TURN_SCALE);
    }

    /** Deadzone plus a squared response so small stick movements allow precise aim. */
    static double curve(float value, double deadzone) {
        double magnitude = Math.abs(value);
        if (magnitude <= deadzone) {
            return 0;
        }
        double normalised = (magnitude - deadzone) / (1 - deadzone);
        return Math.copySign(normalised * normalised, value);
    }

    private void releaseAll() {
        for (Binding binding : bindings) {
            binding.release();
        }
    }

    private void buildBindings(Options options) {
        bindingsBuilt = true;
        bindings.add(new Binding(Action.JUMP, options.keyJump));
        bindings.add(new Binding(Action.SNEAK, options.keyShift));
        bindings.add(new Binding(Action.SPRINT, options.keySprint));
        bindings.add(new Binding(Action.ATTACK, options.keyAttack));
        bindings.add(new Binding(Action.USE, options.keyUse));
        bindings.add(new Binding(Action.DROP, options.keyDrop));
        bindings.add(new Binding(Action.SWAP_HANDS, options.keySwapOffhand));
        bindings.add(new Binding(Action.INVENTORY, options.keyInventory));
        bindings.add(new Binding(Action.PICK_BLOCK, options.keyPickItem));
        bindings.add(new Binding(Action.PERSPECTIVE, options.keyTogglePerspective));
        bindings.add(new Binding(Action.PLAYER_LIST, options.keyPlayerList));
        bindings.add(new Binding(Action.CHAT, options.keyChat));
    }

    private static final class Binding {
        private final Action action;
        private final KeyMapping mapping;
        private boolean held;

        Binding(Action action, KeyMapping mapping) {
            this.action = action;
            this.mapping = mapping;
        }

        void update(Gamepad pad) {
            GamepadInput input = CouchCoop.config().binding(action);
            if (pad.pressed(input)) {
                held = true;
                KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
                accessor.couchcoop$setClickCount(accessor.couchcoop$getClickCount() + 1);
                mapping.setDown(true);
            } else if (held && !pad.isDown(input)) {
                release();
            }
        }

        void release() {
            if (held) {
                held = false;
                mapping.setDown(false);
            }
        }
    }
}
