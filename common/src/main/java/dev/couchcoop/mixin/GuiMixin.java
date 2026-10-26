package dev.couchcoop.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.couchcoop.CouchCoopOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class GuiMixin {
    /** The last GUI call of the frame, and it hands us the graphics object: draw on top here. */
    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V")
    )
    private void couchcoop$drawOverlay(GuiGraphicsExtractor graphics, Window window) {
        CouchCoopOverlay.draw(graphics);
        graphics.applyCursor(window);
    }
}
