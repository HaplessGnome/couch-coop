package dev.couchcoop.config;

import dev.couchcoop.CouchCoop;
import dev.couchcoop.input.GamepadInput;
import dev.couchcoop.profile.Profile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Plain properties file in the loader's config folder. Missing keys are filled with defaults and
 * written back, so the file always documents every option.
 */
public final class CouchCoopConfig {
    public enum Layout {
        /** Two players stacked vertically, like console Minecraft. */
        TOP_BOTTOM,
        SIDE_BY_SIDE
    }

    /**
     * Gameplay and menu actions a controller button can be bound to. Defaults follow Minecraft's
     * console layout on PlayStation, adapted where Java Edition has no matching action.
     */
    public enum Action {
        JUMP(GamepadInput.CROSS),
        SNEAK(GamepadInput.CIRCLE),
        // Consoles open crafting here; Java's inventory already holds the crafting grid.
        SWAP_HANDS(GamepadInput.SQUARE),
        INVENTORY(GamepadInput.TRIANGLE),
        SPRINT(GamepadInput.L3),
        ATTACK(GamepadInput.R2),
        USE(GamepadInput.L2),
        HOTBAR_PREV(GamepadInput.L1),
        HOTBAR_NEXT(GamepadInput.R1),
        PERSPECTIVE(GamepadInput.DPAD_UP),
        DROP(GamepadInput.DPAD_DOWN),
        CHAT(GamepadInput.DPAD_RIGHT),
        // Consoles emote here; Java has no emotes.
        PICK_BLOCK(GamepadInput.DPAD_LEFT),
        PLAYER_LIST(GamepadInput.TOUCHPAD),
        SCREENSHOT(GamepadInput.CREATE),
        PAUSE(GamepadInput.OPTIONS),
        MENU_CLICK(GamepadInput.CROSS),
        MENU_BACK(GamepadInput.CIRCLE),
        MENU_QUICK_MOVE(GamepadInput.SQUARE),
        MENU_RIGHT_CLICK(GamepadInput.TRIANGLE);

        public final GamepadInput defaultInput;

        Action(GamepadInput defaultInput) {
            this.defaultInput = defaultInput;
        }

        public String key() {
            return "bind." + name().toLowerCase(Locale.ROOT);
        }

        public static Action parse(String key) {
            for (Action action : values()) {
                if (action.key().equalsIgnoreCase(key) || action.name().equalsIgnoreCase(key)) {
                    return action;
                }
            }
            return null;
        }
    }

    public Layout layout = Layout.TOP_BOTTOM;
    public boolean coverTaskbar = true;
    public int splitFrameLimit = 60;
    public int maxPlayers = 4;
    public String playerNamePrefix = "Player";
    public double lookSensitivity = 1.0;
    public double cursorSpeed = 1.0;
    public double stickDeadzone = 0.15;
    public boolean invertLookY = false;
    public boolean childInstanceAudio = false;
    public boolean controllerLights = true;
    public boolean quickCraft = true;
    public boolean rumble = true;
    public double rumbleStrength = 1.0;
    public boolean adaptiveTriggers = true;
    /** How hard R2 resists while the attack recharges, 0-1. Kept light: strong resistance gets tiring. */
    public double attackTriggerStrength = 0.035;
    public final Map<Action, GamepadInput> bindings = new EnumMap<>(Action.class);

    private CouchCoopConfig() {
        for (Action action : Action.values()) {
            bindings.put(action, action.defaultInput);
        }
    }

    public GamepadInput binding(Action action) {
        return bindings.get(action);
    }

    /** Layers a guest profile's own controls over the shared settings, in memory only. */
    public void applyProfile(Profile profile) {
        if (profile.lookSensitivity != null) {
            lookSensitivity = profile.lookSensitivity;
        }
        if (profile.invertLookY != null) {
            invertLookY = profile.invertLookY;
        }
        if (profile.bindings != null) {
            profile.bindings.forEach((key, value) -> {
                Action action = Action.parse(key);
                if (action != null) {
                    bindings.put(action, GamepadInput.parse(value, bindings.get(action)));
                }
            });
        }
    }

