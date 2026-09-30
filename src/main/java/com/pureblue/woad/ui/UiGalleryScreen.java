package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Every component of the design system on one screen, for checking them in game.
 *
 * <p>Development only: it is reachable solely from a development environment (see
 * {@code WoadClient}), never from a released jar.
 */
public class UiGalleryScreen extends UiScreen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 264;

    private boolean switchA = true;
    private boolean switchB = false;
    private int memory = 100;
    private int tab = 0;
    private int texture = 0;
    private boolean listening = false;
    private String backend = "Ollama";

    private final UiButton primary = add(new UiButton("Save", UiButton.Variant.PRIMARY, () -> {}));
    private final UiButton secondary = add(new UiButton("Reset colour", UiButton.Variant.SECONDARY, () -> {}));
    private final UiButton ghost = add(new UiButton("Cancel", UiButton.Variant.GHOST, () -> {}));
    private final UiButton disabled = add((UiButton) new UiButton("Open", UiButton.Variant.PRIMARY, () -> {}).enabled(false));
    private final UiButton swatch = add(new UiButton("Border", UiButton.Variant.SECONDARY, () -> {}).swatch(0x38BDF8));
    private final UiToggle toggleA = add(new UiToggle(() -> switchA, v -> switchA = v));
    private final UiToggle toggleB = add(new UiToggle(() -> switchB, v -> switchB = v));
    private final UiToggle toggleOff = add((UiToggle) new UiToggle(() -> false, v -> {}).enabled(false));
    private final UiSlider slider = add(new UiSlider(0, 500, 10, () -> memory, v -> memory = v));
    private final UiSegmented tabs = add(new UiSegmented(List.of("General", "Chat", "Advanced"), () -> tab, i -> tab = i));
    private final UiSegmented lavaWater = add(new UiSegmented(List.of("Lava", "Water"), () -> texture, i -> texture = i));
    private final UiValueButton cycle = add(new UiValueButton(UiValueButton.Kind.CYCLE, () -> backend,
            () -> backend = switch (backend) { case "Ollama" -> "Claude"; case "Claude" -> "OpenAI"; default -> "Ollama"; }));
    private final UiValueButton edit = add(new UiValueButton(UiValueButton.Kind.EDIT, () -> "ai", () -> {}));
    private final UiValueButton key = add(new UiValueButton(UiValueButton.Kind.KEY, () -> "LEFT ALT", () -> listening = !listening));
    private final UiTextField field = add(new UiTextField("warp dungeon_hub", 256).placeholder("Command"));
    private final UiTextField badField = add(new UiTextField("#FF80", 7).invalid(true));
    private final UiScroll scroll = addScroll(new UiScroll());
    private final Anim.Toggle[] slotHover = new Anim.Toggle[10];

    public UiGalleryScreen() {
        super(Component.literal("Woad UI"));
        for (int i = 0; i < slotHover.length; i++) slotHover[i] = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);
    }

    @Override
    protected void init() {
        addWidget(field.box());
        addWidget(badField.box());
    }

    private static void caption(GuiGraphicsExtractor ctx, String text, float x, float y) {
        UiText.draw(ctx, text, UiText.Style.LABEL, x, y, Theme.TEXT_3);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float x = (this.width - PANEL_W) / 2f;
        float y = (this.height - PANEL_H) / 2f;
        UiPanel.panel(ctx, x, y, PANEL_W, PANEL_H);

        // Header
        UiText.draw(ctx, "Woad", UiText.Style.TITLE, x + 14, UiText.centerY(UiText.Style.TITLE, y, 30), Theme.TEXT);
        String scale = "GUI scale " + Draw.guiScale();
        UiPanel.pill(ctx, scale, x + PANEL_W - 14 - UiText.width(scale, UiText.Style.LABEL) - 12, y + 9, Theme.TEXT_2, Theme.NEUTRAL_TINT);
        UiPanel.hairline(ctx, x, x + PANEL_W, y + 30);

        float c1 = x + 14;
        float c2 = x + 148;
        float c3 = x + 284;
        float top = y + 40;
        float colW = 122;

        // Column 1 — buttons, switches, type
        caption(ctx, "BUTTONS", c1, top);
        primary.bounds(c1, top + 12, 58, Theme.BUTTON_H).render(ctx, mx, my);
        secondary.bounds(c1 + 62, top + 12, 60, Theme.BUTTON_H).render(ctx, mx, my);
        ghost.bounds(c1, top + 34, 58, Theme.BUTTON_H).render(ctx, mx, my);
        disabled.bounds(c1 + 62, top + 34, 60, Theme.BUTTON_H).render(ctx, mx, my);
        swatch.bounds(c1, top + 56, swatch.preferredWidth(), Theme.BUTTON_H_SMALL).render(ctx, mx, my);

        caption(ctx, "SWITCHES", c1, top + 82);
        String[] names = {"Party chat", "Guild chat", "All chat"};
        UiToggle[] toggles = {toggleA, toggleB, toggleOff};
        for (int i = 0; i < 3; i++) {
            float ry = top + 95 + i * 17;
            UiText.draw(ctx, names[i], UiText.Style.LABEL, c1, UiText.centerY(UiText.Style.LABEL, ry, Theme.SWITCH_H),
                    toggles[i].isEnabled() ? Theme.TEXT : Theme.TEXT_3);
            toggles[i].at(c1 + colW - Theme.SWITCH_W, ry).render(ctx, mx, my);
        }

        caption(ctx, "TYPE", c1, top + 152);
        UiText.draw(ctx, "AI Chat", UiText.Style.HEADING, c1, top + 165, Theme.TEXT);
        UiText.drawWrapped(ctx, "Answers in chat when a player writes your trigger command. Works with a local Ollama model or an API key.",
                UiText.Style.BODY, c1, top + 181, (int) colW, Theme.TEXT_2);

        // Column 2 — inputs, choices, progress
        caption(ctx, "INPUTS", c2, top);
        field.bounds(c2, top + 12, colW, Theme.FIELD_H).render(ctx, mx, my);
        badField.bounds(c2, top + 34, colW, Theme.FIELD_H).render(ctx, mx, my);
        UiText.draw(ctx, "Memory", UiText.Style.LABEL, c2, top + 60, Theme.TEXT);
        String mem = String.valueOf(memory);
        UiText.draw(ctx, mem, UiText.Style.LABEL, c2 + colW - UiText.width(mem, UiText.Style.LABEL), top + 60, Theme.CYAN);
        slider.bounds(c2, top + 69, colW, 12).render(ctx, mx, my);

        caption(ctx, "CHOICES", c2, top + 88);
        tabs.bounds(c2, top + 100, colW, 16).render(ctx, mx, my);
        cycle.bounds(c2, top + 121, 70, 16).render(ctx, mx, my);
        edit.bounds(c2 + 74, top + 121, colW - 74, 16).render(ctx, mx, my);
        key.listening(listening).bounds(c2, top + 142, 56, 16).render(ctx, mx, my);
        lavaWater.bounds(c2 + 60, top + 142, colW - 60, 16).render(ctx, mx, my);

        caption(ctx, "PROGRESS", c2, top + 168);
        UiText.draw(ctx, "Hidden Jerry", UiText.Style.BODY, c2, top + 181, Theme.TEXT_2);
        float jerry = (float) ((Anim.nowMs() / 1000.0 % 360) / 360.0);
        UiPanel.progress(ctx, c2, top + 192, colW, 4, Math.max(0.35f, jerry), Theme.FILL, Theme.CYAN);
        UiPanel.progress(ctx, c2, top + 201, colW, 4, 1f, Theme.OK, Theme.OK);

        // Column 3 — list, slots, status
        caption(ctx, "LIST", c3, top);
        UiPanel.inset(ctx, c3, top + 12, colW, 70, Theme.RADIUS_CONTROL);
        float off = scroll.begin(ctx, c3 + 2, top + 14, colW - 4, 66);
        String[] cmds = {"/ah", "/bz", "/storage", "/wardrobe", "/pets", "/equipment", "/craft", "/warp dungeon_hub", "/is", "/hub"};
        float ry = top + 14 - off;
        for (int i = 0; i < cmds.length; i++) {
            boolean over = mx >= c3 + 2 && mx < c3 + colW - 6 && my >= ry && my < ry + 13 && my >= top + 14 && my < top + 80;
            if (i == 2) Draw.roundRectH(ctx, c3 + 3, ry + 0.5f, colW - 12, 12, 3f, Theme.SELECTED_FROM, Theme.SELECTED_TO);
            else if (over) Draw.roundRect(ctx, c3 + 3, ry + 0.5f, colW - 12, 12, 3f, Theme.HOVER);
            UiText.draw(ctx, cmds[i], UiText.Style.BODY, c3 + 8, UiText.centerY(UiText.Style.BODY, ry, 13),
                    i == 2 || over ? Theme.TEXT : Theme.TEXT_2);
            ry += 13;
        }
        scroll.end(ctx, cmds.length * 13, mx, my);

        caption(ctx, "SLOTS", c3, top + 90);
        ItemStack[] items = {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.CHEST), ItemStack.EMPTY,
                new ItemStack(Items.ENDER_PEARL), ItemStack.EMPTY, new ItemStack(Items.BONE),
                ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY, ItemStack.EMPTY};
        for (int i = 0; i < items.length; i++) {
            float sx = c3 + (i % 5) * 24.5f;
            float sy = top + 102 + (i / 5) * 24.5f;
            boolean over = mx >= sx && mx < sx + 20 && my >= sy && my < sy + 20;
            UiPanel.slot(ctx, sx, sy, 20, slotHover[i].update(over), !items[i].isEmpty());
            if (!items[i].isEmpty()) ctx.item(items[i], Math.round(sx + 2), Math.round(sy + 2));
            if (over) tooltip.offer("slot" + i, items[i].isEmpty()
                    ? List.of("Empty slot", "Click to add a button")
                    : List.of(items[i].getHoverName().getString(), "Left-click runs it, right-click edits it"));
        }

        caption(ctx, "STATUS", c3, top + 160);
        float pw = UiPanel.pill(ctx, "ACTIVE", c3, top + 172, Theme.OK, Theme.OK_TINT);
        UiPanel.pill(ctx, "INACTIVE", c3 + pw + 4, top + 172, Theme.TEXT_2, Theme.NEUTRAL_TINT);
        UiPanel.dot(ctx, c3 + 4, top + 196, true, Theme.CYAN);
        UiText.draw(ctx, "Jerry Timer", UiText.Style.BODY, c3 + 12, UiText.centerY(UiText.Style.BODY, top + 191, 10), Theme.TEXT);
        // In capture mode nothing is under the pointer, so show a tooltip regardless to check it.
        if ("shot".equals(System.getenv("WOAD_GALLERY"))) {
            tooltip.offer("demo", List.of("Realtek PCIe 2.5GbE Family Controller",
                    "Click to switch connection.", "Applies to joining servers and pings, not to login."));
        }
        UiPanel.dot(ctx, c3 + 4, top + 209, false, Theme.CYAN);
        UiText.draw(ctx, "Loadout Keybinds", UiText.Style.BODY, c3 + 12, UiText.centerY(UiText.Style.BODY, top + 204, 10), Theme.TEXT_2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Automated capture (development only) ----------------------------------------------------

    /** GUI scales photographed in turn with WOAD_GALLERY=shot. */
    private static final int[] SHOT_SCALES = {1, 2, 3, 4};
    private int shotIndex = -1;
    private double phaseStart = Anim.nowMs();
    private int originalScale = -1;

    /**
     * With WOAD_GALLERY=shot, the gallery photographs itself at GUI scale 1, 2, 3 and 4, then puts
     * the scale back and closes the game — so sharpness at every scale can be checked without
     * anyone at the keyboard. The option is never saved.
     */
    @Override
    public void tick() {
        if (!"shot".equals(System.getenv("WOAD_GALLERY")) || this.minecraft == null) return;
        double age = Anim.nowMs() - phaseStart;
        if (shotIndex < 0) {
            originalScale = this.minecraft.options.guiScale().get();
            nextScale(0);
            return;
        }
        if (age < 1400) return; // let the new rasterisation and the opening animation settle
        if (shotIndex < SHOT_SCALES.length) {
            net.minecraft.client.Screenshot.grab(this.minecraft.gameDirectory,
                    "woad_gallery_x" + Draw.guiScale() + ".png",
                    this.minecraft.getMainRenderTarget(), 1, message -> {});
            nextScale(shotIndex + 1);
        } else if (age > 1800) {
            this.minecraft.options.guiScale().set(originalScale);
            this.minecraft.resizeGui();
            this.minecraft.stop();
        }
    }

    private void nextScale(int index) {
        shotIndex = index;
        phaseStart = Anim.nowMs();
        if (index < SHOT_SCALES.length) {
            this.minecraft.options.guiScale().set(SHOT_SCALES[index]);
            this.minecraft.resizeGui();
        }
    }
}
