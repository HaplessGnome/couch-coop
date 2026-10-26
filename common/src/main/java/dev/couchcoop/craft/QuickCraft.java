package dev.couchcoop.craft;

import dev.couchcoop.CouchCoop;
import dev.couchcoop.input.Gamepad;
import dev.couchcoop.input.GamepadInput;
import dev.couchcoop.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick craft list beside the inventory and crafting table: every unlocked recipe that fits
 * the grid and can be made from what the player is carrying. Crafting asks the server to fill
 * the grid exactly as the recipe book does, then shift-clicks the result into the inventory, so
 * it works on any server without server-side support.
 */
public final class QuickCraft {
    public record Entry(RecipeDisplayId id, ItemStack result) {
    }

    /** Where the panel sits, in GUI pixels. Rows start below a one-line header. */
    public record Layout(int left, int top, int width, int rowHeight, int firstRow, int rows) {
    }

    private static final int REFRESH_FRAMES = 10;
    private static final long TAKE_TIMEOUT_MS = 1_500;
    public static final int PANEL_WIDTH = 120;
    public static final int ROW_HEIGHT = 18;
    public static final int HEADER = 12;

    private final List<Entry> entries = new ArrayList<>();
    private Screen screen;
    private int selected;
    private RecipeDisplayId selectedId;
    private int framesUntilRefresh;
    private Layout layout;

    private ItemStack pending;
    private long pendingSince;

    public boolean isActive() {
        return screen != null && layout != null;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int selected() {
        return selected;
    }

    public Layout layout() {
        return layout;
    }

    /** First entry shown, so the selection stays on screen when the list is longer than the panel. */
    public int scroll() {
        return scrollFor(selected, entries.size(), layout.rows());
    }

    static int scrollFor(int selected, int count, int rows) {
        if (count <= rows) {
            return 0;
        }
        return Math.clamp(selected - rows / 2, 0, count - rows);
    }

    /** Runs every frame for every window; {@code pad} is null for keyboard and mouse players. */
    public void frame(Minecraft minecraft, Gamepad pad) {
        Screen current = minecraft.gui.screen();
        if (!CouchCoop.config().quickCraft || !(current instanceof AbstractContainerScreen<?> container)
            || !(container.getMenu() instanceof AbstractCraftingMenu menu) || minecraft.player == null) {
            screen = null;
            layout = null;
            pending = null;
            return;
        }
        if (current != screen) {
            screen = current;
            framesUntilRefresh = 0;
            pending = null;
        }
        if (--framesUntilRefresh <= 0) {
            framesUntilRefresh = REFRESH_FRAMES;
            refresh(minecraft, menu);
        }
        layout = place(container);
        takeResult(minecraft, menu);

        if (pad != null && !entries.isEmpty()) {
            if (pad.pressed(GamepadInput.L1)) {
                select(selected - 1);
            }
            if (pad.pressed(GamepadInput.R1)) {
                select(selected + 1);
            }
            if (pad.pressed(GamepadInput.R2)) {
                craft(minecraft, menu, entries.get(selected), false);
            }
            if (pad.pressed(GamepadInput.L2)) {
                craft(minecraft, menu, entries.get(selected), true);
            }
        }
    }

    /** A click on a panel row crafts it (shift for as many as possible). True if the click was ours. */
    public boolean click(Minecraft minecraft, double x, double y, boolean shift) {
        int row = rowAt(x, y);
        if (row < 0 || !(minecraft.gui.screen() instanceof AbstractContainerScreen<?> container)
            || !(container.getMenu() instanceof AbstractCraftingMenu menu)) {
            return isActive() && inside(x, y);
        }
        select(row);
        craft(minecraft, menu, entries.get(row), shift);
        return true;
    }

    /** Index of the entry under (x, y), or -1. */
    public int rowAt(double x, double y) {
        if (!isActive() || !inside(x, y) || y < layout.top() + layout.firstRow()) {
            return -1;
        }
        int index = scroll() + (int) ((y - layout.top() - layout.firstRow()) / layout.rowHeight());
        return index < entries.size() && index < scroll() + layout.rows() ? index : -1;
    }

    private boolean inside(double x, double y) {
        return x >= layout.left() && x < layout.left() + layout.width()
            && y >= layout.top() && y < layout.top() + layout.firstRow() + layout.rows() * layout.rowHeight();
    }

    private void select(int index) {
        selected = Math.floorMod(index, entries.size());
        selectedId = entries.get(selected).id();
    }

    private void refresh(Minecraft minecraft, AbstractCraftingMenu menu) {
        StackedItemContents contents = new StackedItemContents();
        minecraft.player.getInventory().fillStackedContents(contents);
        menu.fillCraftSlotsStackedContents(contents);
        var context = SlotDisplayContext.fromLevel(minecraft.level);

        entries.clear();
        for (RecipeCollection collection : minecraft.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry recipe : collection.getRecipes()) {
                if (fits(recipe.display(), menu) && recipe.canCraft(contents)) {
                    List<ItemStack> results = recipe.resultItems(context);
                    if (!results.isEmpty() && !results.getFirst().isEmpty()) {
                        entries.add(new Entry(recipe.id(), results.getFirst()));
                    }
                }
            }
        }

        // Keep the same recipe selected as the list changes around it.
        selected = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(selectedId)) {
                selected = i;
            }
        }
    }

    /** Same rule the vanilla crafting recipe book uses. */
    private static boolean fits(RecipeDisplay display, AbstractCraftingMenu menu) {
        return switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.width() <= menu.getGridWidth() && shaped.height() <= menu.getGridHeight();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients().size() <= menu.getGridWidth() * menu.getGridHeight();
            default -> false;
        };
    }

    private void craft(Minecraft minecraft, AbstractCraftingMenu menu, Entry entry, boolean all) {
        if (pending != null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handlePlaceRecipe(menu.containerId, entry.id(), all);
        pending = entry.result();
        pendingSince = System.currentTimeMillis();
    }

    /** Once the server has filled the grid, shift-click the result so it lands in the inventory. */
    private void takeResult(Minecraft minecraft, AbstractCraftingMenu menu) {
        if (pending == null) {
            return;
        }
        // Wait for the item we asked for: the grid may still hold a different recipe until the server answers.
        Slot result = menu.getResultSlot();
        if (result.hasItem() && ItemStack.isSameItem(result.getItem(), pending)) {
            minecraft.gameMode.handleContainerInput(menu.containerId, result.index, 0, ContainerInput.QUICK_MOVE, minecraft.player);
            pending = null;
            framesUntilRefresh = 0;
        } else if (System.currentTimeMillis() - pendingSince > TAKE_TIMEOUT_MS) {
            pending = null;
        }
    }

    /** Right of the container if there is room, otherwise left of it. */
    private static Layout place(AbstractContainerScreen<?> container) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
        int left = accessor.couchcoop$getLeftPos();
        int top = accessor.couchcoop$getTopPos();
        int imageWidth = accessor.couchcoop$getImageWidth();
        int imageHeight = accessor.couchcoop$getImageHeight();
        int x = left + imageWidth + 4;
        if (x + PANEL_WIDTH > container.width - 2) {
            x = Math.max(2, left - PANEL_WIDTH - 4);
        }
        int rows = Math.max(1, (imageHeight - HEADER - 4) / ROW_HEIGHT);
        return new Layout(x, top, PANEL_WIDTH, ROW_HEIGHT, HEADER, rows);
    }
}
