package dev.couchcoop.mixin;

import dev.couchcoop.CouchCoop;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guest windows share the host's game directory. They must not write options.txt, or a muted
 * guest or its tiled window state would overwrite the host's settings.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void couchcoop$guestsDontSave(CallbackInfo ci) {
        if (CouchCoop.isChild()) {
            ci.cancel();
        }
    }
}
