package dev.couchcoop.mixin;

import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A short rumble each time the player breaks a block. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void couchcoop$blockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client != null && cir.getReturnValueZ()) {
            client.onBlockBroken();
        }
    }
}
