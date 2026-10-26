package dev.couchcoop.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.couchcoop.CouchCoop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Guest profiles in {@code config/couchcoop/profiles.json}. Every window on this machine shares
 * the file, so reads pick up changes made by other windows and writes replace it atomically.
 */
public final class ProfileStore {
    /** What vanilla servers accept as a player name. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final long RECHECK_MS = 2_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private List<Profile> profiles = List.of();
    private FileTime loadedTime;
    private long lastCheck;

    public ProfileStore(Path file) {
        this.file = file;
        reload();
    }

    public Path directory() {
        return file.getParent();
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public synchronized List<Profile> all() {
        refreshIfChanged();
        return profiles;
    }

    public Optional<Profile> find(String name) {
        for (Profile profile : all()) {
            if (profile.name.equalsIgnoreCase(name)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /** Adds or replaces a profile; {@code previousName} is the name it had before an edit, or null. */
    public synchronized void save(Profile profile, String previousName) {
        reload();
        List<Profile> updated = new ArrayList<>();
        for (Profile existing : profiles) {
            if (!matches(existing, profile.name) && !matches(existing, previousName)) {
                updated.add(existing);
            }
        }
        updated.add(profile);
        updated.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        write(updated);
    }

    public synchronized void delete(String name) {
        reload();
        List<Profile> updated = new ArrayList<>(profiles);
        updated.removeIf(existing -> matches(existing, name));
        write(updated);
    }

    private static boolean matches(Profile profile, String name) {
        return name != null && profile.name.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
    }

    private void refreshIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < RECHECK_MS) {
            return;
        }
        lastCheck = now;
        try {
            FileTime time = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
            if (time != null && !time.equals(loadedTime)) {
                reload();
            }
        } catch (IOException e) {
            // Keep what we have; the next check retries.
        }
    }

    private void reload() {
        if (!Files.exists(file)) {
            profiles = List.of();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Profile> loaded = GSON.fromJson(reader, new TypeToken<List<Profile>>() { }.getType());
            List<Profile> valid = new ArrayList<>();
            if (loaded != null) {
                for (Profile profile : loaded) {
                    if (profile != null && isValidName(profile.name)) {
                        valid.add(profile);
                    } else if (profile != null) {
                        CouchCoop.LOGGER.warn("Skipping profile with invalid name '{}' (3-16 letters, digits or _)", profile.name);
                    }
                }
            }
            profiles = List.copyOf(valid);
            loadedTime = Files.getLastModifiedTime(file);
        } catch (IOException | JsonParseException e) {
            CouchCoop.LOGGER.warn("Could not read profiles from {}", file, e);
        }
    }

    private void write(List<Profile> updated) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(updated, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            profiles = List.copyOf(updated);
            loadedTime = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            CouchCoop.LOGGER.error("Could not save profiles to {}", file, e);
        }
    }
}
