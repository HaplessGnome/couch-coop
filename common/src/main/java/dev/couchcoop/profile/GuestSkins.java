package dev.couchcoop.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.couchcoop.CouchCoop;
import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
 * Skins the server can't provide. Guests play offline, and the shared world runs without account
 * checks, so no skins arrive from the server: neither guests' nor the host's. Every window on this
 * machine resolves them locally instead. Guest profiles name a PNG on disk or a real account's
 * username; a host window's real account is looked up by its id. Downloads go through Mojang's
 * public API and are cached so they still show offline.
 */
public final class GuestSkins {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final String ACCOUNT = "account:";
    /** A failed download (Mojang timing out, say) is tried again after this long. */
    private static final long RETRY_MS = 30_000;
    private static SkinTextureDownloader downloader;

    /** One player's skin: the settings it was built from and, once loaded, the result. */
    private static final class Entry {
        final String spec;
        volatile PlayerSkin skin;
        volatile long failedAt;

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
        String skin;
        Boolean slim = null;
        Profile profile = CouchCoop.profiles().find(playerName).orElse(null);
        if (profile != null && profile.hasSkin()) {
            skin = profile.skin.trim();
            slim = profile.slim;
        } else {
            CouchCoopClient client = CouchCoopClient.get();
            String accountId = client == null ? null : client.hostAccountId(playerName);
            if (accountId == null) {
                return null;
            }
            // Looked up by id rather than name, so it can never pick up a stranger's skin.
            skin = ACCOUNT + accountId;
        }

        String key = playerName.toLowerCase(Locale.ROOT);
        String spec = skin + "|" + slim;
        Entry entry = ENTRIES.get(key);
        if (entry == null || !entry.spec.equals(spec)
            || (entry.skin == null && entry.failedAt > 0 && System.currentTimeMillis() - entry.failedAt > RETRY_MS)) {
            entry = new Entry(spec);
            ENTRIES.put(key, entry);
            load(key, playerName, skin, slim, entry);
        }
        return entry.skin;
    }

    private static void load(String key, String playerName, String skin, Boolean slim, Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (downloader == null) {
            downloader = new SkinTextureDownloader(minecraft.getProxy(), minecraft.getTextureManager(), minecraft);
        }
        Path skins = CouchCoop.profiles().directory().resolve("skins");
        // A new id per load, so an edited skin never shows a stale texture.
        Identifier id = Identifier.fromNamespaceAndPath(CouchCoop.MOD_ID, "skins/" + key + "_" + Integer.toHexString(entry.spec.hashCode()));

        CompletableFuture
            .supplyAsync(() -> resolve(skins, skin, slim))
            .thenComposeAsync(source -> downloader.downloadAndRegisterSkin(id, source.file(), source.url(), true)
                .thenApply(texture -> PlayerSkin.insecure(texture, null, null, source.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE)), minecraft)
            .whenComplete((result, error) -> {
                if (error != null) {
                    entry.failedAt = System.currentTimeMillis();
                    CouchCoop.LOGGER.warn("Could not load skin '{}' for {}, retrying in {}s: {}", skin, playerName, RETRY_MS / 1000, rootMessage(error));
                } else {
                    entry.skin = result;
                    CouchCoop.LOGGER.info("Loaded skin '{}' for {}", skin, playerName);
                }
            });
    }

    /**
     * Runs off the main thread. A PNG path is used as-is; a username or account id is looked up
     * online. {@code slim} null means automatic: from the PNG's pixels, or the account's model.
     */
    private static Source resolve(Path skins, String skin, Boolean slim) {
        if (looksLikeFile(skin)) {
            Path file = Path.of(skin);
            if (!file.isAbsolute()) {
                file = skins.resolve(skin);
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("no PNG at " + file);
            }
            // The downloader reads its cache file when it exists, so the URL is never fetched.
            return new Source(file, file.toUri().toString(), slim != null ? slim : detectSlim(file));
        }

        String accountId;
        String cacheName;
        if (skin.startsWith(ACCOUNT)) {
            accountId = skin.substring(ACCOUNT.length());
            cacheName = "account-" + accountId;
        } else if (ProfileStore.isValidName(skin)) {
            accountId = null;
            cacheName = skin.toLowerCase(Locale.ROOT);
        } else {
            throw new IllegalArgumentException("not a PNG path or a Minecraft username");
        }

        Path cache = skins.resolve("cache");
        Path modelFile = cache.resolve(cacheName + ".model");
        try {
            if (accountId == null) {
                accountId = getJson("https://api.mojang.com/users/profiles/minecraft/" + skin).get("id").getAsString();
            }
            JsonObject session = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + accountId);
            String encoded = session.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                throw new IllegalArgumentException("that account uses a default skin");
            }
            JsonObject skinInfo = textures.getAsJsonObject("SKIN");
            String url = skinInfo.get("url").getAsString();
            boolean accountSlim = skinInfo.has("metadata") && "slim".equals(skinInfo.getAsJsonObject("metadata").get("model").getAsString());
            // Texture URLs end in a content hash, so a changed skin gets a new cache file.
            Path file = cache.resolve(url.substring(url.lastIndexOf('/') + 1) + ".png");
            Files.createDirectories(cache);
            Files.writeString(modelFile, file.getFileName() + System.lineSeparator() + accountSlim, StandardCharsets.UTF_8);
            return new Source(file, url, slim != null ? slim : accountSlim);
        } catch (IOException | InterruptedException | RuntimeException e) {
            // Offline or rate limited: fall back to the last skin we saw for this account.
            try {
                java.util.List<String> cached = Files.readAllLines(modelFile, StandardCharsets.UTF_8);
                Path file = cache.resolve(cached.get(0).trim());
                if (Files.isRegularFile(file)) {
                    return new Source(file, file.toUri().toString(), slim != null ? slim : Boolean.parseBoolean(cached.get(1).trim()));
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

    private static boolean detectSlim(Path file) {
        try {
            return isSlim(ImageIO.read(file.toFile()));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Slim arms are 3 pixels wide, leaving the outer column of each arm unused: in a 64x64 skin
     * the pixel at (54, 20) is transparent for slim arms and painted for wide ones. Old 64x32
     * skins only had wide arms.
     */
    static boolean isSlim(BufferedImage image) {
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
            return false;
        }
        return (image.getRGB(54, 20) >>> 24) == 0;
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
