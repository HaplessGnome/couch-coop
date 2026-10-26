package dev.couchcoop;

import dev.couchcoop.config.CouchCoopConfig;
import dev.couchcoop.craft.QuickCraft;
import dev.couchcoop.input.ControllerInput;
import dev.couchcoop.input.Gamepad;
import dev.couchcoop.input.GamepadInput;
import dev.couchcoop.input.GamepadManager;
import dev.couchcoop.input.Haptics;
import dev.couchcoop.profile.Profile;
import dev.couchcoop.profile.ProfilePicker;
import dev.couchcoop.profile.ProfileScreen;
import dev.couchcoop.session.InstanceLauncher;
import dev.couchcoop.session.SessionCoordinator;
import dev.couchcoop.session.SessionCoordinator.Peer;
import dev.couchcoop.session.WindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-instance splitscreen state, driven from mixin hooks: {@link #onFrame} once per rendered
 * frame (input) and {@link #onTick} once per client tick (session sync, layout, lifecycle).
 *
 * Joining works like console splitscreen: hold Options on a free controller and a new
 * game window opens for that player, tiles itself and joins the host's world over LAN.
 */
public final class CouchCoopClient {
    private static final long JOIN_HOLD_MS = 1000;
    private static final long SPAWN_TIMEOUT_MS = 120_000;
    private static final long WANTED_PAD_TIMEOUT_MS = 30_000;
    private static final long PAD_GRACE_MS = 30_000;
    private static final long CHILD_EXIT_DELAY_MS = 5_000;
    private static final int SYNC_INTERVAL_TICKS = 20;

    private static CouchCoopClient instance;

    private final GamepadManager gamepads = new GamepadManager();
    private final ControllerInput input = new ControllerInput();
    private final SessionCoordinator session = new SessionCoordinator();
    private final WindowLayout layout = new WindowLayout();
    private final QuickCraft quickCraft = new QuickCraft();
    private final Haptics haptics = new Haptics();
    private final long startedAt = System.currentTimeMillis();

    private Gamepad pad;
    private boolean hadPad;
    private long padMissingSince;
    private String wantedPadKey = System.getProperty(CouchCoop.PROP_PAD);
    private int shownPlayerIndex = -1;

    private final Map<String, Long> startHeldSince = new HashMap<>();
    private final Set<String> startHandled = new HashSet<>();
    private final Map<String, Long> pendingSpawns = new HashMap<>();
    private final Map<String, String> pendingNames = new HashMap<>();
    private final Set<String> knownFreePads = new HashSet<>();
    private ProfilePicker picker;
    private boolean hintPlayStation;

    private long lastFrameNanos = System.nanoTime();
    private int ticks;
    private boolean childAudioApplied;
    private boolean wasInWorld;
    private long leftWorldAt;

    private String message;
    private long messageUntil;
    private long hintUntil;

    private CouchCoopClient() {
    }

    /** Null until a loader has called {@link CouchCoop#init}. */
    public static CouchCoopClient get() {
        if (instance == null && CouchCoop.isInitialised()) {
            instance = new CouchCoopClient();
        }
        return instance;
    }

    public static boolean isControllerDriven() {
        return instance != null && instance.pad != null;
    }

    public static boolean isCursorActive() {
        return isControllerDriven() && instance.input.cursor().isActive() && Minecraft.getInstance().gui.screen() != null;
    }

    public ControllerInput input() {
        return input;
    }

    public QuickCraft quickCraft() {
        return quickCraft;
    }

    /** This window's controller, or null when it is played on keyboard and mouse. */
    public Gamepad pad() {
        return pad;
    }

    /** The on-screen keyboard is up in this window's open screen. */
    public static boolean isKeyboardActive() {
        return isControllerDriven() && instance.input.keyboard().isVisible() && Minecraft.getInstance().gui.screen() != null;
    }

    public Vec2 movement() {
        return pad == null ? Vec2.ZERO : input.movement();
    }

    /** Several windows share one focus, so an unfocused window must keep playing. */
    public boolean keepRunningWhenUnfocused() {
        return pad != null || session.peers().size() > 1;
    }

    // ---------------------------------------------------------------- frame

    public void onFrame(Minecraft minecraft) {
        long now = System.nanoTime();
        double delta = Math.min((now - lastFrameNanos) / 1.0e9, 0.1);
        lastFrameNanos = now;

        gamepads.update();
        resolvePad();
        updatePicker(minecraft);
        handleJoinGestures(minecraft);
        if (pad != null) {
            input.frame(minecraft, pad, delta);
        }
        // Typing in the recipe search box uses the same shoulder buttons, so the keyboard wins.
        quickCraft.frame(minecraft, pad != null && !input.keyboard().isVisible() ? pad : null);
    }

    private void resolvePad() {
        String key = session.padKey();
        if (key == null) {
            if (pad != null) {
                input.reset();
                pad = null;
            }
            claimWantedPad();
            return;
        }

        Gamepad found = gamepads.find(key);
        if (found != null) {
            if (pad != found) {
                pad = found;
                shownPlayerIndex = -1;
            }
            padMissingSince = 0;
            return;
        }

        // Pad unplugged or out of battery: keep the claim for a while in case it comes back.
        if (pad != null) {
            input.reset();
            pad = null;
            padMissingSince = System.currentTimeMillis();
            showMessage("Controller disconnected");
        } else if (padMissingSince > 0 && System.currentTimeMillis() - padMissingSince > PAD_GRACE_MS) {
            session.releasePad();
            padMissingSince = 0;
        }
    }

    /** A freshly launched guest claims the controller whose START press spawned it. */
    private void claimWantedPad() {
        if (wantedPadKey == null) {
            return;
        }
        if (System.currentTimeMillis() - startedAt > WANTED_PAD_TIMEOUT_MS) {
            CouchCoop.LOGGER.warn("Controller {} never appeared; press A on any free controller to play", wantedPadKey);
            wantedPadKey = null;
        } else if (gamepads.find(wantedPadKey) != null && session.tryClaimPad(wantedPadKey)) {
            onPadClaimed();
            wantedPadKey = null;
        }
    }

    private void handleJoinGestures(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        for (Gamepad candidate : gamepads.pads()) {
            String key = candidate.key();
            if (key.equals(session.padKey()) || (picker != null && picker.pad() == candidate)) {
                continue;
            }

            if (candidate.pressed(GamepadInput.CROSS) && session.padKey() == null && mayClaimWithA() && session.tryClaimPad(key)) {
                onPadClaimed();
                return;
            }

            if (candidate.isDown(GamepadInput.OPTIONS)) {
                long since = startHeldSince.computeIfAbsent(key, k -> now);
                if (now - since >= JOIN_HOLD_MS && startHandled.add(key)) {
                    onJoinRequested(minecraft, candidate);
                }
            } else {
                startHeldSince.remove(key);
                startHandled.remove(key);
            }
        }
    }

    /**
     * Pressing A picks up a free controller for a guest window that has none, for a lone
     * player, or for a player whose controller dropped out. The host's keyboard player is
     * otherwise left alone so a joining friend's A press doesn't get captured by window 1.
     */
    private boolean mayClaimWithA() {
        return CouchCoop.isChild() || hadPad || session.peers().size() <= 1;
    }

    private void onPadClaimed() {
        hadPad = true;
        pad = gamepads.find(session.padKey());
        shownPlayerIndex = -1;
        CouchCoop.LOGGER.info("Slot {} claimed controller {}", session.slot(), session.padKey());
        // A keyboard player switching to a controller must let go of the shared cursor.
        Minecraft.getInstance().mouseHandler.releaseMouse();
        if (pad != null) {
            pad.rumble(0.35f, 150);
            showMessage(pad.name() + " connected");
        }
    }

    private void onJoinRequested(Minecraft minecraft, Gamepad candidate) {
        String key = candidate.key();
        if (!session.isPadFree(key)) {
            return;
        }
        if (CouchCoop.isChild() && session.padKey() == null) {
            if (session.tryClaimPad(key)) {
                onPadClaimed();
            }
            return;
        }
        if (!isSpawner(minecraft) || guestWaitingForPad()) {
            return;
        }

        Long pending = pendingSpawns.get(key);
        if (pending != null && System.currentTimeMillis() - pending < SPAWN_TIMEOUT_MS) {
            showMessage(pendingNames.get(key) + " is still starting...");
            return;
        }
        CouchCoopConfig config = CouchCoop.config();
        if (session.peers().size() + activePendingSpawns() >= Math.min(config.maxPlayers, SessionCoordinator.MAX_SLOTS)) {
            showMessage("Couch Co-op is full (" + config.maxPlayers + " players)");
            return;
        }

        if (minecraft.getSingleplayerServer() == null && minecraft.getCurrentServer() == null) {
            showMessage("Load a world first, then hold " + candidate.label(GamepadInput.OPTIONS) + " to join");
            return;
        }
        if (picker == null) {
            picker = new ProfilePicker(candidate, pickerEntries(candidate, config));
            candidate.rumble(0.3f, 100);
        }
    }

    // ---------------------------------------------------------------- profiles

    public ProfilePicker picker() {
        return picker;
    }

    /** Free profiles first (the one this controller last used on top), then "new" and "guest". */
    private List<ProfilePicker.Entry> pickerEntries(Gamepad candidate, CouchCoopConfig config) {
        Set<String> taken = takenNames();
        List<ProfilePicker.Entry> entries = new ArrayList<>();
        for (Profile profile : CouchCoop.profiles().all()) {
            if (!taken.contains(profile.name.toLowerCase(Locale.ROOT))) {
                ProfilePicker.Entry entry = new ProfilePicker.Entry(ProfilePicker.Kind.PROFILE, profile, profile.name);
                if (candidate.key().equals(profile.lastController)) {
                    entries.addFirst(entry);
                } else {
                    entries.add(entry);
                }
            }
        }
        entries.add(new ProfilePicker.Entry(ProfilePicker.Kind.NEW, null, "+ New profile"));
        entries.add(new ProfilePicker.Entry(ProfilePicker.Kind.GUEST, null, "Play as guest (" + nextPlayerName(config) + ")"));
        return entries;
    }

    /** Lower-case names of everyone playing or about to, which a new guest must not reuse. */
    private Set<String> takenNames() {
        Set<String> taken = new HashSet<>();
        for (String name : pendingNames.values()) {
            taken.add(name.toLowerCase(Locale.ROOT));
        }
        for (Peer peer : session.peers()) {
            taken.add(peer.name().toLowerCase(Locale.ROOT));
        }
        taken.add(Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT));
        return taken;
    }

    private void updatePicker(Minecraft minecraft) {
        if (picker == null) {
            return;
        }
        Gamepad candidate = picker.pad();
        if (gamepads.find(candidate.key()) != candidate || minecraft.level == null) {
            picker = null;
            return;
        }
        ProfilePicker.Entry entry = picker.selected();
        switch (picker.update()) {
            case CANCEL -> picker = null;
            case CHOOSE -> {
                picker = null;
                switch (entry.kind()) {
                    case PROFILE -> launchGuest(minecraft, candidate, entry.profile());
                    case GUEST -> launchGuest(minecraft, candidate, null);
                    case NEW -> editProfile(minecraft, candidate, null);
                }
            }
            case EDIT -> {
                picker = null;
                editProfile(minecraft, candidate, entry.profile());
            }
            case NONE -> {
            }
        }
    }

    /** Opens the profile form in this window; saving launches the guest, cancelling reopens the list. */
    private void editProfile(Minecraft minecraft, Gamepad candidate, Profile profile) {
        Set<String> taken = takenNames();
        if (profile != null) {
            taken.remove(profile.name.toLowerCase(Locale.ROOT));
        }
        minecraft.gui.setScreen(new ProfileScreen(profile, taken, saved -> {
            if (saved != null) {
                launchGuest(minecraft, candidate, saved);
            } else if (gamepads.find(candidate.key()) == candidate && session.isPadFree(candidate.key())) {
                picker = new ProfilePicker(candidate, pickerEntries(candidate, CouchCoop.config()));
            }
        }));
    }

    private void launchGuest(Minecraft minecraft, Gamepad candidate, Profile profile) {
        String key = candidate.key();
        String address = shareWorld(minecraft);
        if (address == null) {
            showMessage("Could not open this world to LAN");
            return;
        }
        String name = profile == null ? nextPlayerName(CouchCoop.config()) : profile.name;
        if (takenNames().contains(name.toLowerCase(Locale.ROOT))) {
            showMessage(name + " is already playing");
            return;
        }

        InstanceLauncher.Result result = InstanceLauncher.launch(session.directory(), name, key, address, profile == null ? null : profile.name);
        if (result.started()) {
            pendingSpawns.put(key, System.currentTimeMillis());
            pendingNames.put(key, name);
            candidate.rumble(0.5f, 250);
            showMessage("Starting a window for " + name + "...");
            if (profile != null && !key.equals(profile.lastController)) {
                profile.lastController = key;
                CouchCoop.profiles().save(profile, profile.name);
            }
        } else {
            showMessage(result.error());
        }
    }

    /** Exactly one instance launches guests: the lowest-slot non-guest window that is in a world. */
    private boolean isSpawner(Minecraft minecraft) {
        if (CouchCoop.isChild() || minecraft.level == null) {
            return false;
        }
        for (Peer peer : session.peers()) {
            if (!peer.child() && peer.inWorld()) {
                return peer.slot() == session.slot();
            }
        }
        return true;
    }

    /** A manually opened guest window without a controller takes the pad instead of a new window. */
    private boolean guestWaitingForPad() {
        for (Peer peer : session.peers()) {
            if (peer.child() && !peer.hasPad() && peer.slot() != session.slot()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens the singleplayer world to LAN if needed and returns an address guests can use.
     * Guests run offline profiles, so the integrated server must not authenticate them.
     */
    private static String shareWorld(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            if (!server.isPublished()) {
                int port = HttpUtil.getAvailablePort();
                if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, port)) {
                    return null;
                }
            }
            server.setUsesAuthentication(false);
            return "127.0.0.1:" + server.getPort();
        }
        ServerData current = minecraft.getCurrentServer();
        return current == null ? null : current.ip;
    }

    private String nextPlayerName(CouchCoopConfig config) {
        Set<String> taken = new HashSet<>(pendingNames.values());
        for (Peer peer : session.peers()) {
            taken.add(peer.name());
        }
        for (int number = 2; ; number++) {
            String name = config.playerNamePrefix + number;
            if (!taken.contains(name)) {
                return name;
            }
        }
    }

    private int activePendingSpawns() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Map.Entry<String, Long> entry : pendingSpawns.entrySet()) {
            if (now - entry.getValue() < SPAWN_TIMEOUT_MS && !isPeerName(pendingNames.get(entry.getKey()))) {
                count++;
            }
        }
        return count;
    }

    private boolean isPeerName(String name) {
        for (Peer peer : session.peers()) {
            if (peer.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tick

    public void onTick(Minecraft minecraft) {
        if (ticks++ % SYNC_INTERVAL_TICKS == 0) {
            sync(minecraft);
        }
        if (CouchCoop.isChild()) {
            childLifecycle(minecraft);
        }
        if (pad != null) {
            haptics.tick(minecraft, pad);
        }
    }

    public void onBlockBroken() {
        if (pad != null) {
            haptics.blockBroken(pad);
        }
    }

    /** The game is closing: free the triggers so the controller isn't left stiff. */
    public void onShutdown() {
        if (pad != null) {
            haptics.reset(pad);
        }
    }

    private void sync(Minecraft minecraft) {
        session.sync(minecraft.getUser().getName(), minecraft.level != null);
        List<Peer> peers = session.peers();
        int index = -1;
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).slot() == session.slot()) {
                index = i;
            }
        }
        CouchCoopConfig config = CouchCoop.config();
        layout.apply(minecraft.getWindow(), index, peers.size(), config.layout, config.coverTaskbar);

        if (pad != null && index != shownPlayerIndex && CouchCoop.config().controllerLights) {
            pad.showPlayer(Math.max(index, 0));
            shownPlayerIndex = index;
        }

        pendingSpawns.entrySet().removeIf(entry -> {
            String name = pendingNames.get(entry.getKey());
            boolean done = isPeerName(name) || System.currentTimeMillis() - entry.getValue() > SPAWN_TIMEOUT_MS;
            if (done) {
                pendingNames.remove(entry.getKey());
            }
            return done;
        });

        updateJoinHint(peers);
    }

    /** Shows the join hint for a while whenever a controller nobody owns shows up. */
    private void updateJoinHint(List<Peer> peers) {
        Set<String> owned = new HashSet<>();
        for (Peer peer : peers) {
            if (peer.hasPad()) {
                owned.add(peer.padKey());
            }
        }
        Set<String> free = new HashSet<>();
        for (Gamepad candidate : gamepads.pads()) {
            if (!owned.contains(candidate.key())) {
                if (free.isEmpty()) {
                    hintPlayStation = candidate.isPlayStation();
                }
                free.add(candidate.key());
            }
        }
        if (!knownFreePads.containsAll(free)) {
            hintUntil = System.currentTimeMillis() + 10_000;
        }
        knownFreePads.clear();
        knownFreePads.addAll(free);
    }

    private void childLifecycle(Minecraft minecraft) {
        if (!childAudioApplied && !CouchCoop.config().childInstanceAudio) {
            // Safe: guests never save options.txt (see OptionsMixin), so the host's volume is untouched.
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            childAudioApplied = true;
        }
        if (minecraft.level != null) {
            wasInWorld = true;
            leftWorldAt = 0;
        } else if (wasInWorld) {
            // A guest window has nothing to do once its player leaves the host's world.
            if (leftWorldAt == 0) {
                leftWorldAt = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - leftWorldAt > CHILD_EXIT_DELAY_MS) {
                CouchCoop.LOGGER.info("Guest left the world; closing this window");
                minecraft.stop();
            }
        }
    }

    // ---------------------------------------------------------------- overlay state

    public void showMessage(String text) {
        CouchCoop.LOGGER.info("Overlay: {}", text);
        message = text;
        messageUntil = System.currentTimeMillis() + 4_000;
    }

    /** Transient status line, or null. */
    public String message() {
        return System.currentTimeMillis() < messageUntil ? message : null;
    }

    /** Join instructions shown when a free controller appears, or null. */
    public String hint(Minecraft minecraft) {
        if (System.currentTimeMillis() > hintUntil || knownFreePads.isEmpty()) {
            return null;
        }
        String press = GamepadInput.CROSS.label(hintPlayStation);
        String hold = GamepadInput.OPTIONS.label(hintPlayStation);
        if (session.padKey() == null && mayClaimWithA()) {
            return CouchCoop.isChild()
                ? "Press " + press + " on a controller to play"
                : "Press " + press + " to use a controller - hold " + hold + " to add a player";
        }
        if (isSpawner(minecraft)) {
            return "Hold " + hold + " on a controller to add a player";
        }
        return null;
    }
}
