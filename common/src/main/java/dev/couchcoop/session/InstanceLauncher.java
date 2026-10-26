package dev.couchcoop.session;

import dev.couchcoop.CouchCoop;
import net.minecraft.core.UUIDUtil;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Starts another copy of the running game for an extra player, reusing this JVM's own command
 * line with an offline profile and a quick-play address so it joins the host's world directly.
 */
public final class InstanceLauncher {
    /** Launchers whose main class only reads the real command over stdin. */
    private static final Set<String> WRAPPER_MAIN_CLASSES = Set.of(
        "org.prismlauncher.EntryPoint",
        "org.multimc.EntryPoint",
        "org.polymc.EntryPoint"
    );
    /** Arguments that identify the host's account or session; never passed to a guest. */
    /**
     * Launchers that start the game through a class of their own which first phones home, then
     * calls the real main class named in its first argument. A guest can't phone home (the
     * launcher only answers the instance it started), so guests skip straight to the real class.
     */
    private static final Set<String> RELAUNCH_MAIN_CLASSES = Set.of(
        "com.modrinth.theseus.MinecraftLaunch"
    );
    /** JVM flags that only make sense for a relaunch wrapper talking to its launcher. */
    private static final List<String> WRAPPER_JVM_PREFIXES = List.of(
        "-Dmodrinth.internal.",
        "-Dmodrinth.process."
    );
    private static final Set<String> DROPPED_GAME_ARGS = Set.of(
        "--username", "--uuid", "--accessToken", "--xuid", "--clientId", "--userType",
        "--quickPlayPath", "--quickPlaySingleplayer", "--quickPlayMultiplayer", "--quickPlayRealms",
        "--fullscreen", "--demo"
    );

