package com.pureblue.woad.gui;

import com.pureblue.woad.customitem.CustomItem;
import com.pureblue.woad.customitem.CustomItemApplier;
import com.pureblue.woad.customitem.CustomItemStore;
import com.pureblue.woad.ui.Anim;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiText;
import com.pureblue.woad.ui.UiTextField;
import com.pureblue.woad.ui.UiToggle;
import com.pureblue.woad.ui.UiValueButton;
import com.pureblue.woad.ui.UiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor for the look of the item in hand.
 *
 * <p>Everything is typed in place — no sub-windows — and the preview updates as the fields change,
 * so the panel shows the result rather than describing it. The dye rows only appear once the
 * chosen id is leather armour, which keeps the screen down to what actually applies.
 */
public class CustomItemScreen extends UiScreen {

    private static final int PANEL_W = 300;
    private static final int PAD = 12;
    private static final int PREVIEW = 76;
    private static final int LABEL_W = 30;
    private static final int BADGE = 16;
    private static final int ROW_H = 18;
    private static final int GAP = 5;
    /** Room for five rows even when only three show, so the panel does not jump in size. */
    private static final int ROWS_H = 5 * ROW_H + 4 * GAP;

    /**
     * The code reference keeps a light background, unlike the rest of the interface.
     *
     * <p>A dark background leaves {@code &0} and {@code &1} all but invisible, and no flat colour
     * fixes this on its own: the palette spans every luminance, so the best possible worst-case
     * contrast is only 1.3. What works is the game's own answer: a light panel plus the text
     * shadow. Dark codes stand out against the panel, and light ones get a dark outline from their
     * shadow, 6 to 9 times the contrast of the glyph itself.
     */
    private static final int HELP_BG = 0xFFC4CCDA;
    private static final int HELP_EDGE = 0xFF2A3752;
    private static final int HELP_TEXT = 0xFF33415A;

    /**
     * A help line, split so each half can be drawn differently. Names are flat text. Only the
     * colour codes carry the drop shadow — it is what keeps the light colours readable — since on
     * the dark format codes a shadow just smudges the glyph.
     */
    private record HelpLine(Component name, Component codes, boolean shadow) {}

    private static final List<HelpLine> HELP = buildHelp();

    private static List<HelpLine> buildHelp() {
        List<HelpLine> lines = new ArrayList<>();

        // All sixteen colours on one line, each code printed in the colour it produces.
        MutableComponent colours = Component.empty();
        for (char code : "0123456789abcdef".toCharArray()) {
            if (code != '0') colours.append(Component.literal(" "));
            colours.append(Component.literal("&" + code).withStyle(ChatFormatting.getByCode(code)));
        }
        lines.add(new HelpLine(Component.literal("Colour"), colours, true));

        // One line per kind of formatting, the name written in the style it produces.
        lines.add(styled("Bold", 'l'));
        lines.add(styled("Italic", 'o'));
        lines.add(styled("Underline", 'n'));
        lines.add(styled("Strike", 'm'));
        // "Magic" stays plain: applying it scrambles the glyphs, which reads as a rendering bug
        // rather than as an example — and "Reset" has no look of its own to show.
        lines.add(new HelpLine(Component.literal("Magic"), Component.literal("&k"), false));
        lines.add(new HelpLine(Component.literal("Reset"), Component.literal("&r"), false));
        return lines;
    }

    /** A help line whose name demonstrates the very style it names. */
    private static HelpLine styled(String name, char code) {
        ChatFormatting format = ChatFormatting.getByCode(code);
        return new HelpLine(Component.literal(name).withStyle(format),
                Component.literal("&" + code).withStyle(format), false);
    }

    private final ItemStack original;
    private final String key;
    private CustomItem draft;

    private final UiTextField nameField;
    private final UiTextField idField;
    private final UiTextField dyeField;
    private final UiValueButton glint;
    private final UiToggle rgb;
    private final HelpBadge help = new HelpBadge();
    private final UiButton reset;
    private final UiButton close;
    private final UiButton save;

