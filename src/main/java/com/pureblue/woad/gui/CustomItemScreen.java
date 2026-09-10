package com.pureblue.woad.gui;

import com.pureblue.woad.core.Woad;
import com.pureblue.woad.customitem.CustomItem;
import com.pureblue.woad.customitem.CustomItemApplier;
import com.pureblue.woad.customitem.CustomItemStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
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
 * so the panel shows the result rather than describing it. The dye row only appears once the chosen
 * id is leather armour, which keeps the screen down to what actually applies.
 */
public class CustomItemScreen extends Screen {

    private static final int OVERLAY   = 0xC8000000;
    private static final int BORDER    = 0xFF34343C;
    private static final int PANEL     = 0xFF1B1B20;
    private static final int WELL      = 0xFF141417;
    private static final int FIELD     = 0xFF26262C;
    private static final int FIELD_HOT = 0xFF32323C;
    private static final int TEXT      = 0xFFE8E8EC;
    private static final int TEXT_DIM  = 0xFF8B8B95;
    private static final int ACCENT    = Woad.ACCENT;

    private static final int PANEL_W   = 300;
    private static final int PANEL_H   = 168;
    private static final int PAD       = 12;
    private static final int PREVIEW   = 76;
    private static final int LABEL_W   = 34;
    private static final int BADGE_W   = 12;
    private static final int ROW_H     = 18;
    private static final int GAP       = 4;

    /**
     * The help panel is drawn by hand rather than as a vanilla tooltip.
     *
     * <p>Vanilla tooltips sit on a near-black background, where {@code &0} and {@code &1} are all
     * but invisible — useless for a colour reference. No flat background fixes this on its own: the
     * palette spans every luminance, so the best possible worst-case contrast is only 1.3.
     *
     * <p>What does work is the game's own answer: a light panel plus the text shadow. Dark codes
     * stand out against the panel, and light ones get a dark outline from their shadow, which
     * measures between 6 and 9 times the contrast of the glyph itself.
     */
    private static final int HELP_BG     = 0xFFC6C6C6;
    private static final int HELP_BORDER = 0xFF373737;
    private static final int HELP_TEXT   = 0xFF3F3F46;

    /**
     * A help line, split so each half can be drawn differently: the name is flat text without a
     * shadow, the codes carry one because it is what keeps light colours readable.
     */
    private record HelpLine(Component name, Component codes) {}

    private static final List<HelpLine> HELP = buildHelp();

    private static List<HelpLine> buildHelp() {
        List<HelpLine> lines = new ArrayList<>();

        // All sixteen colours on one line, each code printed in the colour it produces.
        MutableComponent colours = Component.empty();
        for (char code : "0123456789abcdef".toCharArray()) {
            colours.append(Component.literal("&" + code).withStyle(ChatFormatting.getByCode(code)));
        }
        lines.add(new HelpLine(Component.literal("Colour"), colours));

        // One line per kind of formatting, the name written in the style it produces.
        lines.add(styled("Bold", 'l'));
        lines.add(styled("Italic", 'o'));
        lines.add(styled("Underline", 'n'));
        lines.add(styled("Strike", 'm'));
        // "Magic" stays plain: applying it scrambles the glyphs, which reads as a rendering bug
        // rather than as an example — and "Reset" has no look of its own to show.
        lines.add(new HelpLine(Component.literal("Magic"), Component.literal("&k")));
        lines.add(new HelpLine(Component.literal("Reset"), Component.literal("&r")));
        return lines;
    }

    /** A help line whose name demonstrates the very style it names. */
    private static HelpLine styled(String name, char code) {
        ChatFormatting format = ChatFormatting.getByCode(code);
        return new HelpLine(Component.literal(name).withStyle(format),
                Component.literal("&" + code).withStyle(format));
    }

    private final ItemStack original;
    private final String key;
    private CustomItem draft;

    private EditBox nameField;
    private EditBox idField;
    private EditBox dyeField;

    private boolean helpHovered = false;

    private final int[] glintBox = new int[4];
    private final int[] rgbBox = new int[4];
    private final int[] saveBox = new int[4];
    private final int[] resetBox = new int[4];
    private final int[] closeBox = new int[4];

