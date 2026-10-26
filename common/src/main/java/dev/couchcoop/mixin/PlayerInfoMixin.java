package dev.couchcoop.mixin;

import dev.couchcoop.profile.GuestSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player models, heads and the tab list all ask PlayerInfo for skins; guest profiles answer here. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void couchcoop$guestSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = GuestSkins.get(((PlayerInfo) (Object) this).getProfile().name());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}
