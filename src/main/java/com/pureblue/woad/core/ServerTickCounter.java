package com.pureblue.woad.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts server ticks the exact way the Odin mod does.
 *
 * <p>Hypixel sends a top-level common ping packet ({@code ClientboundPingPacket}) with a non-zero
 * parameter once per server tick. {@code ClientConnectionMixin} counts those packets at the netty
 * layer ({@code Connection.channelRead0}) &mdash; i.e. only the packets that arrive on their
 * own, not pings nested inside bundle packets. That distinction matters: counting bundled pings too
 * inflates the rate above 20/s and makes the run look longer than it really was.
 *
 * <p>The counter is incremented on the netty thread and read on the client thread, so it uses an
 * {@link AtomicLong}. It only ever increases; durations are measured as differences between two
 * snapshots, so the absolute value never needs resetting.
 */
public final class ServerTickCounter {

    private static final AtomicLong TICKS = new AtomicLong();

    private ServerTickCounter() {}

    /** Called once per received top-level server-tick ping, on the netty thread. */
    public static void increment() {
        TICKS.incrementAndGet();
    }

    /** Total server ticks observed since the client connected. */
    public static long get() {
        return TICKS.get();
    }
}
