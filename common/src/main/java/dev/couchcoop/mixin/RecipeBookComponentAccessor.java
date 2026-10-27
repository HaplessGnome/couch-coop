package dev.couchcoop.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The recipe book keeps its widgets to itself rather than adding them to the screen. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabButtons")
    List<RecipeBookTabButton> couchcoop$getTabButtons();

    @Accessor("filterButton")
    CycleButton<Boolean> couchcoop$getFilterButton();

    @Accessor("searchBox")
    EditBox couchcoop$getSearchBox();

    @Accessor("recipeBookPage")
    RecipeBookPage couchcoop$getPage();
}
