package dev.couchcoop;

import dev.couchcoop.config.CouchCoopConfig;
import dev.couchcoop.profile.ProfileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. Fabric and Forge each call {@link #init(Platform)} once on the client.
 */
public final class CouchCoop {
    public static final String MOD_ID = "couchcoop";
    public static final Logger LOGGER = LoggerFactory.getLogger("CouchCoop");

    /** Set on instances that were launched by another splitscreen instance. */
    public static final String PROP_CHILD = MOD_ID + ".child";
    /** Controller key the launched instance should claim on startup. */
    public static final String PROP_PAD = MOD_ID + ".pad";
    /** Name of the guest profile the launched instance plays as, if any. */
    public static final String PROP_PROFILE = MOD_ID + ".profile";

    private static Platform platform;
    private static CouchCoopConfig config;
    private static ProfileStore profiles;

    private CouchCoop() {
    }

    public static void init(Platform platform) {
        CouchCoop.platform = platform;
        CouchCoop.config = CouchCoopConfig.load(platform.configDir().resolve(MOD_ID + ".properties"));
        CouchCoop.profiles = new ProfileStore(platform.configDir().resolve(MOD_ID).resolve("profiles.json"));
        String profile = System.getProperty(PROP_PROFILE);
        if (profile != null) {
            profiles.find(profile).ifPresent(config::applyProfile);
        }
        LOGGER.info("Couch Co-op initialised on {} (child instance: {}, profile: {})", platform.loaderName(), isChild(), profile);
    }

    public static Platform platform() {
        return platform;
    }

    public static CouchCoopConfig config() {
        return config;
    }

    public static ProfileStore profiles() {
        return profiles;
    }

    public static boolean isChild() {
        return Boolean.getBoolean(PROP_CHILD);
    }

    public static boolean isInitialised() {
        return platform != null;
    }
}
