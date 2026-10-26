package dev.couchcoop.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /** Runs once per rendered frame, just before mouse look is applied. */
    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void couchcoop$onFrame(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null) {
            client.onFrame(Minecraft.getInstance());
        }
    }

    /**
     * Grabbing warps the shared OS cursor to this window's centre (on every screen close), which
     * yanks it away from the keyboard player. A controller-driven window has no use for it.
     */
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void couchcoop$noGrabForController(CallbackInfo ci) {
        if (CouchCoopClient.isControllerDriven()) {
            ci.cancel();
        }
    }

    /** The real mouse belongs to the keyboard-and-mouse player's window; ignore its clicks here. */
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void couchcoop$ignoreOthersClicks(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && client.ignoresRealMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void couchcoop$ignoreOthersScroll(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && client.ignoresRealMouse()) {
            ci.cancel();
        }
    }

    /** Screens read the mouse position for hover and tooltips; report the controller cursor. */
    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void couchcoop$cursorX(Window window, CallbackInfoReturnable<Double> cir) {
        if (CouchCoopClient.isCursorActive()) {
            cir.setReturnValue(CouchCoopClient.get().input().cursor().x());
        }
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void couchcoop$cursorY(Window window, CallbackInfoReturnable<Double> cir) {
        if (CouchCoopClient.isCursorActive()) {
            cir.setReturnValue(CouchCoopClient.get().input().cursor().y());
        }
    }
}
