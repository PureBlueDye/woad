package com.pureblue.woad.gui;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.pureblue.woad.features.InventoryButtonsFeature.CmdButton;
import com.pureblue.woad.features.InventoryButtonsFeature.Style;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Draws a single command button (shared by the live inventory and the editor). */
public final class InvButtonRenderer {

    public static final int SIZE = 16;
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");

    private InvButtonRenderer() {}

    public static void draw(GuiGraphicsExtractor ctx, Font tr, int x, int y, boolean hover,
                            Style style, int borderColor, CmdButton b) {
        // Background: vanilla button sprite, or a flat fill with a coloured border.
        if (style == Style.VANILLA) {
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, SIZE, SIZE);
        } else {
            ctx.fill(x, y, x + SIZE, y + SIZE, hover ? 0xF0323240 : 0xE01C1C24);
            int c = 0xFF000000 | (borderColor & 0xFFFFFF);
            ctx.fill(x, y, x + SIZE, y + 1, c);
            ctx.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, c);
            ctx.fill(x, y, x + 1, y + SIZE, c);
            ctx.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, c);
        }

        // Content: item icon, else a label/number, else a default glyph.
        ItemStack stack = b.getStack();
        if (!stack.isEmpty()) {
            ctx.item(stack, x, y);
        } else if (b.label != null && !b.label.isBlank()) {
            drawLabel(ctx, tr, b.label, x, y);
        } else {
            boolean has = b.command != null && !b.command.isBlank();
            ctx.centeredText(tr, Component.literal(has ? "»" : "+"),
                x + SIZE / 2, y + 4, has ? 0xFFE8E8EC : 0xFF8A8A92);
        }
    }

    private static void drawLabel(GuiGraphicsExtractor ctx, Font tr, String label, int x, int y) {
        int w = tr.width(label);
        if (w <= SIZE - 1) {
            ctx.centeredText(tr, Component.literal(label), x + SIZE / 2, y + 4, 0xFFFFFFFF);
        } else {
            float scale = (float) (SIZE - 1) / w;
            Matrix3x2fStack m = ctx.pose();
            m.pushMatrix();
            m.translate(x + SIZE / 2f, y + SIZE / 2f);
            m.scale(scale, scale);
            ctx.centeredText(tr, Component.literal(label), 0, -4, 0xFFFFFFFF);
            m.popMatrix();
        }
    }

    /**
     * Resolves the button's icon string to a stack, or EMPTY. Accepts:
     * <ul>
     *   <li>a vanilla item id ("diamond_sword", "minecraft:stone");</li>
     *   <li>"skull:&lt;value&gt;" for a player head, where value is a <b>player name</b> (its real
     *       skin is fetched asynchronously) or a texture hash / URL / base64 value.</li>
     * </ul>
     */
    public static ItemStack resolveItem(String input) {
        if (input == null || input.isBlank()) return ItemStack.EMPTY;
        String s = input.trim();
        if (s.regionMatches(true, 0, "skull:", 0, 6)) {
            return makeSkull(s.substring(6).trim());
        }
        if (s.regionMatches(true, 0, "head:", 0, 5)) { // backwards-compatible alias
            return makeSkull(s.substring(5).trim());
        }
        String id = s.contains(":") ? s : "minecraft:" + s;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(ident);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    /**
     * Builds a player-head stack. A short "[A-Za-z0-9_]" value is treated as a <b>player name</b>
     * and resolved to that player's real skin asynchronously (dynamic profile, like Firmament);
     * anything else is treated as a texture hash / URL / base64 value (static profile).
     */
    private static ItemStack makeSkull(String value) {
        if (value.isBlank()) return ItemStack.EMPTY;
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (value.matches("[A-Za-z0-9_]{1,16}")) {
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(value));
            return head;
        }
        String base64 = toTextureBase64(value);
        // authlib's GameProfile.properties() is immutable, so build the map first and pass it in.
        ArrayListMultimap<String, Property> mm = ArrayListMultimap.create();
        mm.put("textures", new Property("textures", base64));
        PropertyMap props = new PropertyMap(mm);
        // Stable UUID from the texture so identical heads share a profile (nicer for caching).
        GameProfile profile = new GameProfile(
            UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8)), "woad_head", props);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }

    private static String toTextureBase64(String value) {
        // If the value already decodes to a textures JSON, use it directly.
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (decoded.contains("textures")) return value;
        } catch (IllegalArgumentException ignored) {
            // not base64 → treat as a hash/url below
        }
        String url = value.startsWith("http") ? value : "http://textures.minecraft.net/texture/" + value;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
