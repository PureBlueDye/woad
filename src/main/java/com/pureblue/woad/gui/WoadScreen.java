package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.core.setting.BooleanSetting;
import com.pureblue.woad.core.setting.KeybindSetting;
import com.pureblue.woad.core.setting.IntSetting;
import com.pureblue.woad.core.setting.ModeSetting;
import com.pureblue.woad.core.setting.Setting;
import com.pureblue.woad.core.setting.StringSetting;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's configuration menu: a centered, flat, dark panel with a feature list on the left and
 * the selected feature's toggle + settings on the right. Deliberately minimal and sober.
 */
public class WoadScreen extends Screen {

    // ---- Palette (sober dark theme) -------------------------------------------------------
    private static final int OVERLAY    = 0xC8000000;
    private static final int BORDER     = 0xFF34343C;
    private static final int PANEL      = 0xFF1B1B20;
    private static final int HEADER     = 0xFF202028;
    private static final int SIDEBAR    = 0xFF161619;
    private static final int ROW        = 0xFF202027;
    private static final int ROW_HOVER  = 0xFF2A2A33;
    private static final int ROW_SEL    = 0xFF2E2E39;
    private static final int TEXT       = 0xFFE8E8EC;
    private static final int TEXT_DIM   = 0xFF8B8B95;
    private static final int DIVIDER    = 0xFF2A2A31;
    private static final int TOGGLE_OFF = 0xFF3A3A44;
    private static final int KNOB       = 0xFFF0F0F2;
    private static final int DOT_OFF    = 0xFF55555F;
    private static final int ACCENT     = Woad.ACCENT;
    private static final int TITLE      = 0xFF000000 | Woad.LIGHT_BLUE;  // light-blue brand title

    // ---- Layout constants -----------------------------------------------------------------
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 264;
    private static final int HEADER_H = 36;
    private static final int SIDEBAR_W = 134;
    private static final int PAD = 14;
    private static final int FEATURE_ROW_H = 24;
    private static final int SWITCH_W = 26;
    private static final int SWITCH_H = 13;

    private int selected = 0;

    // Hit-boxes captured during render, consumed by mouseClicked.
    private final List<int[]> featureRows = new ArrayList<>();   // {x1,y1,x2,y2}
    private final int[] masterSwitch = new int[4];
    private final int[] openButton = new int[4];
    private boolean openButtonShown = false;
    private final List<int[]> settingSwitches = new ArrayList<>();
    private final List<BooleanSetting> settingRefs = new ArrayList<>();
    private final List<int[]> keybindButtons = new ArrayList<>();
    private final List<KeybindSetting> keybindRefs = new ArrayList<>();
    private final List<int[]> valueButtons = new ArrayList<>();
    private final List<Setting<?>> valueRefs = new ArrayList<>();
    private KeybindSetting listeningKeybind = null;
    private int settingsScroll = 0;
    private int settingsMaxScroll = 0;

    public WoadScreen() {
        super(Component.literal(Woad.NAME));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Screen.render() already renders (and blurs) the background once; calling renderBackground
        // again here would trigger "Can only blur once per frame". So let super do it, then overlay.
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, OVERLAY);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        // Outer panel + header.
        panel(context, x, y, x + PANEL_W, y + PANEL_H, PANEL);
        fillRound(context, x + 1, y + 1, x + PANEL_W - 1, y + HEADER_H, HEADER);

        context.text(this.font, Woad.NAME, x + PAD, y + 9, TITLE, false);
        // Accent underline under the title.
        context.fill(x + PAD, y + 21, x + PAD + this.font.width(Woad.NAME), y + 22, ACCENT);

        int bodyTop = y + HEADER_H;
        renderSidebar(context, x, bodyTop, mouseX, mouseY);
        renderContent(context, x + SIDEBAR_W, bodyTop, x + PANEL_W, y + PANEL_H, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphicsExtractor context, int x, int top, int mouseX, int mouseY) {
        int bottom = top + (PANEL_H - HEADER_H);
        context.fill(x + 1, top, x + SIDEBAR_W, bottom - 1, SIDEBAR);

        featureRows.clear();
        List<Feature> features = FeatureManager.getFeatures();
        for (int i = 0; i < features.size(); i++) {
            Feature feature = features.get(i);
            int rx1 = x + 7;
            int ry1 = top + 8 + i * FEATURE_ROW_H;
            int rx2 = x + SIDEBAR_W - 7;
            int ry2 = ry1 + FEATURE_ROW_H - 4;
            featureRows.add(new int[]{rx1, ry1, rx2, ry2});

            boolean hovered = inside(mouseX, mouseY, rx1, ry1, rx2, ry2);
            int bg = i == selected ? ROW_SEL : (hovered ? ROW_HOVER : ROW);
            fillRound(context, rx1, ry1, rx2, ry2, bg);
            if (i == selected) {
                context.fill(rx1, ry1, rx1 + 2, ry2, ACCENT); // accent left edge
            }

            // Status dot + name.
            int dotColor = feature.isEnabled() ? ACCENT : DOT_OFF;
            context.fill(rx1 + 8, ry1 + (FEATURE_ROW_H - 4) / 2 - 2, rx1 + 12, ry1 + (FEATURE_ROW_H - 4) / 2 + 2, dotColor);
            context.text(this.font, feature.getName(), rx1 + 18,
                    ry1 + (FEATURE_ROW_H - 4 - 8) / 2, i == selected ? TEXT : TEXT_DIM, false);
        }
    }

