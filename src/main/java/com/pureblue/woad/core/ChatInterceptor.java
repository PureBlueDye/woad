package com.pureblue.woad.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads incoming system-chat packets straight off the network, before any other mod can cancel or
 * rewrite them.
 *
 * <p>{@code ClientReceiveMessageEvents.GAME} turned out to miss some Hypixel lines entirely &mdash;
 * notably Mort's dungeon-opening dialogue, which a chat-processing mod (e.g. SkyHanni) cancels and
 * re-emits through its own path. Intercepting {@link ClientboundSystemChatPacket} at the netty layer (the
 * same place {@link ServerTickCounter} counts ticks) guarantees we see every line. The work is then
 * marshalled onto the client thread so feature state is only ever touched from one thread.
 */
public final class ChatInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    private ChatInterceptor() {}

    /** Called from the connection mixin on the netty thread for every received packet. */
    public static void handle(Packet<?> packet) {
        if (!(packet instanceof ClientboundSystemChatPacket msg) || msg.overlay()) return;

        String raw = msg.content().getString();
        if (raw.contains("Mort")) LOGGER.info("[TickTime] Mort raw message: '{}'", raw);

        String stripped = raw.replaceAll("§.", "");
        Minecraft.getInstance().execute(() -> FeatureManager.onChatMessage(stripped));
    }
}
