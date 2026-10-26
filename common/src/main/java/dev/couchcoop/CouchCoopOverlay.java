package dev.couchcoop;

import dev.couchcoop.craft.QuickCraft;
import dev.couchcoop.input.Gamepad;
import dev.couchcoop.input.GamepadInput;
import dev.couchcoop.input.KeyboardLayout;
import dev.couchcoop.input.OnScreenKeyboard;
import dev.couchcoop.input.VirtualCursor;
import dev.couchcoop.profile.ProfilePicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Draws on top of everything else in the GUI: status messages, the join hint, the profile
 * picker, the on-screen keyboard and the controller's menu cursor.
 */
public final class CouchCoopOverlay {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int BACKDROP = 0xA0000000;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int PANEL = 0xD0101018;
    private static final int HIGHLIGHT = 0xFF3050A0;
    private static final int KEY = 0xFF303040;
    private static final int CURSOR_FILL = 0xFFFFFFFF;
    private static final int CURSOR_OUTLINE = 0xFF000000;

    private CouchCoopOverlay() {
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        int y = 4;
        String message = client.message();
        if (message != null) {
            banner(graphics, font, message, y);
            y += font.lineHeight + 6;
        }
        String hint = client.hint(minecraft);
        if (hint != null && client.picker() == null) {
            banner(graphics, font, hint, y);
        }
        if (client.picker() != null) {
            picker(graphics, font, client.picker());
        }

        if (client.quickCraft().isActive()) {
            quickCraft(graphics, font, client.quickCraft(), minecraft, client.pad());
        }
        if (CouchCoopClient.isKeyboardActive()) {
            keyboard(graphics, font, client.input().keyboard(), client.pad());
        } else if (CouchCoopClient.isCursorActive()) {
            VirtualCursor cursor = client.input().cursor();
            cursor(graphics, (int) cursor.x(), (int) cursor.y());
        }
    }

    private static void banner(GuiGraphicsExtractor graphics, Font font, String text, int y) {
        int width = font.width(text);
        int x = (graphics.guiWidth() - width) / 2;
        graphics.fill(x - 4, y - 2, x + width + 4, y + font.lineHeight + 2, BACKDROP);
        graphics.text(font, text, x, y, TEXT);
    }

    /** The joining player's profile list, centred, with the highlighted row marked. */
    private static void picker(GuiGraphicsExtractor graphics, Font font, ProfilePicker picker) {
        String title = "Who's playing?";
        String help = picker.help();
        List<ProfilePicker.Entry> entries = picker.entries();
        int row = font.lineHeight + 4;
        int width = Math.max(font.width(title), font.width(help));
        for (ProfilePicker.Entry entry : entries) {
            width = Math.max(width, font.width(entry.label()) + 16);
        }
        width += 16;
        int height = row * (entries.size() + 2) + 12;
        int left = (graphics.guiWidth() - width) / 2;
        int top = (graphics.guiHeight() - height) / 2;
        graphics.fill(left, top, left + width, top + height, PANEL);

        int y = top + 6;
        graphics.text(font, title, (graphics.guiWidth() - font.width(title)) / 2, y, TEXT);
        y += row + 2;
        for (int i = 0; i < entries.size(); i++) {
            boolean selected = i == picker.selectedIndex();
            if (selected) {
                graphics.fill(left + 4, y - 2, left + width - 4, y + font.lineHeight + 1, HIGHLIGHT);
            }
            graphics.text(font, entries.get(i).label(), left + 12, y, selected ? TEXT : MUTED);
            y += row;
        }
        graphics.text(font, help, (graphics.guiWidth() - font.width(help)) / 2, y + 2, MUTED);
    }

