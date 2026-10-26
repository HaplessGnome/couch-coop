package dev.couchcoop.session;

import dev.couchcoop.CouchCoop;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Lets the game instances on this machine find each other without a network protocol.
 *
 * Each instance holds an OS file lock on {@code slot-N.lock} for as long as it runs and
 * publishes its state next to it. Controllers are claimed the same way with {@code pad-*.lock}.
 * Locks are released by the OS when a process exits or crashes, so stale state never lingers.
 */
public final class SessionCoordinator {
    public static final int MAX_SLOTS = 4;

    /** {@code accountId} is the signed-in Minecraft account's id for a host window, empty for guests. */
    public record Peer(int slot, long pid, String name, String padKey, boolean inWorld, boolean child, String accountId) {
        public boolean hasPad() {
            return !padKey.isEmpty();
        }
    }

    private final Path dir;
    private int slot = -1;
    private FileChannel slotChannel;
    private FileLock slotLock;
    private String padKey;
    private FileChannel padChannel;
    private FileLock padLock;
    private List<Peer> peers = List.of();

    /**
     * @param gameDir this instance's game directory. Only windows playing from the same game
     *                directory share a session, so two unrelated instances running at once (say,
     *                two launcher profiles) are never tiled together.
     */
    public SessionCoordinator(Path gameDir) {
        this.dir = Path.of(System.getProperty("java.io.tmpdir"), CouchCoop.MOD_ID, sessionName(gameDir));
        try {
            Files.createDirectories(dir);
            claimSlot();
        } catch (IOException e) {
            CouchCoop.LOGGER.error("Couch Co-op session directory {} is unusable", dir, e);
        }
    }

    static String sessionName(Path gameDir) {
        String normalised = gameDir.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/').toLowerCase(Locale.ROOT);
        return "session-" + UUID.nameUUIDFromBytes(normalised.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
    }

    public Path directory() {
        return dir;
    }

    public int slot() {
        return slot;
    }

    /** Every running instance including this one, ordered by slot. */
    public List<Peer> peers() {
        return peers;
    }

    public String padKey() {
        return padKey;
    }

    private void claimSlot() throws IOException {
        for (int i = 0; i < MAX_SLOTS; i++) {
            FileChannel channel = FileChannel.open(dir.resolve("slot-" + i + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = tryLock(channel);
            if (lock != null) {
                slot = i;
                slotChannel = channel;
                slotLock = lock;
                CouchCoop.LOGGER.info("Joined splitscreen session as slot {}", i);
                return;
            }
            channel.close();
        }
        CouchCoop.LOGGER.warn("All {} splitscreen slots are taken; this window will not be tiled", MAX_SLOTS);
    }

    public boolean tryClaimPad(String key) {
        if (key.equals(padKey)) {
            return true;
        }
        releasePad();
        try {
            FileChannel channel = FileChannel.open(padLockFile(key), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = tryLock(channel);
            if (lock == null) {
                channel.close();
                return false;
            }
            padKey = key;
            padChannel = channel;
            padLock = lock;
            return true;
        } catch (IOException e) {
            CouchCoop.LOGGER.warn("Could not claim controller {}", key, e);
            return false;
        }
    }

    public void releasePad() {
        if (padKey == null) {
            return;
        }
        try {
            padLock.release();
            padChannel.close();
        } catch (IOException e) {
            CouchCoop.LOGGER.warn("Could not release controller {}", padKey, e);
        }
        padKey = null;
        padLock = null;
        padChannel = null;
    }

    /** Asks the OS whether anyone holds the pad's lock. Touches the disk, so call sparingly. */
    public boolean isPadFree(String key) {
        if (key.equals(padKey)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(padLockFile(key), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = tryLock(channel);
            if (lock != null) {
                lock.release();
                return true;
            }
        } catch (IOException e) {
            CouchCoop.LOGGER.warn("Could not check controller {}", key, e);
        }
        return false;
    }

    /** Writes this instance's state and re-reads everyone else's. */
    public void sync(String name, boolean inWorld, String accountId) {
        if (slot < 0) {
            peers = List.of();
            return;
        }
        Peer self = new Peer(slot, ProcessHandle.current().pid(), name, padKey == null ? "" : padKey, inWorld, CouchCoop.isChild(), accountId);
        write(self);

        List<Peer> found = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (i == slot) {
                found.add(self);
            } else if (isSlotHeld(i)) {
                Peer peer = read(i);
                if (peer != null) {
                    found.add(peer);
                }
            }
        }
        peers = List.copyOf(found);
    }

    private boolean isSlotHeld(int i) {
        try (FileChannel channel = FileChannel.open(dir.resolve("slot-" + i + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = tryLock(channel);
            if (lock == null) {
                return true;
            }
            lock.release();
            Files.deleteIfExists(statusFile(i));
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private void write(Peer peer) {
        Properties props = new Properties();
        props.setProperty("pid", Long.toString(peer.pid()));
        props.setProperty("name", peer.name());
        props.setProperty("pad", peer.padKey());
        props.setProperty("inWorld", Boolean.toString(peer.inWorld()));
        props.setProperty("child", Boolean.toString(peer.child()));
        props.setProperty("account", peer.accountId());
        Path target = statusFile(peer.slot());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                props.store(writer, null);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Another instance may be reading the file on Windows; the next sync retries.
        }
    }

    private Peer read(int i) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(statusFile(i), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            // Not written yet (instance still starting), or mid-replace.
            return null;
        }
        return new Peer(
            i,
            parseLong(props.getProperty("pid")),
            props.getProperty("name", "?"),
            props.getProperty("pad", ""),
            Boolean.parseBoolean(props.getProperty("inWorld")),
            Boolean.parseBoolean(props.getProperty("child")),
            props.getProperty("account", "")
        );
    }

    private Path statusFile(int i) {
        return dir.resolve("slot-" + i + ".properties");
    }

    private Path padLockFile(String key) {
        return dir.resolve("pad-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".lock");
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (java.nio.channels.OverlappingFileLockException e) {
            return null;
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? -1 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
