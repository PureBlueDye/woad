package com.pureblue.woad.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the (protected) top-left pixel of a container GUI, so we can place buttons relative to it. */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

    @Accessor("leftPos")
    int woad$getX();

    @Accessor("topPos")
    int woad$getY();
}
