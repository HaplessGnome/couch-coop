package dev.couchcoop.mixin;

import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clicks on the quick craft panel craft; they must never count as clicking outside the inventory. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void couchcoop$quickCraftClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && client.quickCraft().click(Minecraft.getInstance(), event.x(), event.y(), event.hasShiftDown())) {
            cir.setReturnValue(true);
        }
    }
}
