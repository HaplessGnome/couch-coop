package dev.couchcoop.profile;

import java.util.Map;

/**
 * A saved guest player. The name doubles as the offline account name, so the same profile gets
 * the same player data (inventory, position) in every world. Unset optional fields fall back to
 * the shared config. Serialised with Gson, so fields stay public and nullable.
 */
public final class Profile {
    public String name;
    /** A Minecraft username to borrow the skin of, or a path to a 64x64 PNG; null for the default skin. */
    public String skin;
    /** Arm model: true slim, false wide, null automatic (read from a PNG, or the account's own model). */
    public Boolean slim;
    public Double lookSensitivity;
    public Boolean invertLookY;
    /** Action to button overrides, keyed like the config file ({@code bind.jump} or {@code jump}). */
    public Map<String, String> bindings;
    /** Key of the controller this profile last played on, so the picker can suggest it first. */
    public String lastController;

    public Profile() {
    }

    public Profile(String name) {
        this.name = name;
    }

    public boolean hasSkin() {
        return skin != null && !skin.isBlank();
    }

}
