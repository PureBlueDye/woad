package com.pureblue.woad.mixin;

import com.pureblue.woad.net.NetworkChoice;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Sends the game's traffic out of the adapter picked in the server list.
 *
 * <p>Vanilla calls {@code Bootstrap.connect(address, port)} and lets the OS choose the source
 * address. Binding a local address instead makes the routing table use that adapter — the only way
 * to steer Minecraft onto one of several connections without touching the system's routes.
 *
 * <p>This covers joining a server and the server-list pings, both of which go through here. It does
 * not cover Mojang authentication, skins or resource-pack downloads: those are plain HTTP made by
 * other parts of the game.
 */
@Mixin(Connection.class)
public class ConnectionBindMixin {

    @Redirect(
            method = "connect",
            at = @At(value = "INVOKE",
                    target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)"
                            + "Lio/netty/channel/ChannelFuture;")
    )
    private static ChannelFuture woad$bindToChosenAdapter(Bootstrap bootstrap, InetAddress address, int port) {
        InetAddress local = NetworkChoice.boundAddress();
        if (local == null) {
            return bootstrap.connect(address, port); // automatic: behave exactly like vanilla
        }
        // Port 0 lets the OS pick the source port, as it normally would.
        return bootstrap.connect(new InetSocketAddress(address, port), new InetSocketAddress(local, 0));
    }
}