    public record Result(boolean started, String error) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result failed(String error) {
            return new Result(false, error);
        }
    }

    private InstanceLauncher() {
    }

    /** @param profileName guest profile whose settings the new window applies, or null */
    public static Result launch(Path workDir, String playerName, String padKey, String address, String profileName) {
        Optional<List<String>> mainAndArgs = mainClassAndGameArgs();
        if (mainAndArgs.isEmpty()) {
            return Result.failed("This launcher hides the game's command line. Start another instance manually.");
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.addAll(jvmArguments());
        command.add("-D" + CouchCoop.PROP_CHILD + "=true");
        command.add("-D" + CouchCoop.PROP_PAD + "=" + padKey);
        if (profileName != null) {
            command.add("-D" + CouchCoop.PROP_PROFILE + "=" + profileName);
        }
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isEmpty()) {
            command.add("-cp");
            command.add(classPath);
        }

        List<String> game = mainAndArgs.get();
        command.add(game.getFirst());
        command.addAll(filterGameArgs(game.subList(1, game.size())));
        command.add("--username");
        command.add(playerName);
        command.add("--uuid");
        command.add(UUIDUtil.createOfflinePlayerUUID(playerName).toString().replace("-", ""));
        command.add("--accessToken");
        command.add("0");
        command.add("--quickPlayMultiplayer");
        command.add(address);

        try {
            // An argument file keeps us clear of Windows' 32k command line limit.
            Path argFile = workDir.resolve("launch-" + playerName + ".args");
            Path log = workDir.resolve("launch-" + playerName + ".log");
            Files.writeString(argFile, toArgFile(command.subList(1, command.size())), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(command.getFirst(), "@" + argFile)
                .directory(new File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
            CouchCoop.LOGGER.info("Started {} as process {} (log: {})", playerName, process.pid(), log);
            return Result.ok();
        } catch (IOException e) {
            CouchCoop.LOGGER.error("Could not start a game instance for {}", playerName, e);
            return Result.failed("Could not start the game: " + e.getMessage());
        }
    }

    /**
     * The JVM keeps the original main class and program arguments in {@code sun.java.command},
     * space-joined. Minecraft's arguments are all {@code --name value} pairs, so values that
     * contained spaces are rebuilt by joining everything up to the next {@code --name}.
     */
    private static Optional<List<String>> mainClassAndGameArgs() {
        String raw = System.getProperty("sun.java.command", "").trim();
        String first = raw.isEmpty() ? "" : raw.split(" ")[0];
        if (first.isEmpty() || WRAPPER_MAIN_CLASSES.contains(first) || first.endsWith(".jar")) {
            return CouchCoop.platform().fallbackLaunchCommand();
        }
        return Optional.of(parseCommand(raw));
    }

    /**
     * Splits the JVM's original main class and program arguments. Minecraft's arguments are all
     * {@code --name value} pairs, so values that contained spaces are rebuilt by joining
     * everything up to the next {@code --name}. Relaunch wrappers are replaced by the main class
     * they would have called.
     */
    static List<String> parseCommand(String raw) {
        String[] tokens = raw.trim().split(" ");
        List<String> result = new ArrayList<>();
        int start = 1;
        if (RELAUNCH_MAIN_CLASSES.contains(tokens[0]) && tokens.length > 1 && !tokens[1].startsWith("--")) {
            result.add(tokens[1]);
            start = 2;
        } else {
            result.add(tokens[0]);
        }
        StringBuilder value = null;
        for (int i = start; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("--")) {
                if (value != null) {
                    result.add(value.toString());
                    value = null;
                }
                result.add(token);
            } else if (value == null) {
                value = new StringBuilder(token);
            } else {
                value.append(' ').append(token);
            }
        }
        if (value != null) {
            result.add(value.toString());
        }
        return result;
    }

    /** The wrapper's IPC settings and its Java agent, which patches the wrapper's own launch. */
    static boolean isWrapperJvmArg(String arg) {
        for (String prefix : WRAPPER_JVM_PREFIXES) {
            if (arg.startsWith(prefix)) {
                return true;
            }
        }
        return arg.startsWith("-javaagent:") && arg.toLowerCase(java.util.Locale.ROOT).endsWith("theseus.jar");
    }

    /** True when this game was started through a launcher's relaunch wrapper. */
    private static boolean startedByWrapper() {
        String raw = System.getProperty("sun.java.command", "").trim();
        return RELAUNCH_MAIN_CLASSES.contains(raw.isEmpty() ? "" : raw.split(" ")[0]);
    }

    private static List<String> filterGameArgs(List<String> args) {
        Map<String, String> kept = new LinkedHashMap<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            boolean hasValue = i + 1 < args.size() && !args.get(i + 1).startsWith("--");
            String value = hasValue ? args.get(++i) : null;
            if (!arg.startsWith("--") || DROPPED_GAME_ARGS.contains(arg)) {
                continue;
            }
            kept.put(arg, value);
        }
        kept.forEach((arg, value) -> {
            out.add(arg);
            if (value != null) {
                out.add(value);
            }
        });
        return out;
    }

    /**
     * The JVM reports module options as {@code jdk.module.*} system properties; turn them back
     * into real flags. Debugger agents are dropped because their port is already in use.
     */
    private static List<String> jvmArguments() {
        boolean wrapped = startedByWrapper();
        List<String> out = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-agentlib:jdwp") || arg.startsWith("-D" + CouchCoop.MOD_ID + ".")) {
                continue;
            }
            if (wrapped && isWrapperJvmArg(arg)) {
                continue;
            }
            int eq = arg.indexOf('=');
            String name = eq > 0 ? arg.substring(0, eq) : arg;
            String value = eq > 0 ? arg.substring(eq + 1) : "";
            if (name.equals("-Djdk.module.path")) {
                out.add("--module-path");
                out.add(value);
            } else if (name.equals("-Djdk.module.upgrade.path")) {
                out.add("--upgrade-module-path");
                out.add(value);
            } else if (name.startsWith("-Djdk.module.addmods")) {
                out.add("--add-modules");
                out.add(value);
            } else if (name.startsWith("-Djdk.module.addexports")) {
                out.add("--add-exports");
                out.add(value);
            } else if (name.startsWith("-Djdk.module.addopens")) {
                out.add("--add-opens");
                out.add(value);
            } else if (name.startsWith("-Djdk.module.addreads")) {
                out.add("--add-reads");
                out.add(value);
            } else if (name.startsWith("-Djdk.module.enable.native.access")) {
                out.add("--enable-native-access=" + value);
            } else if (name.startsWith("-Djdk.module.")) {
                // Other internal module properties (main module, limit-modules) are not relaunchable.
                continue;
            } else {
                out.add(arg);
            }
        }
        return out;
    }

    private static String javaExecutable() {
        String command = ProcessHandle.current().info().command().orElse(null);
        if (command == null) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
            command = Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString();
        }
        return command;
    }

    private static String toArgFile(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            out.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
        }
        return out.toString();
    }
}