    /** The quick craft list: result icons and names, the selected row highlighted for controllers. */
    private static void quickCraft(GuiGraphicsExtractor graphics, Font font, QuickCraft panel, Minecraft minecraft, Gamepad pad) {
        QuickCraft.Layout layout = panel.layout();
        List<QuickCraft.Entry> entries = panel.entries();
        int shown = Math.min(layout.rows(), entries.size());
        int bottom = layout.top() + layout.firstRow() + Math.max(shown, 1) * layout.rowHeight() + 2;
        graphics.fill(layout.left(), layout.top(), layout.left() + layout.width(), bottom, PANEL);
        String title = pad == null ? "Quick craft" : "Quick craft  " + pad.label(GamepadInput.L1) + "/" + pad.label(GamepadInput.R1);
        graphics.text(font, title, layout.left() + 4, layout.top() + 2, MUTED);
        if (entries.isEmpty()) {
            graphics.text(font, "Nothing to craft", layout.left() + 4, layout.top() + layout.firstRow() + 4, MUTED);
            return;
        }

        double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        int hovered = panel.rowAt(mouseX, mouseY);
        int scroll = panel.scroll();
        for (int i = 0; i < shown; i++) {
            int index = scroll + i;
            QuickCraft.Entry entry = entries.get(index);
            int y = layout.top() + layout.firstRow() + i * layout.rowHeight();
            if ((pad != null && index == panel.selected()) || index == hovered) {
                graphics.fill(layout.left() + 1, y, layout.left() + layout.width() - 1, y + layout.rowHeight(), HIGHLIGHT);
            }
            graphics.item(entry.result(), layout.left() + 2, y + 1);
            graphics.itemDecorations(font, entry.result(), layout.left() + 2, y + 1);
            String name = font.plainSubstrByWidth(entry.result().getHoverName().getString(), layout.width() - 24);
            graphics.text(font, name, layout.left() + 21, y + 5, TEXT);
        }
        if (pad != null) {
            String help = pad.label(GamepadInput.R2) + " craft  " + pad.label(GamepadInput.L2) + " all";
            graphics.text(font, help, layout.left() + 4, bottom + 2, MUTED);
        }
    }

    /** The on-screen keyboard, at the bottom of the screen or the top if the text box is low down. */
    private static void keyboard(GuiGraphicsExtractor graphics, Font font, OnScreenKeyboard keyboard, Gamepad pad) {
        List<List<KeyboardLayout.Key>> rows = keyboard.rows();
        String[] help = keyboard.help(pad);
        int unit = Math.min(26, (graphics.guiWidth() - 16) / KeyboardLayout.ROW_UNITS);
        int keyHeight = font.lineHeight + 5;
        int width = unit * KeyboardLayout.ROW_UNITS;
        int height = rows.size() * (keyHeight + 2) + help.length * (font.lineHeight + 1) + 8;
        int left = (graphics.guiWidth() - width) / 2;
        int top = keyboard.atTop() ? 4 : graphics.guiHeight() - height - 4;
        graphics.fill(left - 4, top - 4, left + width + 4, top + height, PANEL);

        int y = top;
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                KeyboardLayout.Key key = rows.get(r).get(c);
                int x = left + key.start() * unit;
                boolean selected = keyboard.cursor().row() == r && keyboard.cursor().column() == c;
                boolean lit = selected || (key.kind() == KeyboardLayout.Kind.SHIFT && keyboard.shift());
                graphics.fill(x + 1, y, x + key.width() * unit - 1, y + keyHeight, selected ? HIGHLIGHT : lit ? 0xFF305030 : KEY);
                String label = key.kind() == KeyboardLayout.Kind.CHAR && keyboard.shift() ? key.shifted() : key.label();
                graphics.text(font, label, x + (key.width() * unit - font.width(label)) / 2, y + 3, TEXT);
            }
            y += keyHeight + 2;
        }
        y += 2;
        for (String line : help) {
            graphics.text(font, line, (graphics.guiWidth() - font.width(line)) / 2, y, MUTED);
            y += font.lineHeight + 1;
        }
    }

    /** A small arrow pointer, outline first so it reads on any background. */
    private static void cursor(GuiGraphicsExtractor graphics, int x, int y) {
        for (int row = 0; row < 10; row++) {
            int span = row < 7 ? row + 1 : 9 - row;
            graphics.fill(x - 1, y + row - 1, x + span + 1, y + row + 2, CURSOR_OUTLINE);
        }
        for (int row = 0; row < 10; row++) {
            int span = row < 7 ? row + 1 : 9 - row;
            graphics.fill(x, y + row, x + span, y + row + 1, CURSOR_FILL);
        }
    }
}
