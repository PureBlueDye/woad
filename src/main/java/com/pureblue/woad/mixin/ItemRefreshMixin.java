package com.pureblue.woad.mixin;

import com.pureblue.woad.customitem.CustomItemApplier;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repaints customised items the instant the server replaces them.
 *
 * <p>Applying only once per tick left a gap: the game renders far faster than it ticks, so a stack
 * the server had just resent was drawn in its original form for the frames before the next tick —
 * a visible flicker when swapping quickly, and on dyed armour.
 *
 * <p>Patching here closes the gap entirely, because the stack is repainted in the same call that
 * installed it, before anything can draw it.
 */
@Mixin(ClientPacketListener.class)
public class ItemRefreshMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void woad$afterSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        CustomItemApplier.tick();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void woad$afterContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        CustomItemApplier.tick();
    }

    @Inject(method = "handleSetEquipment", at = @At("TAIL"))
    private void woad$afterEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        CustomItemApplier.tick();
    }
}