    private void renderContent(GuiGraphicsExtractor context, int x, int top, int right, int bottom, int mouseX, int mouseY) {
        settingSwitches.clear();
        settingRefs.clear();
        keybindButtons.clear();
        keybindRefs.clear();
        valueButtons.clear();
        valueRefs.clear();

        List<Feature> features = FeatureManager.getFeatures();
        if (features.isEmpty()) return;
        Feature feature = features.get(Math.min(selected, features.size() - 1));

        // Feature title.
        context.text(this.font, feature.getName(), x + PAD, top + PAD, TEXT, false);

        // Master toggle (only for features that have one).
        if (feature.hasToggle()) {
            int swX = right - PAD - SWITCH_W;
            int swY = top + PAD - 2;
            drawSwitch(context, swX, swY, feature.isEnabled());
            masterSwitch[0] = swX; masterSwitch[1] = swY; masterSwitch[2] = swX + SWITCH_W; masterSwitch[3] = swY + SWITCH_H;
        } else {
            masterSwitch[0] = masterSwitch[1] = masterSwitch[2] = masterSwitch[3] = -1; // disabled
        }

        // Features with a custom panel (e.g. Custom Lava) draw their own UI and nothing else.
        if (feature.hasCustomPanel()) {
            feature.renderPanel(context, this, x, top + PAD + 16, right, bottom, mouseX, mouseY);
            return;
        }

        // Description (wrapped).
        int textW = right - x - PAD * 2;
        int dy = top + PAD + 14;
        for (FormattedCharSequence line : this.font.split(Component.literal(feature.getDescription()), textW)) {
            context.text(this.font, line, x + PAD, dy, TEXT_DIM, false);
            dy += 11;
        }

        // "Open" button for features with a separate config screen (e.g. Inventory Buttons editor).
        openButtonShown = feature.hasConfigScreen();
        if (openButtonShown) {
            dy += 6;
            String label = "Open";
            int bw = this.font.width(label) + 18;
            int bh = 16;
            int bx = x + PAD;
            boolean en = feature.isEnabled();
            boolean hovered = inside(mouseX, mouseY, bx, dy, bx + bw, dy + bh);
            fillRound(context, bx, dy, bx + bw, dy + bh, !en ? ROW : (hovered ? ROW_HOVER : ROW_SEL));
            fillRound(context, bx, dy, bx + 2, dy + bh, ACCENT);
            context.text(this.font, label, bx + 9, dy + 4, en ? TEXT : DOT_OFF, false);
            openButton[0] = bx; openButton[1] = dy; openButton[2] = bx + bw; openButton[3] = dy + bh;
            dy += bh + 6;
        }

        // Feature-specific extra controls (e.g. Inventory Buttons style / border color).
        feature.renderExtra(context, this, x + PAD, dy, right - PAD, mouseX, mouseY);
        dy += feature.extraHeight();

        // Divider + settings (only when the feature actually has settings).
        if (feature.getSettings().isEmpty()) {
            settingsMaxScroll = 0;
            return;
        }
        dy += 6;
        context.fill(x + PAD, dy, right - PAD, dy + 1, DIVIDER);
        dy += 10;

        // Scrollable settings area, clipped to the panel.
        int settingsTop = dy;
        int visibleBottom = bottom - 6;
        int visibleHeight = visibleBottom - settingsTop;
        boolean enabledFeature = feature.isEnabled();

        context.enableScissor(x, settingsTop, right, visibleBottom);
        int sy = settingsTop - settingsScroll;
        for (Setting<?> setting : feature.getSettings()) {
            boolean visible = sy + 14 > settingsTop && sy < visibleBottom;
            if (setting instanceof BooleanSetting bool) {
                int sSwX = right - PAD - SWITCH_W;
                drawSwitch(context, sSwX, sy, bool.enabled() && enabledFeature, enabledFeature);
                if (visible) {
                    settingSwitches.add(new int[]{sSwX, sy, sSwX + SWITCH_W, sy + SWITCH_H});
                    settingRefs.add(bool);
                }
                context.text(this.font, setting.getName(), x + PAD, sy, enabledFeature ? TEXT : DOT_OFF, false);
                sy += 11;
                for (FormattedCharSequence line : this.font.split(Component.literal(setting.getDescription()), textW - SWITCH_W - 6)) {
                    context.text(this.font, line, x + PAD, sy, TEXT_DIM, false);
                    sy += 10;
                }
                sy += 10;
            } else if (setting instanceof KeybindSetting kb) {
                int bw = 52;
                int bh = 13;
                int kbX = x + PAD + 82; // close to the name rather than far right
                boolean listening = listeningKeybind == kb;
                boolean hovered = inside(mouseX, mouseY, kbX, sy, kbX + bw, sy + bh);
                fillRound(context, kbX, sy, kbX + bw, sy + bh, listening ? ACCENT : (hovered ? ROW_HOVER : ROW_SEL));
                String key = listening ? "..." : kb.keyName();
                context.text(this.font, key, kbX + bw / 2 - this.font.width(key) / 2, sy + 3,
                        enabledFeature ? TEXT : DOT_OFF, false);
                if (visible) {
                    keybindButtons.add(new int[]{kbX, sy, kbX + bw, sy + bh});
                    keybindRefs.add(kb);
                }
                context.text(this.font, setting.getName(), x + PAD, sy + 3, enabledFeature ? TEXT : DOT_OFF, false);
                sy += bh + 3;
            } else if (setting instanceof ModeSetting || setting instanceof StringSetting
                    || setting instanceof IntSetting) {
                // One row: the name on the left, the current value as a button on the right.
                String value;
                if (setting instanceof ModeSetting mode) {
                    value = mode.get();
                } else if (setting instanceof IntSetting number) {
                    value = String.valueOf(number.get());
                } else {
                    value = ((StringSetting) setting).display();
                }
                int bh = 14;
                int bw = Math.min(140, Math.max(46, this.font.width(value) + 12));
                int bx = right - PAD - bw;
                boolean hovered = inside(mouseX, mouseY, bx, sy, bx + bw, sy + bh);
                fillRound(context, bx, sy, bx + bw, sy + bh, hovered ? ROW_HOVER : ROW_SEL);
                String shown = trim(value, bw - 8);
                context.text(this.font, shown, bx + bw / 2 - this.font.width(shown) / 2,
                        sy + 3, enabledFeature ? TEXT : DOT_OFF, false);
                if (visible) {
                    valueButtons.add(new int[]{bx, sy, bx + bw, sy + bh});
                    valueRefs.add(setting);
                }

                context.text(this.font, setting.getName(), x + PAD, sy + 3, enabledFeature ? TEXT : DOT_OFF, false);
                sy += bh + 1;
                for (FormattedCharSequence line : this.font.split(Component.literal(setting.getDescription()), textW - 6)) {
                    context.text(this.font, line, x + PAD, sy, TEXT_DIM, false);
                    sy += 10;
                }
                sy += 6;
            }
        }
        context.disableScissor();

        // Update scroll bounds (content height vs visible height).
        int contentHeight = (sy + settingsScroll) - settingsTop;
        settingsMaxScroll = Math.max(0, contentHeight - visibleHeight);
        if (settingsScroll > settingsMaxScroll) settingsScroll = settingsMaxScroll;

        // Scrollbar hint.
        if (settingsMaxScroll > 0) {
            int trackH = visibleHeight;
            int thumbH = Math.max(12, trackH * visibleHeight / contentHeight);
            int thumbY = settingsTop + (trackH - thumbH) * settingsScroll / settingsMaxScroll;
            context.fill(right - 3, settingsTop, right - 2, visibleBottom, DIVIDER);
            context.fill(right - 4, thumbY, right - 1, thumbY + thumbH, ACCENT);
        }
    }

