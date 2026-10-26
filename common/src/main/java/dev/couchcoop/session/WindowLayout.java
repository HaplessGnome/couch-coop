package dev.couchcoop.session;

import com.mojang.blaze3d.platform.Window;
import dev.couchcoop.CouchCoop;
import dev.couchcoop.config.CouchCoopConfig.Layout;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.sdl.SDLVideo.*;

/**
 * Tiles this instance's window into its share of the primary display: borderless, positioned
 * and sized through SDL. The original window bounds are restored when the session shrinks
 * back to a single player.
 */
public final class WindowLayout {
    public record Rect(int x, int y, int width, int height) {
    }

    private Rect applied;
    private Rect original;

    /**
     * @param index this window's position among the running instances
     * @param count number of running instances
     */
    public void apply(Window window, int index, int count, Layout layout, boolean coverTaskbar) {
        long handle = window.handle();
        if (count <= 1 || index < 0) {
            restore(handle);
            return;
        }

        Rect bounds = displayBounds(coverTaskbar);
        if (bounds == null) {
            return;
        }
        Rect target = tile(bounds, index, count, layout);
        if (target.equals(applied)) {
            return;
        }

        if (original == null) {
            original = currentBounds(handle);
        }
        SDL_SetWindowFullscreen(handle, false);
        SDL_RestoreWindow(handle);
        SDL_SetWindowBordered(handle, false);
        // The taskbar sits above normal windows, so tiles that cover it must be topmost too.
        SDL_SetWindowAlwaysOnTop(handle, coverTaskbar);
        SDL_SetWindowSize(handle, target.width(), target.height());
        SDL_SetWindowPosition(handle, target.x(), target.y());
        applied = target;
        CouchCoop.LOGGER.info("Tiled window {}/{} to {}", index + 1, count, target);
    }

    private void restore(long handle) {
        if (applied == null) {
            return;
        }
        SDL_SetWindowAlwaysOnTop(handle, false);
        SDL_SetWindowBordered(handle, true);
        if (original != null) {
            SDL_SetWindowSize(handle, original.width(), original.height());
            SDL_SetWindowPosition(handle, original.x(), original.y());
        }
        applied = null;
        original = null;
    }

    static Rect tile(Rect b, int index, int count, Layout layout) {
        int halfW = b.width() / 2;
        int halfH = b.height() / 2;
        boolean stacked = layout == Layout.TOP_BOTTOM;
        if (count == 2) {
            return stacked
                ? new Rect(b.x(), b.y() + index * halfH, b.width(), halfH)
                : new Rect(b.x() + index * halfW, b.y(), halfW, b.height());
        }
        if (count == 3 && index == 0) {
            // Player 1 keeps a full-width (or full-height) strip, the other two split the rest.
            return stacked
                ? new Rect(b.x(), b.y(), b.width(), halfH)
                : new Rect(b.x(), b.y(), halfW, b.height());
        }
        if (count == 3) {
            int i = index - 1;
            return stacked
                ? new Rect(b.x() + i * halfW, b.y() + halfH, halfW, halfH)
                : new Rect(b.x() + halfW, b.y() + i * halfH, halfW, halfH);
        }
        return new Rect(b.x() + (index % 2) * halfW, b.y() + (index / 2) * halfH, halfW, halfH);
    }

    /** The whole primary display, or just the part the taskbar leaves free. */
    private static Rect displayBounds(boolean coverTaskbar) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Rect rect = SDL_Rect.malloc(stack);
            int display = SDL_GetPrimaryDisplay();
            boolean ok = coverTaskbar ? SDL_GetDisplayBounds(display, rect) : SDL_GetDisplayUsableBounds(display, rect);
            if (!ok) {
                return null;
            }
            return new Rect(rect.x(), rect.y(), rect.w(), rect.h());
        }
    }

    private static Rect currentBounds(long handle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            SDL_GetWindowPosition(handle, x, y);
            SDL_GetWindowSize(handle, w, h);
            return new Rect(x.get(0), y.get(0), w.get(0), h.get(0));
        }
    }
}
