package com.pureblue.woad.features;

import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.ServerTickCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Reports how long a Catacombs run took in <em>tick time</em>, computed exactly like Odin's Splits.
 *
 * <p>The run is timed in server ticks (see {@link ServerTickCounter}) from the "Blood Open" moment
 * &mdash; Mort's opening line, which is Odin's first dungeon split &mdash; to the boss-defeat line,
 * matching the "Total" value Odin shows. Tick time advances only when the server processes a tick,
 * so it excludes lag. The result is announced one second after the boss is defeated, e.g.:
 *
 * <pre>[Woad] The run took 5m23 (tick time).</pre>
 */
public class TickTimeFeature extends Feature {

    /** Last line of the Hypixel dungeon countdown; arms the run. */
    private static final String START_MESSAGE = "Starting in 1 second.";

    /** Boss-defeat line that ends a run, e.g. "☠ Defeated Necron in 4m 20s". */
    private static final Pattern DEFEAT_PATTERN = Pattern.compile("^\\s*☠ Defeated .+ in .+$");

    /** Send the announcement this many client ticks after the boss-defeat line (1 second). */
    private static final int ANNOUNCE_DELAY_TICKS = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    private boolean running = false;
    private long runStartTick = 0L;          // tick at "Starting in 1 second." (fallback start)
    private long bloodOpenTick = -1L;        // tick at Mort's line (Odin's Total start)

    // Pending (delayed) announcement.
    private MutableComponent pendingMessage = null;
    private int pendingDelayTicks = 0;

    public TickTimeFeature() {
        super("tick_time", "Tick Time",
                "Sends the run's tick time to chat at the end of the run.",
                true);
    }

    @Override
    public void onClientTick() {
        // Cancel a run only if the player actually left the world (back to menu / disconnected).
        if (running && Minecraft.getInstance().level == null) {
            running = false;
        }

        // Fire the delayed announcement.
        if (pendingMessage != null && --pendingDelayTicks <= 0) {
            sendModMessage(pendingMessage);
            pendingMessage = null;
        }
    }

    @Override
    public void onChatMessage(String message) {
        if (message.equals(START_MESSAGE)) {
            running = true;
            runStartTick = ServerTickCounter.get();
            bloodOpenTick = -1L;
            LOGGER.info("[TickTime] Run armed at tick {}.", runStartTick);
            return;
        }

        if (!running) return;

        // Odin's first dungeon split ("Blood Open"): start the tick timer at Mort's opening line.
        // Match on a distinctive phrase (no color codes inside it) so it works regardless of
        // formatting, which is more robust than matching the whole formatted line.
        if (bloodOpenTick == -1L
                && (message.contains("found this map") || message.contains("Right-click the Orb"))) {
            bloodOpenTick = ServerTickCounter.get();
            LOGGER.info("[TickTime] Blood Open at tick {}.", bloodOpenTick);
            return;
        }

        if (DEFEAT_PATTERN.matcher(message).matches()) {
            long startTick = bloodOpenTick != -1L ? bloodOpenTick : runStartTick;
            long elapsedTicks = ServerTickCounter.get() - startTick;
            running = false;
            LOGGER.info("[TickTime] Run ended at tick {}: {} ticks = {}",
                    ServerTickCounter.get(), elapsedTicks, formatTime(elapsedTicks / 20.0));

            pendingMessage = buildMessage(elapsedTicks);
            pendingDelayTicks = ANNOUNCE_DELAY_TICKS;
        }
    }

    @Override
    protected void onDisabled() {
        running = false;
        pendingMessage = null;
    }

    private MutableComponent buildMessage(long elapsedTicks) {
        String tickTime = formatTime(elapsedTicks / 20.0);
        return Component.literal("The run took ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tickTime).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (tick time).").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Formats a duration in seconds with one decimal (like Odin's tick display), e.g. "23.6s",
     * "5m23.6" or "1h05m03.6". The whole total is rounded to 0.1s first so a value like 59.96s
     * correctly rolls over to "1m00.0".
     */
    public static String formatTime(double totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long tenths = Math.round(totalSeconds * 10.0);   // total tenths of a second
        long whole = tenths / 10;
        long decimal = tenths % 10;

        long hours = whole / 3600;
        long minutes = (whole % 3600) / 60;
        long seconds = whole % 60;

        if (hours > 0) {
            return hours + "h" + pad(minutes) + "m" + pad(seconds) + "." + decimal;
        }
        if (minutes > 0) {
            return minutes + "m" + pad(seconds) + "." + decimal;
        }
        return seconds + "." + decimal + "s";
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }
}
