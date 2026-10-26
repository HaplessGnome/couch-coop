package dev.couchcoop.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.couchcoop.CouchCoop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins for guest profiles. Guests play offline, so no server hands out their skin. Every window
 * on this machine reads the same profile file, though, so each one resolves the skin locally and
 * everyone sees it. A skin is either a PNG on disk or a real account's skin, looked up by username
 * through Mojang's public API and cached so it still shows offline.
 */
public final class GuestSkins {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static SkinTextureDownloader downloader;

    /** One player's skin: the settings it was built from and, once loaded, the result. */
    private static final class Entry {
        final String spec;
        volatile PlayerSkin skin;

        Entry(String spec) {
            this.spec = spec;
        }
    }

    private record Source(Path file, String url, boolean slim) {
    }

    private GuestSkins() {
    }

    /** The skin to show for this player, or null to keep whatever vanilla would show. */
    public static PlayerSkin get(String playerName) {
        if (CouchCoop.profiles() == null || playerName == null) {
            return null;
        }
        Profile profile = CouchCoop.profiles().find(playerName).orElse(null);
        if (profile == null || !profile.hasSkin()) {
            return null;
        }
        String key = playerName.toLowerCase(Locale.ROOT);
        String spec = profile.skin.trim() + "|" + profile.isSlim();
        Entry entry = ENTRIES.get(key);
        if (entry == null || !entry.spec.equals(spec)) {
            entry = new Entry(spec);
            ENTRIES.put(key, entry);
            load(key, profile, entry);
        }
        return entry.skin;
    }

    private static void load(String key, Profile profile, Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (downloader == null) {
            downloader = new SkinTextureDownloader(minecraft.getProxy(), minecraft.getTextureManager(), minecraft);
        }
        Path skins = CouchCoop.profiles().directory().resolve("skins");
        String skin = profile.skin.trim();
        // A new id per load, so an edited skin never shows a stale texture.
        Identifier id = Identifier.fromNamespaceAndPath(CouchCoop.MOD_ID, "skins/" + key + "_" + Integer.toHexString(entry.spec.hashCode()));

        CompletableFuture
            .supplyAsync(() -> resolve(skins, skin, profile.isSlim()))
            .thenComposeAsync(source -> downloader.downloadAndRegisterSkin(id, source.file(), source.url(), true)
                .thenApply(texture -> PlayerSkin.insecure(texture, null, null, source.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE)), minecraft)
            .whenComplete((result, error) -> {
                if (error != null) {
                    CouchCoop.LOGGER.warn("Could not load skin '{}' for {}: {}", skin, profile.name, rootMessage(error));
                } else {
                    entry.skin = result;
                    CouchCoop.LOGGER.info("Loaded skin '{}' for {}", skin, profile.name);
                }
            });
    }

    /** Runs off the main thread: a PNG path is used as-is, a username is looked up online. */
    private static Source resolve(Path skins, String skin, boolean slim) {
        if (looksLikeFile(skin)) {
            Path file = Path.of(skin);
            if (!file.isAbsolute()) {
                file = skins.resolve(skin);
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("no PNG at " + file);
            }
            // The downloader reads its cache file when it exists, so the URL is never fetched.
            return new Source(file, file.toUri().toString(), slim);
        }
        if (!ProfileStore.isValidName(skin)) {
            throw new IllegalArgumentException("not a PNG path or a Minecraft username");
        }

        Path cache = skins.resolve("cache");
        Path modelFile = cache.resolve(skin.toLowerCase(Locale.ROOT) + ".model");
        try {
            JsonObject account = getJson("https://api.mojang.com/users/profiles/minecraft/" + skin);
            String uuid = account.get("id").getAsString();
            JsonObject session = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            String encoded = session.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                throw new IllegalArgumentException(skin + " uses a default skin");
            }
            JsonObject skinInfo = textures.getAsJsonObject("SKIN");
            String url = skinInfo.get("url").getAsString();
            boolean accountSlim = skinInfo.has("metadata") && "slim".equals(skinInfo.getAsJsonObject("metadata").get("model").getAsString());
            // Texture URLs end in a content hash, so a changed skin gets a new cache file.
            Path file = cache.resolve(url.substring(url.lastIndexOf('/') + 1) + ".png");
            Files.createDirectories(cache);
            Files.writeString(modelFile, file.getFileName() + "\n" + accountSlim, StandardCharsets.UTF_8);
            return new Source(file, url, accountSlim);
        } catch (IOException | InterruptedException | RuntimeException e) {
            // Offline or rate limited: fall back to the last skin we saw for this username.
            try {
                String[] cached = Files.readString(modelFile, StandardCharsets.UTF_8).split("\n");
                Path file = cache.resolve(cached[0]);
                if (Files.isRegularFile(file)) {
                    return new Source(file, file.toUri().toString(), Boolean.parseBoolean(cached[1].trim()));
                }
            } catch (IOException | RuntimeException ignored) {
                // No cached copy either.
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("could not reach Mojang: " + e.getMessage(), e);
        }
    }

    private static boolean looksLikeFile(String skin) {
        return skin.toLowerCase(Locale.ROOT).endsWith(".png") || skin.contains("/") || skin.contains("\\");
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            throw new IllegalArgumentException("no Minecraft account is called that");
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
