package com.pureblue.woad.features;

import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.SkyBlockArea;
import com.pureblue.woad.core.setting.BooleanSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Things the game does not need to draw. There is no switch for the whole feature: each
 * optimisation has its own.
 *
 * <p>Skeleton skulls: the Catacombs are littered with them, lying on the floor or flying around.
 * They are armor stands wearing a "Skeleton Skull" as a helmet, so while in the Catacombs any armor
 * stand dressed like that is simply not rendered — standing still or moving.
 */
public class RenderOptimizerFeature extends Feature {

    private static final String SKELETON_SKULL = "Skeleton Skull";

    private final BooleanSetting hideSkeletonSkulls = addSetting(new BooleanSetting("Hide skeleton skulls",
            "Don't draw the skeleton skulls in dungeons, whether lying around or moving.", true));

    private final BooleanSetting hideArcherPassive = addSetting(new BooleanSetting("Hide archer passive",
            "Remove the bone meal that spins around the Archer in dungeons.", true));

    public RenderOptimizerFeature() {
        super("render_optimizer", "Render Optimizer",
                "Skips drawing things you don't need to see. Each optimisation has its own switch.",
                true);
    }

    @Override
    public boolean hasToggle() {
        return false;
    }

    /**
     * Archer's passive: the bone meal it spins around in dungeons. Each piece is a dropped-item
     * entity holding bone meal, so as soon as an item entity turns out to be one, it is taken out of
     * the client's world altogether — not drawn, not ticked. The server still has it; nothing is
     * lost.
     */
    public void onEntityData(int entityId) {
        if (!hideArcherPassive.enabled() || !SkyBlockArea.inCatacombs()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(entityId) instanceof ItemEntity item && item.getItem().is(Items.BONE_MEAL)) {
            level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
        }
    }

    /** Asked for every entity, every frame: answers fast for anything that is not concerned. */
    public boolean shouldHide(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) return false;
        if (!hideSkeletonSkulls.enabled() || !SkyBlockArea.inCatacombs()) return false;
        ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        // The vanilla skull itself, whatever the game's language, or a head named after it.
        if (helmet.is(Items.SKELETON_SKULL)) return true;
        String name = ChatFormatting.stripFormatting(helmet.getHoverName().getString());
        return SKELETON_SKULL.equals(name == null ? null : name.trim());
    }
}
