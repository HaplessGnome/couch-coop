package dev.couchcoop.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.couchcoop.CouchCoopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Several game windows rendering flat out fight over one GPU and stutter each other. While the
 * screen is split, each window is capped (60 FPS by default), which is plenty for a half screen.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void couchcoop$capWhileSplit(CallbackInfoReturnable<Integer> cir) {
        CouchCoopClient client = CouchCoopClient.get();
        int cap = client == null ? 0 : client.splitFrameLimit();
        if (cap > 0 && cir.getReturnValueI() > cap) {
            cir.setReturnValue(cap);
        }
    }
}
