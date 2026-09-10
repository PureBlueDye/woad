package com.pureblue.woad.customitem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Repaints the player's own items to match their saved overrides.
 *
 * <p>The work is done by writing data components onto the client's copy of each stack, so the game
 * renders the result itself — no rendering mixins, and tooltips, held item and worn armour all
 * follow along. The server keeps its own untouched copy; nothing here is sent anywhere.
 *
 * <p>It runs every tick because the server resends stacks constantly, which would otherwise wipe
 * the overrides. Writing the same values again is idempotent and costs nothing measurable.
 */
public final class CustomItemApplier {

    /** Slots worn on the body, which live outside the inventory container in 26.x. */
    private static final EquipmentSlot[] WORN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND,
    };

    private CustomItemApplier() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Every item now yields a key, so without this the tick would build one per stack for
        // nothing on a player who has customised none.
        if (CustomItemStore.count() == 0) return;

        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            apply(inventory.getItem(slot));
        }
        for (EquipmentSlot slot : WORN) {
            apply(mc.player.getItemBySlot(slot));
        }
        // Chests, the auction house, any open menu: those stacks are separate objects.
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            for (Slot slot : screen.getMenu().slots) {
                apply(slot.getItem());
            }
        }
    }

    /** Applies the stored look to one stack, in place. Does nothing for untouched items. */
    public static void apply(ItemStack stack) {
        CustomItem custom = CustomItemStore.get(CustomItemStore.keyOf(stack));
        if (custom != null) applyTo(stack, custom);
    }

    /**
     * Applies an override to a stack without consulting the store.
     *
     * <p>Used by the editor to build its preview from settings that are not saved yet.
     */
    public static void applyTo(ItemStack stack, CustomItem custom) {
        if (stack == null || stack.isEmpty() || custom == null) return;

        if (!custom.name.isEmpty()) {
            // Vanilla italicises renamed items; SkyBlock names are upright, so cancel it.
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(colorCodes(custom.name)).setStyle(Style.EMPTY.withItalic(false)));
        }

        Item look = resolveItem(custom.vanillaItem);
        if (look != null) {
            // ITEM_MODEL swaps the icon and held model; the item itself is left alone, so the
            // server still sees the real thing.
            stack.set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(look));
            // Swap the worn asset only for something that was already worn as armour. A player
            // head is drawn by its own head layer, so giving it a helmet asset as well showed the
            // helmet and the head at once on the body.
            if (stack.get(DataComponents.EQUIPPABLE) != null) {
                Equippable equippable = new ItemStack(look).get(DataComponents.EQUIPPABLE);
                if (equippable != null) stack.set(DataComponents.EQUIPPABLE, equippable);
            }
        }

        switch (custom.glint) {
            case ON -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            case OFF -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            case DEFAULT -> { /* leave the item's own shimmer alone */ }
        }

        int tint = custom.rgb ? rainbow() : custom.leatherColor;
        if (tint != CustomItem.NO_COLOR) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(tint));
        }
    }

    /**
     * Turns the {@code &} colour codes players type into the section sign the game renders.
     *
     * <p>Only real codes are converted, so a stray ampersand in a name survives untouched.
     */
    public static String colorCodes(String text) {
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    /** Resolves "golden_sword" or "minecraft:golden_sword" to an item, or {@code null}. */
    public static Item resolveItem(String id) {
        if (id == null || id.isBlank()) return null;
        Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id.trim());
        if (identifier == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == null || new ItemStack(item).isEmpty() ? null : item;
    }

    /** A full hue sweep every six seconds, bright and fully saturated. */
    public static int rainbow() {
        float hue = (System.currentTimeMillis() % 6000L) / 6000f;
        return java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
    }
}