    public CustomItemScreen(ItemStack held) {
        super(Component.literal("Custom item"));
        this.original = held.copy();
        this.key = CustomItemStore.keyOf(held);
        CustomItem saved = CustomItemStore.get(key);
        this.draft = saved == null ? new CustomItem() : saved.copy();

        nameField = add(new UiTextField(draft.name, 64));
        nameField.box().setResponder(value -> draft.name = value);
        nameField.box().addFormatter((visible, offset) ->
                LegacyColors.render(visible, LegacyColors.styleAt(nameField.value(), offset)));

        idField = add(new UiTextField(draft.vanillaItem, 48));
        idField.box().setResponder(value -> draft.vanillaItem = value.trim().toLowerCase(Locale.ROOT));

        dyeField = add(new UiTextField(draft.leatherColor == CustomItem.NO_COLOR ? "" : hex(draft.leatherColor), 7));
        dyeField.box().setResponder(this::readDye);

        glint = add(new UiValueButton(UiValueButton.Kind.CYCLE,
                () -> draft.glint.name().toLowerCase(Locale.ROOT), () -> {
                    CustomItem.Glint[] values = CustomItem.Glint.values();
                    draft.glint = values[(draft.glint.ordinal() + 1) % values.length];
                }));
        rgb = add(new UiToggle(() -> draft.rgb, on -> {
            draft.rgb = on;
            if (on) {
                draft.leatherColor = CustomItem.NO_COLOR;
                dyeField.value("");
            }
        }));

        reset = add(new UiButton("Reset", UiButton.Variant.GHOST, () -> {
            draft = new CustomItem();
            CustomItemStore.remove(key);
            nameField.value("");
            idField.value("");
            dyeField.value("");
        }));
        close = add(new UiButton("Close", UiButton.Variant.SECONDARY, this::onClose));
        save = add(new UiButton("Save", UiButton.Variant.PRIMARY, () -> {
            CustomItemStore.put(key, draft);
            onClose();
        }));
    }

    @Override
    protected void init() {
        addWidget(nameField.box());
        addWidget(idField.box());
        addWidget(dyeField.box());
    }

    /** A blank or unparsable dye box simply means "no dye", rather than an error. */
    private void readDye(String value) {
        String text = value.trim().replace("#", "");
        if (text.isEmpty()) {
            draft.leatherColor = CustomItem.NO_COLOR;
            return;
        }
        try {
            draft.leatherColor = Integer.parseInt(text, 16) & 0xFFFFFF;
            draft.rgb = false; // a fixed dye and the cycle cannot both apply
        } catch (NumberFormatException ignored) {
            // keep typing; the value stays whatever it last parsed to
        }
    }

    // ---- Drawing ---------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float h = PAD + ROWS_H + 12 + Theme.BUTTON_H_SMALL + PAD;
        float x = Math.round((this.width - PANEL_W) / 2f);
        float y = Math.round((this.height - h) / 2f);
        UiPanel.panel(ctx, x, y, PANEL_W, h);

        drawPreview(ctx, x + PAD, y + PAD);

        float labelX = x + PAD + PREVIEW + PAD;
        float left = labelX + LABEL_W;
        float right = x + PANEL_W - PAD;
        float row = y + PAD;
        float step = ROW_H + GAP;

        label(ctx, "Name", labelX, row);
        nameField.bounds(left, row, right - left - BADGE - 4, ROW_H).render(ctx, mouseX, mouseY);
        help.bounds(right - BADGE, row + (ROW_H - BADGE) / 2f, BADGE, BADGE).render(ctx, mouseX, mouseY);

        label(ctx, "Id", labelX, row + step);
        idField.bounds(left, row + step, right - left, ROW_H).render(ctx, mouseX, mouseY);

        label(ctx, "Glint", labelX, row + step * 2);
        glint.bounds(left, row + step * 2, right - left, ROW_H).render(ctx, mouseX, mouseY);

        boolean leather = leatherChosen();
        dyeField.box().visible = leather;
        dyeField.box().active = leather;
        dyeField.visible(leather);
        rgb.visible(leather);
        if (leather) {
            label(ctx, "Dye", labelX, row + step * 3);
            float swatch = ROW_H;
            drawDyeSwatch(ctx, left, row + step * 3, swatch);
            dyeField.bounds(left + swatch + 4, row + step * 3, right - left - swatch - 4, ROW_H).render(ctx, mouseX, mouseY);
            label(ctx, "RGB", labelX, row + step * 4);
            rgb.at(left, row + step * 4 + (ROW_H - Theme.SWITCH_H) / 2f).render(ctx, mouseX, mouseY);
        }

