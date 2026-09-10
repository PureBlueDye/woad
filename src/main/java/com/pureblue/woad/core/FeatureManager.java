package com.pureblue.woad.core;

import com.pureblue.woad.features.AiChatFeature;
import com.pureblue.woad.features.BlackjackFeature;
import com.pureblue.woad.features.CustomLavaFeature;
import com.pureblue.woad.features.ExplosiveShotFeature;
import com.pureblue.woad.features.InventoryButtonsFeature;
import com.pureblue.woad.features.JerryTimerFeature;
import com.pureblue.woad.features.LoadoutKeybindsFeature;
import com.pureblue.woad.features.TickTimeFeature;
import com.pureblue.woad.features.TranslatorFeature;

import java.util.ArrayList;
import java.util.List;

/** Owns every {@link Feature}, and fans client ticks and chat messages out to the enabled ones. */
public final class FeatureManager {

    private static final List<Feature> FEATURES = new ArrayList<>();

    public static final TickTimeFeature TICK_TIME = register(new TickTimeFeature());
    public static final ExplosiveShotFeature EXPLOSIVE_SHOT = register(new ExplosiveShotFeature());
    public static final JerryTimerFeature JERRY_TIMER = register(new JerryTimerFeature());
    public static final CustomLavaFeature CUSTOM_LAVA = register(new CustomLavaFeature());
    public static final InventoryButtonsFeature INV_BUTTONS = register(new InventoryButtonsFeature());
    public static final LoadoutKeybindsFeature LOADOUT_KEYBINDS = register(new LoadoutKeybindsFeature());
    public static final AiChatFeature AI_CHAT = register(new AiChatFeature());
    public static final TranslatorFeature TRANSLATOR = register(new TranslatorFeature());
    public static final BlackjackFeature BLACKJACK = register(new BlackjackFeature());

    private FeatureManager() {}

    private static <T extends Feature> T register(T feature) {
        FEATURES.add(feature);
        return feature;
    }

    public static List<Feature> getFeatures() {
        return FEATURES;
    }

    public static void onClientTick() {
        for (Feature feature : FEATURES) {
            if (feature.isEnabled()) feature.onClientTick();
        }
    }

    public static void onChatMessage(String message) {
        for (Feature feature : FEATURES) {
            if (feature.isEnabled()) feature.onChatMessage(message);
        }
    }

    /** Player chat (singleplayer / vanilla servers), with the sender already separated out. */
    public static void onPlayerChatMessage(String sender, String content) {
        for (Feature feature : FEATURES) {
            if (feature.isEnabled()) feature.onPlayerChatMessage(sender, content);
        }
    }
}
