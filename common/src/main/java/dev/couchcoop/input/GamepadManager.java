package dev.couchcoop.input;

import dev.couchcoop.CouchCoop;
import org.lwjgl.sdl.SDL_GUID;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLGamepad.*;
import static org.lwjgl.sdl.SDLHints.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLStdinc.SDL_free;

/**
 * Owns SDL's gamepad subsystem. Minecraft 26.x already runs on SDL3 but only initialises video,
 * so we start the gamepad subsystem ourselves. SDL ships mappings for Xbox One/Series and
 * DualSense/DualShock pads, so both work without extra setup.
 *
 * Every instance opens every pad: unclaimed pads still need to be read to spot a player joining.
 */
public final class GamepadManager {
    private static final int REFRESH_INTERVAL_FRAMES = 30;

    private final Map<Integer, Gamepad> pads = new LinkedHashMap<>();
    private boolean initialised;
    private boolean failed;
    private int framesUntilRefresh;

    public void update() {
        if (!ensureInitialised()) {
            return;
        }
        if (--framesUntilRefresh <= 0) {
            framesUntilRefresh = REFRESH_INTERVAL_FRAMES;
            refresh();
        }
        for (Gamepad pad : pads.values()) {
            pad.poll();
        }
    }

    public Collection<Gamepad> pads() {
        return pads.values();
    }

    public Gamepad find(String key) {
        for (Gamepad pad : pads.values()) {
            if (pad.key().equals(key)) {
                return pad;
            }
        }
        return null;
    }

    private boolean ensureInitialised() {
        if (initialised || failed) {
            return initialised;
        }
        // Without this SDL drops controller input while the window is unfocused, and in
        // splitscreen only one window can have focus.
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
        if (!SDL_InitSubSystem(SDL_INIT_GAMEPAD)) {
            failed = true;
            CouchCoop.LOGGER.error("Could not initialise SDL gamepad support: {}", SDL_GetError());
            return false;
        }
        initialised = true;
        return true;
    }

    private void refresh() {
        List<Integer> ids = new ArrayList<>();
        IntBuffer buffer = SDL_GetGamepads();
        if (buffer != null) {
            try {
                for (int i = buffer.position(); i < buffer.limit(); i++) {
                    ids.add(buffer.get(i));
                }
            } finally {
                SDL_free(buffer);
            }
        }

        pads.values().removeIf(pad -> {
            if (ids.contains(pad.instanceId) && pad.isConnected()) {
                return false;
            }
            CouchCoop.LOGGER.info("Controller disconnected: {}", pad.name());
            pad.close();
            return true;
        });

        Map<String, Integer> guidCounts = new HashMap<>();
        for (int id : ids) {
            String path = SDL_GetGamepadPathForID(id);
            String guid = guidString(id);
            int ordinal = guidCounts.merge(guid, 1, Integer::sum) - 1;
            if (pads.containsKey(id)) {
                continue;
            }
            long handle = SDL_OpenGamepad(id);
            if (handle == MemoryUtil.NULL) {
                CouchCoop.LOGGER.warn("Could not open controller {}: {}", id, SDL_GetError());
                continue;
            }
            Set<String> inUse = new HashSet<>();
            for (Gamepad open : pads.values()) {
                inUse.add(open.key());
            }
            String key = stableKey(SDL_GetGamepadSerial(handle), path, guid, ordinal, inUse);
            Gamepad pad = new Gamepad(id, handle, key);
            pads.put(id, pad);
            CouchCoop.LOGGER.info("Controller connected: {} ({})", pad.name(), key);
        }
    }

    /**
     * SDL instance ids differ between processes, so pads need a key every instance agrees on.
     * A serial number (a DualSense reports its Bluetooth address) is best: it stays the same
     * over USB and Bluetooth, so a player keeps their controller when they plug it in. Pads
     * without one fall back to device path, then GUID plus position among pads with that GUID.
     */
    static String stableKey(String serial, String path, String guid, int ordinal, Set<String> inUse) {
        String normalised = serial == null ? "" : serial.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
        // Cheap pads sometimes report a blank or shared serial; never let two pads share a key.
        if (!normalised.isEmpty() && !normalised.chars().allMatch(c -> c == '0') && !inUse.contains("serial:" + normalised)) {
            return "serial:" + normalised;
        }
        if (path != null && !path.isBlank()) {
            return "path:" + path;
        }
        return "guid:" + guid + "#" + ordinal;
    }

    private static String guidString(int id) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_GUID guid = SDL_GetGamepadGUIDForID(id, SDL_GUID.malloc(stack));
            ByteBuffer text = stack.malloc(33);
            org.lwjgl.sdl.SDLGUID.SDL_GUIDToString(guid, text);
            return MemoryUtil.memASCII(text, MemoryUtil.memLengthNT1(text));
        }
    }
}