    // ---- Input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int button = click.button();

        // Sidebar selection (left click).
        if (button == 0) {
            for (int i = 0; i < featureRows.size(); i++) {
                int[] r = featureRows.get(i);
                if (inside(mx, my, r[0], r[1], r[2], r[3])) {
                    if (selected != i) settingsScroll = 0; // reset scroll when switching features
                    selected = i;
                    return true;
                }
            }
        }

        List<Feature> features = FeatureManager.getFeatures();
        if (!features.isEmpty()) {
            Feature feature = features.get(Math.min(selected, features.size() - 1));

            // Custom-panel features handle their own clicks (any button).
            if (feature.hasCustomPanel()) {
                if (feature.panelMouseClicked(this, click.x(), click.y(), button)) {
                    return true;
                }
                return super.mouseClicked(click, doubled);
            }

            if (button == 0) {
                if (inside(mx, my, masterSwitch[0], masterSwitch[1], masterSwitch[2], masterSwitch[3])) {
                    feature.setEnabled(!feature.isEnabled());
                    ConfigStore.save();
                    return true;
                }
                // "Open" button → the feature's config screen.
                if (openButtonShown && inside(mx, my, openButton[0], openButton[1], openButton[2], openButton[3])) {
                    Screen screen = feature.createConfigScreen(this);
                    if (screen != null && this.minecraft != null) {
                        this.minecraft.setScreen(screen);
                        return true;
                    }
                }
                // Feature-specific extra controls.
                if (feature.extraMouseClicked(this, mx, my, button)) {
                    return true;
                }
                // Settings toggles / keybind buttons only act while the feature itself is on.
                if (feature.isEnabled()) {
                    for (int i = 0; i < settingSwitches.size(); i++) {
                        int[] r = settingSwitches.get(i);
                        if (inside(mx, my, r[0], r[1], r[2], r[3])) {
                            settingRefs.get(i).toggle();
                            ConfigStore.save();
                            return true;
                        }
                    }
                    for (int i = 0; i < keybindButtons.size(); i++) {
                        int[] r = keybindButtons.get(i);
                        if (inside(mx, my, r[0], r[1], r[2], r[3])) {
                            listeningKeybind = keybindRefs.get(i); // capture the next key press
                            return true;
                        }
                    }
                    // Mode settings cycle in place; text settings open a small popup.
                    for (int i = 0; i < valueButtons.size(); i++) {
                        int[] r = valueButtons.get(i);
                        if (!inside(mx, my, r[0], r[1], r[2], r[3])) continue;
                        Setting<?> setting = valueRefs.get(i);
                        if (setting instanceof ModeSetting mode) {
                            mode.cycle();
                            ConfigStore.save();
                        } else if (setting instanceof IntSetting number && this.minecraft != null) {
                            this.minecraft.setScreen(new TextPromptScreen(this, number.getName(),
                                    String.valueOf(number.get()), value -> {
                                number.parse(value);
                                ConfigStore.save();
                            }));
                        } else if (setting instanceof StringSetting text && this.minecraft != null) {
                            this.minecraft.setScreen(new TextPromptScreen(this, text.getName(), text.get(), value -> {
                                text.set(value);
                                ConfigStore.save();
                            }));
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (listeningKeybind != null) {
            int key = input.key();
            listeningKeybind.setKey(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            listeningKeybind = null;
            ConfigStore.save();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && settingsMaxScroll > 0) {
            settingsScroll = Math.max(0, Math.min(settingsMaxScroll, settingsScroll - (int) (verticalAmount * 16)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        ConfigStore.save();
        super.onClose();
    }

    @Override
    public void removed() {
        // Let custom panels free GPU resources (e.g. the Custom Lava preview texture).
        for (Feature feature : FeatureManager.getFeatures()) {
            feature.panelRemoved();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Drawing helpers ------------------------------------------------------------------

    private void drawSwitch(GuiGraphicsExtractor context, int x, int y, boolean on) {
        drawSwitch(context, x, y, on, true);
    }

    private void drawSwitch(GuiGraphicsExtractor context, int x, int y, boolean on, boolean active) {
        int track = on ? ACCENT : TOGGLE_OFF;
        if (!active) track = TOGGLE_OFF;
        fillRound(context, x, y, x + SWITCH_W, y + SWITCH_H, track);
        int knob = SWITCH_H - 4;
        int knobX = on ? x + SWITCH_W - knob - 2 : x + 2;
        fillRound(context, knobX, y + 2, knobX + knob, y + 2 + knob, active ? KNOB : DOT_OFF);
    }

    /** Panel with a 1px border (rounded corners) and a fill. */
    private static void panel(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int fill) {
        fillRound(context, x1, y1, x2, y2, BORDER);
        fillRound(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** Filled rectangle with single-pixel chamfered corners, for a subtle rounded look. */
    private static void fillRound(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 2 || y2 - y1 < 2) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }
        context.fill(x1 + 1, y1, x2 - 1, y2, color);
        context.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    /** Shortens text with an ellipsis so it fits the given pixel width. */
    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (this.font.width(sb.toString() + c + "...") > maxWidth) break;
            sb.append(c);
        }
        return sb + "...";
    }

    private static boolean inside(int px, int py, int x1, int y1, int x2, int y2) {
        return px >= x1 && px <= x2 && py >= y1 && py <= y2;
    }
}
