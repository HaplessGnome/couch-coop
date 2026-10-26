package dev.couchcoop.profile;

import dev.couchcoop.CouchCoop;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Creates or edits a guest profile. Typing needs a keyboard, like chat; the controller cursor can
 * still click the buttons. {@code onDone} receives the saved profile, or null when cancelled or deleted.
 */
public final class ProfileScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ERROR = 0xFFFF6060;
    private static final int MUTED = 0xFFA0A0A0;

    private final Profile original;
    private final Set<String> takenNames;
    private final Consumer<Profile> onDone;

    private String name;
    private String skin;
    /** null = automatic, as stored in {@link Profile#slim}. */
    private Boolean slim;
    private String problem;
    private Button saveButton;
    private EditBox nameBox;
    private EditBox skinBox;

    /**
     * @param original   the profile to edit, or null to create one
     * @param takenNames lower-case names already playing, which a profile may not take
     */
    public ProfileScreen(Profile original, Set<String> takenNames, Consumer<Profile> onDone) {
        super(Component.literal(original == null ? "New guest profile" : "Edit " + original.name));
        this.original = original;
        this.takenNames = takenNames;
        this.onDone = onDone;
        this.name = original == null ? "" : original.name;
        this.skin = original == null || original.skin == null ? "" : original.skin;
        this.slim = original == null ? null : original.slim;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int y = height / 2 - 60;

        nameBox = new EditBox(font, left, y, 200, 20, Component.literal("Name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Name: 3-16 letters, digits or _"));
        nameBox.setValue(name);
        nameBox.setResponder(value -> {
            name = value;
            validate();
        });
        addRenderableWidget(nameBox);

        skinBox = new EditBox(font, left, y + 26, 200, 20, Component.literal("Skin"));
        skinBox.setMaxLength(260);
        skinBox.setHint(Component.literal("Skin: Minecraft username or PNG path"));
        skinBox.setValue(skin);
        skinBox.setResponder(value -> skin = value);
        addRenderableWidget(skinBox);

        // A button rather than a checkbox: one press cycles it, easy with a controller.
        addRenderableWidget(Button.builder(armsLabel(), button -> {
                slim = slim == null ? Boolean.FALSE : slim ? null : Boolean.TRUE;
                button.setMessage(armsLabel());
            })
            .bounds(left, y + 52, 200, 20)
            .build());

        saveButton = addRenderableWidget(Button.builder(Component.literal(original == null ? "Create and play" : "Save and play"), button -> save())
            .bounds(left, y + 92, 98, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(left + 102, y + 92, 98, 20)
            .build());
        if (original != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete profile"), button -> delete())
                .bounds(left, y + 116, 200, 20)
                .build());
        }

        setInitialFocus(nameBox);
        validate();
    }

    /**
     * Enter steps through the form: name, then skin, then save. With the controller's on-screen
     * keyboard up, Options sends Enter, so a player never needs the D-pad to reach the next field.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (getFocused() == nameBox) {
                setFocused(skinBox);
                return true;
            }
            if (getFocused() == skinBox) {
                validate();
                if (problem == null) {
                    save();
                } else {
                    setFocused(nameBox);
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private Component armsLabel() {
        String model = slim == null ? "Auto (from the skin)" : slim ? "Slim" : "Wide";
        return Component.literal("Arms: " + model);
    }

    private void validate() {
        if (!ProfileStore.isValidName(name)) {
            problem = "Names need 3-16 letters, digits or _";
        } else if (takenNames.contains(name.toLowerCase(java.util.Locale.ROOT))) {
            problem = name + " is already playing";
        } else if (CouchCoop.profiles().find(name).filter(found -> original == null || !found.name.equalsIgnoreCase(original.name)).isPresent()) {
            problem = "A profile called " + name + " already exists";
        } else {
            problem = null;
        }
        if (saveButton != null) {
            saveButton.active = problem == null;
        }
    }

    private void save() {
        validate();
        if (problem != null) {
            return;
        }
        Profile profile = new Profile(name);
        if (original != null) {
            // Keep the settings this screen doesn't edit.
            profile.lookSensitivity = original.lookSensitivity;
            profile.invertLookY = original.invertLookY;
            profile.bindings = original.bindings;
            profile.lastController = original.lastController;
        }
        profile.skin = skin.isBlank() ? null : skin.trim();
        profile.slim = slim;
        CouchCoop.profiles().save(profile, original == null ? null : original.name);
        finish(profile);
    }

    private void delete() {
        CouchCoop.profiles().delete(original.name);
        finish(null);
    }

    @Override
    public void onClose() {
        finish(null);
    }

    private void finish(Profile result) {
        minecraft.gui.setScreen(null);
        onDone.accept(result);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - 60;
        centered(graphics, title.getString(), top - 34, TEXT);
        centered(graphics, "Each profile keeps its own inventory and position in every world.", top - 20, MUTED);
        centered(graphics, "Enter (Options on a controller) moves to the next field", top + 140, MUTED);
        if (problem != null) {
            centered(graphics, problem, top + 74, ERROR);
        }
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.text(font, text, (width - font.width(text)) / 2, y, color);
    }
}