        // Footer: reset on the left, close and save on the right.
        float fy = y + h - PAD - Theme.BUTTON_H_SMALL;
        reset.bounds(x + PAD, fy, reset.preferredWidth(), Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
        float saveW = Math.max(50, save.preferredWidth());
        save.bounds(right - saveW, fy, saveW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
        float closeW = close.preferredWidth();
        close.bounds(right - saveW - 4 - closeW, fy, closeW, Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);

        if (help.contains(mouseX, mouseY)) drawHelpPanel(ctx, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphicsExtractor ctx, float left, float top) {
        UiPanel.inset(ctx, left, top, PREVIEW, PREVIEW, Theme.RADIUS_CARD);
        Draw.circle(ctx, left + PREVIEW / 2f, top + PREVIEW / 2f, 26f, Theme.GLOW_SOFT);
        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(left + PREVIEW / 2f - 24, top + PREVIEW / 2f - 24);
        pose.scale(3f, 3f);
        ctx.item(preview(), 0, 0);
        pose.popMatrix();
    }

    private void drawDyeSwatch(GuiGraphicsExtractor ctx, float x, float y, float size) {
        if (draft.rgb) {
            float t = (float) (Anim.nowMs() % 6000 / 6000.0);
            Draw.roundRectH(ctx, x, y, size, size, Theme.RADIUS_CONTROL,
                    0xFF000000 | java.awt.Color.HSBtoRGB(t, 1f, 1f), 0xFF000000 | java.awt.Color.HSBtoRGB(t + 0.33f, 1f, 1f));
        } else if (draft.leatherColor != CustomItem.NO_COLOR) {
            Draw.roundRect(ctx, x, y, size, size, Theme.RADIUS_CONTROL, 0xFF000000 | draft.leatherColor);
        } else {
            Draw.roundRect(ctx, x, y, size, size, Theme.RADIUS_CONTROL, Theme.INSET);
            Draw.line(ctx, x + 4, y + size - 4, x + size - 4, y + 4, 1.2f, Theme.TEXT_3);
        }
        Draw.outline(ctx, x, y, size, size, Theme.RADIUS_CONTROL, 1f, 0x2EFFFFFF);
    }

    /** The code reference, on its own light background so every colour stays readable. */
    private void drawHelpPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        UiText.Style style = UiText.Style.LABEL;
        int pad = 8;
        int lineHeight = 11;

        // The codes line up in a column of their own, measured from the widest name. A padded
        // string would not work: bold and italic names are wider than plain ones.
        int nameColumn = 0;
        int codeColumn = 0;
        for (HelpLine line : HELP) {
            nameColumn = Math.max(nameColumn, UiText.width(line.name(), style));
            codeColumn = Math.max(codeColumn, UiText.width(line.codes(), style));
        }
        int gap = 12;
        float width = pad + nameColumn + gap + codeColumn + pad;
        float height = HELP.size() * lineHeight + pad * 2 - 3;

        // Prefer below-right of the cursor, but never off the screen.
        float left = Math.max(4, Math.min(mouseX + 10, this.width - width - 4));
        float top = Math.max(4, Math.min(mouseY + 10, this.height - height - 4));

        ctx.nextStratum();
        Draw.roundRect(ctx, left, top, width, height, 6f, HELP_BG);
        Draw.outline(ctx, left, top, width, height, 6f, 1f, HELP_EDGE);

        float y = top + pad;
        for (HelpLine line : HELP) {
            UiText.draw(ctx, line.name().getVisualOrderText(), style, left + pad, y, HELP_TEXT, false);
            UiText.draw(ctx, line.codes().getVisualOrderText(), style, left + pad + nameColumn + gap, y,
                    HELP_TEXT, line.shadow());
            y += lineHeight;
        }
    }

    private static void label(GuiGraphicsExtractor ctx, String text, float x, float rowY) {
        UiText.draw(ctx, text, UiText.Style.LABEL, x, UiText.centerY(UiText.Style.LABEL, rowY, ROW_H), Theme.TEXT_2);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** The stack as it would look with the pending settings, rebuilt each frame. */
    private ItemStack preview() {
        ItemStack copy = original.copy();
        CustomItemApplier.applyTo(copy, draft);
        return copy;
    }

    /** True when the look in use is leather armour, the only case where a dye does anything. */
    private boolean leatherChosen() {
        Item look = CustomItemApplier.resolveItem(draft.vanillaItem);
        Item effective = look != null ? look : original.getItem();
        return BuiltInRegistries.ITEM.getKey(effective).getPath().startsWith("leather_");
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The "!" beside the name field; hovering it lists the codes without cluttering the panel. */
    private static final class HelpBadge extends UiWidget {
        @Override
        protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
            float r = w / 2f;
            if (hv > 0.01f) Draw.shadow(ctx, x, y, w, h, r, 4f, 0f, Draw.withAlpha(Theme.GLOW, hv));
            Draw.roundRect(ctx, x, y, w, h, r, Draw.mix(Theme.INSET, Theme.HOVER, hv));
            Draw.outline(ctx, x, y, w, h, r, 1f, Draw.mix(Theme.LINE_STRONG, Theme.ACCENT, hv));
            UiText.drawCentered(ctx, "!", UiText.Style.LABEL, x + w / 2f,
                    UiText.centerY(UiText.Style.LABEL, y, h), Draw.mix(Theme.TEXT_2, Theme.CYAN, hv));
        }
    }
}
