package dev.couchcoop.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The variant pop-up's buttons; their own class is private, so they're exposed as plain widgets. */
@Mixin(OverlayRecipeComponent.class)
public interface OverlayRecipeComponentAccessor {
    @Accessor("recipeButtons")
    List<? extends AbstractWidget> couchcoop$getButtons();
}
