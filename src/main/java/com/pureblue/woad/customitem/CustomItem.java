package com.pureblue.woad.customitem;

import com.google.gson.JsonObject;

/**
 * The client-side look given to one SkyBlock item.
 *
 * <p>Every field is optional: left at its default, the item keeps whatever the server sent. Nothing
 * here leaves the client — it only changes what this player sees.
 */
public final class CustomItem {

    /** Marks "no colour chosen", since 0 is a valid colour (black). */
    public static final int NO_COLOR = -1;

    /** Whether to force the enchantment shimmer on or off, or leave the item's own behaviour. */
    public enum Glint { DEFAULT, ON, OFF }

    /**
     * Replacement name, or empty to keep the original. Accepts Minecraft colour codes written with
     * {@code &}, e.g. {@code &6Hyperion4}, the same way they are typed everywhere else in SkyBlock.
     */
    public String name = "";
    /** Vanilla item whose look to borrow ("golden_sword"), or empty to keep the original. */
    public String vanillaItem = "";
    public Glint glint = Glint.DEFAULT;
    /** Leather tint, only meaningful when the chosen look is leather armour. */
    public int leatherColor = NO_COLOR;
    /** Cycles the leather tint through the rainbow instead of using a fixed colour. */
    public boolean rgb = false;

    /** True when this override would change nothing, and is therefore not worth storing. */
    public boolean isEmpty() {
        return name.isEmpty()
                && vanillaItem.isEmpty()
                && glint == Glint.DEFAULT
                && leatherColor == NO_COLOR
                && !rgb;
    }

    public CustomItem copy() {
        CustomItem copy = new CustomItem();
        copy.name = name;
        copy.vanillaItem = vanillaItem;
        copy.glint = glint;
        copy.leatherColor = leatherColor;
        copy.rgb = rgb;
        return copy;
    }

    public JsonObject write() {
        JsonObject node = new JsonObject();
        if (!name.isEmpty()) node.addProperty("name", name);
        if (!vanillaItem.isEmpty()) node.addProperty("item", vanillaItem);
        if (glint != Glint.DEFAULT) node.addProperty("glint", glint.name());
        if (leatherColor != NO_COLOR) node.addProperty("leatherColor", leatherColor);
        if (rgb) node.addProperty("rgb", true);
        return node;
    }

    public static CustomItem read(JsonObject node) {
        CustomItem item = new CustomItem();
        if (node.has("name")) item.name = node.get("name").getAsString();
        if (node.has("item")) item.vanillaItem = node.get("item").getAsString();
        if (node.has("glint")) {
            try {
                item.glint = Glint.valueOf(node.get("glint").getAsString());
            } catch (IllegalArgumentException ignored) {
                // an unknown value simply means "leave it alone"
            }
        }
        if (node.has("leatherColor")) item.leatherColor = node.get("leatherColor").getAsInt();
        if (node.has("rgb")) item.rgb = node.get("rgb").getAsBoolean();
        return item;
    }
}
