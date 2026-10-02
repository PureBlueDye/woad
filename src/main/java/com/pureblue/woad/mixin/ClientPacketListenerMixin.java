package com.pureblue.woad.mixin;

import com.pureblue.woad.core.FeatureManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands Render Optimizer each entity whose data the server just changed, once the change is applied
 * — that is when a dropped item learns what it is (an item entity arrives empty, then its stack
 * follows in a data update).
 *
 * <p>The handler first runs on the network thread and only forwards itself to the game thread, by
 * throwing; the end of the method is therefore reached on the game thread only, with the data in
 * place.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void woad$afterEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        FeatureManager.RENDER_OPTIMIZER.onEntityData(packet.id());
    }
}
