package dev.couchcoop.mixin;

import dev.couchcoop.CouchCoopClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Analog left-stick walking: overrides the digital WASD vector when the stick is in use. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    private static final float DIRECTION_THRESHOLD = 0.3f;

    @Inject(method = "tick", at = @At("TAIL"))
    private void couchcoop$stickMovement(CallbackInfo ci) {
        CouchCoopClient client = CouchCoopClient.get();
        if (client == null) {
            return;
        }
        Vec2 stick = client.movement();
        if (stick.x == 0 && stick.y == 0) {
            return;
        }
        Input keys = this.keyPresses;
        // Direction flags feed sprinting, boats and riding, so derive them from the stick too.
        this.keyPresses = new Input(
            keys.forward() || stick.y > DIRECTION_THRESHOLD,
            keys.backward() || stick.y < -DIRECTION_THRESHOLD,
            keys.left() || stick.x > DIRECTION_THRESHOLD,
            keys.right() || stick.x < -DIRECTION_THRESHOLD,
            keys.jump(),
            keys.shift(),
            keys.sprint()
        );
        this.moveVector = stick;
    }
}
