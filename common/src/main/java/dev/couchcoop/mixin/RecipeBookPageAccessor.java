package dev.couchcoop.mixin;

import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RecipeBookPage.class)
public interface RecipeBookPageAccessor {
    @Accessor("buttons")
    List<RecipeButton> couchcoop$getButtons();

    @Accessor("forwardButton")
    ImageButton couchcoop$getForwardButton();

    @Accessor("backButton")
    ImageButton couchcoop$getBackButton();

    /** The pop-up listing a recipe's variants. */
    @Accessor("overlay")
    OverlayRecipeComponent couchcoop$getOverlay();
}
