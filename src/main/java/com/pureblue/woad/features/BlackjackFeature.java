package com.pureblue.woad.features;

import com.pureblue.woad.blackjack.BlackjackManager;
import com.pureblue.woad.blackjack.chat.ChatOutput;
import com.pureblue.woad.blackjack.gui.BlackjackScreen;
import com.pureblue.woad.core.Feature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Blackjack played through the Hypixel party or guild chat: one client hosts the table and deals,
 * the others follow along from the broadcast status lines.
 *
 * <p>Players drive it with chat commands ({@code !bj create}, {@code !bj join}, {@code !bj start},
 * {@code !hit}, {@code !stand}, {@code !split}); the table itself is drawn by {@link BlackjackScreen},
 * opened from the menu's "Open" button or with {@code /bj}.
 *
 * <p>The game logic is untouched from the standalone mod — only the plumbing changed: chat lines now
 * arrive through Woad's own interceptor instead of a second connection mixin.
 */
public class BlackjackFeature extends Feature {

    public BlackjackFeature() {
        super("blackjack", "Blackjack",
                "Play blackjack in the party or guild chat. One player hosts the table with "
                        + "!bj create, the others join with !bj join. Open the table with /bj.",
                false);
    }

    @Override
    public void onChatMessage(String message) {
        BlackjackManager.onChatLine(message);
    }

    @Override
    public void onClientTick() {
        // Releases one queued status line at a time, paced under Hypixel's command throttle.
        ChatOutput.tick();
    }

    /** Nothing should linger once the feature is switched off mid-game. */
    @Override
    protected void onDisabled() {
        BlackjackManager.reset();
    }

    @Override
    public boolean hasConfigScreen() {
        return true;
    }

    @Override
    public Screen createConfigScreen(Screen parent) {
        return new BlackjackScreen();
    }
}
