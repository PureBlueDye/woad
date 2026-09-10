package com.pureblue.woad.features;

import com.pureblue.woad.core.Feature;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports the per-enemy damage of the Explosive Shot ability.
 *
 * <p>Hypixel announces the ability's result in chat, e.g.
 * "Your Explosive Shot hit 4 enemies for 369,286,222 damage." &mdash; where the number is the
 * total dealt across all enemies hit. We read that line straight off the network (so it works even
 * when it is hidden by another mod), then divide by the enemy count to report a single hit:
 *
 * <pre>[Woad] Explosive Shot dealt 92,321,556 damage.</pre>
 */
public class ExplosiveShotFeature extends Feature {

    private static final Logger LOGGER = LoggerFactory.getLogger("Woad");

    /**
     * Captures (enemy count, total damage) from Hypixel's Explosive Shot chat line, e.g.
     * "Your Explosive Shot hit 5 enemies for 961,905,496.2 damage." The total has thousands
     * separators and a decimal, and "enem\w+" matches both "enemy" and "enemies".
     */
    private static final Pattern PATTERN =
            Pattern.compile("Your Explosive Shot hit (\\d+) enem\\w+ for ([\\d,]+(?:\\.\\d+)?) damage");

    public ExplosiveShotFeature() {
        super("explosive_shot", "Explosive Shot",
                "Sends the per-enemy damage of your Explosive Shot ability to chat.",
                true);
    }

    @Override
    public void onChatMessage(String message) {
        Matcher matcher = PATTERN.matcher(message);
        if (!matcher.find()) return;

        int enemies = Integer.parseInt(matcher.group(1));
        double total = Double.parseDouble(matcher.group(2).replace(",", ""));
        if (enemies <= 0) return;

        double perHit = total / enemies;
        LOGGER.info("[ExplosiveShot] {} enemies, total {} -> {} per hit", enemies, total, perHit);

        sendModMessage(Component.literal("Explosive Shot dealt ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.US, "%,.1f", perHit)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" damage.").withStyle(ChatFormatting.GRAY)));
    }
}