    public CustomItemScreen(ItemStack held) {
        super(Component.literal("Custom item"));
        this.original = held.copy();
        this.key = CustomItemStore.keyOf(held);
        CustomItem saved = CustomItemStore.get(key);
        this.draft = saved == null ? new CustomItem() : saved.copy();
    }

    /** Left edge of the label column: right of the preview, never over it. */
    private int labelLeft() {
        return (this.width - PANEL_W) / 2 + PAD + PREVIEW + PAD;
    }

    /** Left edge of the value column. */
    private int fieldLeft() {
        return labelLeft() + LABEL_W;
    }

    private int fieldRight() {
        return (this.width - PANEL_W) / 2 + PANEL_W - PAD;
    }

    @Override
    protected void init() {
        int left = fieldLeft();
        int top = (this.height - PANEL_H) / 2 + PAD;
        int width = fieldRight() - left;

        // The name field gives up a sliver for the "!" help badge beside it.
        nameField = field(left, top, width - BADGE_W - 2, draft.name, 64, value -> draft.name = value);
        nameField.addFormatter((visible, offset) ->
                LegacyColors.render(visible, LegacyColors.styleAt(nameField.getValue(), offset)));

        idField = field(left, top + (ROW_H + GAP), width, draft.vanillaItem, 48,
                value -> draft.vanillaItem = value.trim().toLowerCase(Locale.ROOT));
        dyeField = field(left, top + (ROW_H + GAP) * 3, width,
                draft.leatherColor == CustomItem.NO_COLOR ? "" : hex(draft.leatherColor), 7,
                this::readDye);
    }

