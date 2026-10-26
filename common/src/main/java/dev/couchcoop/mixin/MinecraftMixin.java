package dev.couchcoop.mixin;

import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void couchcoop$onTick(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null) {
            client.onTick((Minecraft) (Object) this);
        }
    }

    /** Only one splitscreen window can be focused; the others must not open the pause menu. */
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void couchcoop$keepPlayingUnfocused(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && client.keepRunningWhenUnfocused()) {
            ci.cancel();
        }
    }

    /**
     * Holding attack to mine requires a grabbed mouse, which unfocused windows can't have.
     * Only this call is redirected: reporting a grab elsewhere would make the window
     * steal the real cursor on resize.
     */
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean couchcoop$controllerCountsAsGrabbed(MouseHandler mouseHandler) {
        return mouseHandler.isMouseGrabbed() || CouchCoopClient.isControllerDriven();
    }
}
