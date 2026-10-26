package dev.couchcoop;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The few things the shared code needs from the mod loader.
 */
public interface Platform {
    String loaderName();

    Path configDir();

    /**
     * Main class followed by game arguments, used to relaunch the game when the JVM's own
     * command line is unavailable (for example under launchers that pass arguments over stdin).
     */
    Optional<List<String>> fallbackLaunchCommand();
}
