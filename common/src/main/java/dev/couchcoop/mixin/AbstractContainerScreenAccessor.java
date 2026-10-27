package dev.couchcoop.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot positions are relative to the container's top-left corner, which is protected. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int couchcoop$getLeftPos();

    @Accessor("topPos")
    int couchcoop$getTopPos();
}
