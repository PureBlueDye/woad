package com.pureblue.woad.mixin;

import com.pureblue.woad.core.FeatureManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
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

    /** Chest Profit's colours go under the item, as the game's own hover highlight does. */
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void woad$highlightSlot(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (FeatureManager.CHEST_PROFIT.isEnabled()) {
            FeatureManager.CHEST_PROFIT.highlightSlot((AbstractContainerScreen<?>) (Object) this, ctx, slot);
        }
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void woad$drawInventoryButtons(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof InventoryScreen) {
            FeatureManager.INV_BUTTONS.renderInInventory(self, ctx, mouseX, mouseY);
        } else if (FeatureManager.CHEST_PROFIT.isEnabled()) {
            FeatureManager.CHEST_PROFIT.renderInContainer(self, ctx, mouseX, mouseY);
        }
    }
}
