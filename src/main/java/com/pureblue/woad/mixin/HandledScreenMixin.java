package com.pureblue.woad.mixin;

import com.pureblue.woad.core.FeatureManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the inventory command buttons at the end of {@code extractContents} — after the slots/items but
 * before the (deferred) item tooltip is flushed, so the tooltip stays on top. (Drawing them in
 * Fabric's afterRender put them over the tooltip.)
 */
@Mixin(AbstractContainerScreen.class)
public class HandledScreenMixin {

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void woad$drawInventoryButtons(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof InventoryScreen) {
            FeatureManager.INV_BUTTONS.renderInInventory(self, ctx, mouseX, mouseY);
        }
    }
}