    private EditBox field(int x, int y, int width, String value, int maxLength,
                          java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, x, y + 1, width, ROW_H - 2, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(onChange);
        return this.addRenderableWidget(box);
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
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, OVERLAY);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        ctx.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, BORDER);
        ctx.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL);

        drawPreview(ctx, x + PAD, y + PAD);

        int left = fieldLeft();
        int right = fieldRight();
        int row = y + PAD;

        label(ctx, left, row, "Name");
        drawHelpBadge(ctx, right - BADGE_W, row, mouseX, mouseY);
        label(ctx, left, row + (ROW_H + GAP), "Id");

        boolean leather = leatherChosen();
        toggle(ctx, glintBox, left, row + (ROW_H + GAP) * 2, right, "Glint",
                draft.glint.name().toLowerCase(Locale.ROOT), mouseX, mouseY);

        dyeField.visible = leather;
        dyeField.active = leather;
        if (leather) {
            label(ctx, left, row + (ROW_H + GAP) * 3, "Dye");
            toggle(ctx, rgbBox, left, row + (ROW_H + GAP) * 4, right, "RGB",
                    draft.rgb ? "on" : "off", mouseX, mouseY);
        } else {
            java.util.Arrays.fill(rgbBox, 0);
        }

        drawFooter(ctx, x, y, mouseX, mouseY);
        // Widgets before the help so the text fields sit above the panel, help above everything.
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (helpHovered) drawHelpPanel(ctx, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphicsExtractor ctx, int left, int top) {
        ctx.fill(left, top, left + PREVIEW, top + PREVIEW, WELL);
        ctx.fill(left, top, left + PREVIEW, top + 1, BORDER);
        ctx.fill(left, top + PREVIEW - 1, left + PREVIEW, top + PREVIEW, BORDER);
        ctx.fill(left, top, left + 1, top + PREVIEW, BORDER);
        ctx.fill(left + PREVIEW - 1, top, left + PREVIEW, top + PREVIEW, BORDER);

        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(left + PREVIEW / 2f - 24, top + PREVIEW / 2f - 24);
        pose.scale(3f, 3f);
        ctx.item(preview(), 0, 0);
        pose.popMatrix();
    }

    /** The "!" beside the name field; hovering it lists the codes without cluttering the panel. */
    private void drawHelpBadge(GuiGraphicsExtractor ctx, int left, int y, int mouseX, int mouseY) {
        helpHovered = mouseX >= left && mouseX <= left + BADGE_W
                && mouseY >= y && mouseY <= y + ROW_H;
        ctx.fill(left, y, left + BADGE_W, y + ROW_H, helpHovered ? FIELD_HOT : FIELD);
        ctx.centeredText(this.font, Component.literal("!"), left + BADGE_W / 2, y + 5,
                helpHovered ? ACCENT : TEXT_DIM);
    }

    /** The code reference, on its own light background so every colour stays readable. */
    private void drawHelpPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int pad = 6;
        int lineHeight = this.font.lineHeight + 2;

        // The codes line up in a column of their own, measured from the widest name. A padded
        // string would not work: bold and italic names are wider than plain ones.
        int nameColumn = 0;
        int codeColumn = 0;
        for (HelpLine line : HELP) {
            nameColumn = Math.max(nameColumn, this.font.width(line.name()));
            codeColumn = Math.max(codeColumn, this.font.width(line.codes()));
        }
        int gap = 10;
        int width = pad + nameColumn + gap + codeColumn + pad;
        int height = HELP.size() * lineHeight + pad * 2;

        // Prefer below-right of the cursor, but never off the screen.
        int left = Math.max(2, Math.min(mouseX + 10, this.width - width - 2));
        int top = Math.max(2, Math.min(mouseY + 10, this.height - height - 2));

        ctx.fill(left - 1, top - 1, left + width + 1, top + height + 1, HELP_BORDER);
        ctx.fill(left, top, left + width, top + height, HELP_BG);

        int y = top + pad;
        for (HelpLine line : HELP) {
            // No shadow behind the names: a dark glyph over its own dark shadow just smudges.
            ctx.text(this.font, line.name(), left + pad, y, HELP_TEXT, false);
            // Shadow on the codes: it is what keeps the light colours readable on a light panel.
            ctx.text(this.font, line.codes(), left + pad + nameColumn + gap, y, HELP_TEXT, true);
            y += lineHeight;
        }
    }

    private void label(GuiGraphicsExtractor ctx, int left, int y, String text) {
        ctx.text(this.font, Component.literal(text), labelLeft(), y + 5, TEXT_DIM, false);
    }

    /** A row that cycles on click rather than being typed into. */
    private void toggle(GuiGraphicsExtractor ctx, int[] box, int left, int y, int right,
                        String text, String value, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + ROW_H;
        ctx.fill(left, y, right, y + ROW_H, hovered ? FIELD_HOT : FIELD);
        if (hovered) ctx.fill(left, y, left + 1, y + ROW_H, ACCENT);
        label(ctx, left, y, text);
        ctx.text(this.font, Component.literal(value), left + 6, y + 5, TEXT, false);

        box[0] = left;
        box[1] = y;
        box[2] = right;
        box[3] = y + ROW_H;
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
        int footer = y + PANEL_H - PAD - SmallButton.HEIGHT;
        int width = (PANEL_W - PAD * 2 - GAP * 2) / 3;
        SmallButton.draw(ctx, saveBox, x + PAD, footer, width, "Save", mouseX, mouseY);
        SmallButton.draw(ctx, resetBox, x + PAD + width + GAP, footer, width, "Reset", mouseX, mouseY);
        SmallButton.draw(ctx, closeBox, x + PAD + (width + GAP) * 2, footer, width, "Close", mouseX, mouseY);
    }

    // ---- Input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            if (inside(glintBox, mx, my)) {
                CustomItem.Glint[] values = CustomItem.Glint.values();
                draft.glint = values[(draft.glint.ordinal() + 1) % values.length];
                return true;
            }
            if (inside(rgbBox, mx, my)) {
                draft.rgb = !draft.rgb;
                if (draft.rgb) {
                    draft.leatherColor = CustomItem.NO_COLOR;
                    dyeField.setValue("");
                }
                return true;
            }
            if (SmallButton.hit(saveBox, mx, my)) {
                CustomItemStore.put(key, draft);
                onClose();
                return true;
            }
            if (SmallButton.hit(resetBox, mx, my)) {
                draft = new CustomItem();
                CustomItemStore.remove(key);
                nameField.setValue("");
                idField.setValue("");
                dyeField.setValue("");
                return true;
            }
            if (SmallButton.hit(closeBox, mx, my)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
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

    private static boolean inside(int[] box, double mx, double my) {
        return box[2] > box[0] && mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3];
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
