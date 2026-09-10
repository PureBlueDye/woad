package com.pureblue.woad.mixin;

import com.pureblue.woad.core.ChatInterceptor;
import com.pureblue.woad.core.ServerTickCounter;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the netty packet read, exactly like Odin's {@code ConnectionMixin}, for two things:
 *
 * <ul>
 *   <li>Counting server ticks: each top-level {@link ClientboundPingPacket} with a non-zero parameter
 *       is one server tick on Hypixel. Counting here (not in the per-packet handler) avoids
 *       double-counting pings bundled inside other packets, keeping the rate at the true ~20/s.</li>
 *   <li>Reading system chat before any other mod can cancel it (see {@link ChatInterceptor}) —
 *       this is also how hidden Hypixel lines (e.g. the Explosive Shot result) are still read.</li>
 * </ul>
 */
@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void woad$onPacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        // Ping counting must stay top-level only (bundled pings would inflate the tick rate).
        if (packet instanceof ClientboundPingPacket ping && ping.getId() != 0) {
            ServerTickCounter.increment();
        }
        ChatInterceptor.handle(packet);
    }
}