    public static CouchCoopConfig load(Path file) {
        CouchCoopConfig config = new CouchCoopConfig();
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                CouchCoop.LOGGER.warn("Could not read {}, using defaults", file, e);
            }
        }

        config.layout = parseEnum(props.getProperty("layout"), Layout.class, config.layout);
        config.coverTaskbar = Boolean.parseBoolean(props.getProperty("coverTaskbar", Boolean.toString(config.coverTaskbar)));
        config.splitFrameLimit = Math.max(0, parseInt(props.getProperty("splitFrameLimit"), config.splitFrameLimit));
        config.maxPlayers = Math.clamp(parseInt(props.getProperty("maxPlayers"), config.maxPlayers), 1, 4);
        config.playerNamePrefix = props.getProperty("playerNamePrefix", config.playerNamePrefix).trim();
        config.lookSensitivity = parseDouble(props.getProperty("lookSensitivity"), config.lookSensitivity);
        config.cursorSpeed = parseDouble(props.getProperty("cursorSpeed"), config.cursorSpeed);
        config.stickDeadzone = Math.clamp(parseDouble(props.getProperty("stickDeadzone"), config.stickDeadzone), 0.0, 0.9);
        config.invertLookY = Boolean.parseBoolean(props.getProperty("invertLookY", Boolean.toString(config.invertLookY)));
        config.childInstanceAudio = Boolean.parseBoolean(props.getProperty("childInstanceAudio", Boolean.toString(config.childInstanceAudio)));
        config.controllerLights = Boolean.parseBoolean(props.getProperty("controllerLights", Boolean.toString(config.controllerLights)));
        config.quickCraft = Boolean.parseBoolean(props.getProperty("quickCraft", Boolean.toString(config.quickCraft)));
        config.rumble = Boolean.parseBoolean(props.getProperty("rumble", Boolean.toString(config.rumble)));
        config.rumbleStrength = Math.clamp(parseDouble(props.getProperty("rumbleStrength"), config.rumbleStrength), 0.0, 1.0);
        config.adaptiveTriggers = Boolean.parseBoolean(props.getProperty("adaptiveTriggers", Boolean.toString(config.adaptiveTriggers)));
        config.attackTriggerStrength = Math.clamp(parseDouble(props.getProperty("attackTriggerStrength"), config.attackTriggerStrength), 0.0, 1.0);
        for (Action action : Action.values()) {
            config.bindings.put(action, GamepadInput.parse(props.getProperty(action.key()), action.defaultInput));
        }

        config.save(file);
        return config;
    }

    private void save(Path file) {
        StringBuilder out = new StringBuilder("""
            # Couch Co-op config
            # layout: TOP_BOTTOM or SIDE_BY_SIDE (how two players share the screen)
            # coverTaskbar: true tiles over the whole monitor like one fullscreen game; false leaves the taskbar visible
            # splitFrameLimit: max FPS per window while the screen is split, so windows share the GPU evenly (0 = off)
            # quickCraft: list craftable items beside the inventory and crafting table (L1/R1 pick, R2 craft, L2 craft all)
            # rumble: vibrate when hurt or breaking blocks (rumbleStrength 0-1)
            # adaptiveTriggers: DualSense L2 stiffens while drawing a bow, R2 resists while the attack recharges
            # attackTriggerStrength: how hard R2 resists, 0-1 (0 = off)
            # bind.*: CROSS CIRCLE SQUARE TRIANGLE L1 R1 L2 R2 L3 R3 CREATE OPTIONS PS TOUCHPAD MUTE
            #         DPAD_UP DPAD_DOWN DPAD_LEFT DPAD_RIGHT NONE
            # Xbox pads use the button in the same place: CROSS=A CIRCLE=B SQUARE=X TRIANGLE=Y L1=LB R1=RB
            # L2=LT R2=RT L3=LS R3=RS CREATE=View OPTIONS=Menu PS=Guide MUTE=Share. Xbox names work here too.
            # Guest profiles can override lookSensitivity, invertLookY and bind.* in couchcoop/profiles.json.
            """);
        line(out, "layout", layout.name());
        line(out, "coverTaskbar", coverTaskbar);
        line(out, "splitFrameLimit", splitFrameLimit);
        line(out, "maxPlayers", maxPlayers);
        line(out, "playerNamePrefix", playerNamePrefix);
        line(out, "lookSensitivity", lookSensitivity);
        line(out, "cursorSpeed", cursorSpeed);
        line(out, "stickDeadzone", stickDeadzone);
        line(out, "invertLookY", invertLookY);
        line(out, "childInstanceAudio", childInstanceAudio);
        line(out, "controllerLights", controllerLights);
        line(out, "quickCraft", quickCraft);
        line(out, "rumble", rumble);
        line(out, "rumbleStrength", rumbleStrength);
        line(out, "adaptiveTriggers", adaptiveTriggers);
        line(out, "attackTriggerStrength", attackTriggerStrength);
        for (Action action : Action.values()) {
            line(out, action.key(), bindings.get(action).name());
        }

        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(out.toString());
            }
        } catch (IOException e) {
            CouchCoop.LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static void line(StringBuilder out, String key, Object value) {
        out.append(key).append('=').append(value).append('\n');
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
