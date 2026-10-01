package com.pureblue.woad.gui;

import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.setting.BooleanSetting;
import com.pureblue.woad.core.setting.IntSetting;
import com.pureblue.woad.core.setting.KeybindSetting;
import com.pureblue.woad.core.setting.ModeSetting;
import com.pureblue.woad.core.setting.Setting;
import com.pureblue.woad.core.setting.StringSetting;
import com.pureblue.woad.ui.Anim;
import com.pureblue.woad.ui.Draw;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiDropdown;
import com.pureblue.woad.ui.UiValueSlider;
import com.pureblue.woad.ui.UiPanel;
import com.pureblue.woad.ui.UiScreen;
import com.pureblue.woad.ui.UiScroll;
import com.pureblue.woad.ui.UiText;
import com.pureblue.woad.ui.UiToggle;
import com.pureblue.woad.ui.UiValueButton;
import com.pureblue.woad.ui.UiWidget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's configuration menu: the feature list on the left, the selected feature on the right —
 * its switch, description, actions and settings.
 *
 * <p>Layout happens every frame, so rows follow the scroll position and the content of the
 * selected feature. Components are kept per feature and per setting, which is what lets hover,
 * switches and the opening animate smoothly instead of restarting on every frame.
 */
public class WoadScreen extends UiScreen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 264;
    private static final int HEADER_H = 30;
    private static final int SIDEBAR_W = 118;
    private static final int PAD = 12;
    private static final int SIDE_ROW_H = 19;

    private int selected = 0;
    private KeybindSetting listeningKeybind = null;

    // Components, kept across frames so their animations carry on.
    private final List<SideRow> sideRows = new ArrayList<>();
    private final Map<Feature, UiToggle> masterSwitches = new HashMap<>();
    private final Map<Feature, UiButton> openButtons = new HashMap<>();
    private final Map<Setting<?>, UiWidget> controls = new HashMap<>();
    private final CloseButton close = new CloseButton();
    private final UiScroll scroll = new UiScroll();

    // What was laid out this frame, for routing clicks to what is actually on screen.
    private final List<UiWidget> visibleControls = new ArrayList<>();
    private float viewTop;
    private float viewBottom;
    private UiToggle shownMaster;
    private UiButton shownOpen;
    /** The control a press started on, which gets the drag and the release (sliders). */
    private UiWidget dragging;

    public WoadScreen() {
        super(Component.literal(Woad.NAME));
        List<Feature> features = FeatureManager.getFeatures();
        for (int i = 0; i < features.size(); i++) sideRows.add(new SideRow(features.get(i), i));
    }

    /** Selects a feature by name, as a click in the sidebar would. Used by development captures. */
    public void selectFeature(String name) {
        List<Feature> features = FeatureManager.getFeatures();
        for (int i = 0; i < features.size(); i++) {
            if (features.get(i).getName().equalsIgnoreCase(name)) {
                selected = i;
                scroll.scrollToTop();
                return;
            }
        }
    }

    /**
     * Where a setting's control of the selected feature was last drawn, as {x, y, w, h}, or
     * {@code null}. Used by development captures to click real controls.
     */
    public float[] controlBounds(String settingName) {
        for (Map.Entry<Setting<?>, UiWidget> entry : controls.entrySet()) {
            if (entry.getKey().getName().equals(settingName) && visibleControls.contains(entry.getValue())) {
                UiWidget c = entry.getValue();
                return new float[]{c.x(), c.y(), c.width(), c.height()};
            }
        }
        return null;
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float x = Math.round((this.width - PANEL_W) / 2f);
        float y = Math.round((this.height - PANEL_H) / 2f);
        UiPanel.panel(ctx, x, y, PANEL_W, PANEL_H);

        renderHeader(ctx, x, y, mouseX, mouseY);

        float bodyTop = y + HEADER_H;
        float bodyBottom = y + PANEL_H;
        Draw.roundRect(ctx, x + 1, bodyTop, SIDEBAR_W - 1, bodyBottom - bodyTop - 1, 0f, Theme.SIDEBAR);
        UiPanel.vHairline(ctx, x + SIDEBAR_W, bodyTop, bodyBottom - 1);
        for (SideRow row : sideRows) {
            row.bounds(x + 6, bodyTop + 7 + row.index * (SIDE_ROW_H + 1), SIDEBAR_W - 12, SIDE_ROW_H)
                    .render(ctx, mouseX, mouseY);
        }

        List<Feature> features = FeatureManager.getFeatures();
        if (features.isEmpty()) return;
        Feature feature = features.get(Math.min(selected, features.size() - 1));
        renderFeature(ctx, feature, x + SIDEBAR_W, bodyTop, x + PANEL_W, bodyBottom, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor ctx, float x, float y, int mouseX, int mouseY) {
        Draw.roundRectH(ctx, x + 1, y + 1, PANEL_W - 2, HEADER_H - 1, 0f, 0x242563EB, 0x00000000);
        UiText.draw(ctx, Woad.NAME, UiText.Style.TITLE, x + 14,
                UiText.centerY(UiText.Style.TITLE, y, HEADER_H), Theme.TEXT);

        close.bounds(x + PANEL_W - 8 - 16, y + (HEADER_H - 16) / 2f, 16, 16).render(ctx, mouseX, mouseY);
        String version = "v" + version();
        float pillW = UiText.width(version, UiText.Style.LABEL) + 12;
        UiPanel.pill(ctx, version, x + PANEL_W - 8 - 16 - 6 - pillW, y + (HEADER_H - 12) / 2f,
                Theme.TEXT_2, Theme.NEUTRAL_TINT);
        UiPanel.hairline(ctx, x, x + PANEL_W, y + HEADER_H);
    }

    private void renderFeature(GuiGraphicsExtractor ctx, Feature feature, float left, float top,
                               float right, float bottom, int mouseX, int mouseY) {
        visibleControls.clear();
        shownMaster = null;
        shownOpen = null;
        float cx = left + PAD;
        float contentW = right - left - PAD * 2;
        float y = top + 11;

        // Title row: name, state and master switch.
        UiText.draw(ctx, feature.getName(), UiText.Style.HEADING, cx, y, Theme.TEXT);
        if (feature.hasToggle()) {
            float chipX = cx + UiText.width(feature.getName(), UiText.Style.HEADING) + 7;
            float chipY = y + (UiText.capHeight(UiText.Style.HEADING) - 12) / 2f;
            if (feature.isEnabled()) {
                UiPanel.pill(ctx, "ACTIVE", chipX, chipY, Theme.OK, Theme.OK_TINT);
            } else {
                UiPanel.pill(ctx, "INACTIVE", chipX, chipY, Theme.TEXT_2, Theme.NEUTRAL_TINT);
            }
            UiToggle master = masterSwitches.computeIfAbsent(feature, f -> new UiToggle(f::isEnabled, on -> {
                f.setEnabled(on);
                ConfigStore.save();
            }));
            master.at(right - PAD - Theme.SWITCH_W,
                    y + (UiText.capHeight(UiText.Style.HEADING) - Theme.SWITCH_H) / 2f).render(ctx, mouseX, mouseY);
            shownMaster = master;
        }
        y += 17;

        String description = feature.getDescription();
        if (description != null && !description.isBlank()) {
            y += UiText.drawWrapped(ctx, description, UiText.Style.BODY, cx, y, (int) contentW, Theme.TEXT_2) - 2;
        }

        // Actions: the feature's own screen, then whatever extra controls it draws.
        float actionsX = cx;
        boolean hasActions = feature.hasConfigScreen() || feature.extraHeight() > 0;
        if (hasActions) {
            y += 5;
            if (feature.hasConfigScreen()) {
                UiButton open = openButtons.computeIfAbsent(feature, f -> new UiButton("Open", UiButton.Variant.PRIMARY, () -> {
                    Screen screen = f.createConfigScreen(this);
                    if (screen != null && this.minecraft != null) this.minecraft.setScreen(screen);
                }));
                open.bounds(actionsX, y, open.preferredWidth(), Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
                shownOpen = open;
                actionsX += open.width() + 5;
            }
            if (feature.extraHeight() > 0) {
                feature.renderExtra(ctx, this, Math.round(actionsX), Math.round(y), Math.round(right - PAD), mouseX, mouseY);
            }
            y += Math.max(Theme.BUTTON_H_SMALL, feature.extraHeight());
        }

        // Features with their own panel (Custom Lava) take over the rest of the space.
        if (feature.hasCustomPanel()) {
            feature.renderPanel(ctx, this, Math.round(left), Math.round(y + 6), Math.round(right), Math.round(bottom), mouseX, mouseY);
            return;
        }
        if (feature.getSettings().isEmpty()) return;

        y += 9;
        UiPanel.hairline(ctx, left, right, y);
        renderSettings(ctx, feature, left, y + 1, right, bottom - 1, mouseX, mouseY);
    }

    private void renderSettings(GuiGraphicsExtractor ctx, Feature feature, float left, float top,
                                float right, float bottom, int mouseX, int mouseY) {
        boolean on = feature.isEnabled();
        viewTop = top;
        viewBottom = bottom;
        float cx = left + PAD;
        float contentW = right - left - PAD * 2;

        float offset = scroll.begin(ctx, left, top, right - left, bottom - top);
        // Settings of a switched-off feature stay visible but read as unavailable.
        float fadePrevious = Draw.pushAlpha(on ? 1f : 0.45f);
        float y = top - offset;
        List<Setting<?>> settings = feature.getSettings();
        for (int i = 0; i < settings.size(); i++) {
            Setting<?> setting = settings.get(i);
            UiWidget control = controlFor(setting);
            if (control == null) continue;
            control.enabled(on);

            float controlW = controlWidth(setting, control);
            float textW = contentW - controlW - 12;
            float nameY = y + 7;
            int descH = UiText.wrappedHeight(setting.getDescription(), UiText.Style.BODY, (int) textW);
            float rowH = 7 + 10 + descH + 5;

            UiText.draw(ctx, setting.getName(), UiText.Style.LABEL, cx, nameY, Theme.TEXT);
            UiText.drawWrapped(ctx, setting.getDescription(), UiText.Style.BODY, cx, nameY + 10, (int) textW, Theme.TEXT_2);

            float controlH = control instanceof UiToggle ? Theme.SWITCH_H : 16;
            control.bounds(right - PAD - controlW, y + (rowH - controlH) / 2f, controlW, controlH);
            if (control instanceof UiValueButton value && setting instanceof KeybindSetting kb) {
                value.listening(kb == listeningKeybind);
            }
            control.render(ctx, mouseX, mouseY);
            if (y + rowH > top && y < bottom) visibleControls.add(control);
            // A list whose button has scrolled out of view would float detached from it.
            if (control instanceof UiDropdown dropdown
                    && (control.y() < top || control.y() + controlH > bottom)) dropdown.close();

            y += rowH;
            if (i < settings.size() - 1) UiPanel.hairline(ctx, cx, right - PAD, y);
        }
        Draw.popAlpha(fadePrevious);
        scroll.end(ctx, y + offset - top + 4, mouseX, mouseY);

        // Drawn last and outside the scroll area's clipping, so the list lies over the rows below.
        for (Setting<?> setting : settings) {
            if (controls.get(setting) instanceof UiDropdown dropdown) {
                dropdown.renderList(ctx, mouseX, mouseY, this.width, this.height);
            }
        }
    }

    /** The settings dropdown whose list is unfolded, if any. Only one is open at a time. */
    private UiDropdown settingsDropdown() {
        for (UiWidget control : controls.values()) {
            if (control instanceof UiDropdown dropdown && dropdown.isOpen()) return dropdown;
        }
        return null;
    }

    private void closeDropdowns() {
        for (UiWidget control : controls.values()) {
            if (control instanceof UiDropdown dropdown) dropdown.close();
        }
    }

    private float controlWidth(Setting<?> setting, UiWidget control) {
        if (setting instanceof BooleanSetting) return Theme.SWITCH_W;
        if (control instanceof MutedWhenEmpty text) return text.preferredWidth(58, 110);
        if (setting instanceof KeybindSetting) return ((UiValueButton) control).preferredWidth(44, 80);
        if (control instanceof UiDropdown dropdown) return dropdown.preferredWidth(58, 110);
        if (control instanceof UiValueSlider) return 110;
        return ((UiValueButton) control).preferredWidth(58, 110);
    }

    /** The control for a setting, created on first use and kept so its animations continue. */
    private UiWidget controlFor(Setting<?> setting) {
        return controls.computeIfAbsent(setting, s -> {
            if (s instanceof BooleanSetting bool) {
                return new UiToggle(bool::enabled, value -> {
                    if (value != bool.enabled()) bool.toggle();
                    ConfigStore.save();
                });
            }
            if (s instanceof ModeSetting mode) {
                return new UiDropdown(mode::getOptions, mode::get, value -> {
                    mode.set(value);
                    ConfigStore.save();
                });
            }
            if (s instanceof IntSetting number && number.isSlider()) {
                return new UiValueSlider(number.min(), number.max(), number.sliderStep(), number::get,
                        number::set, number::sliderLabel, ConfigStore::save);
            }
            if (s instanceof IntSetting number) {
                return new UiValueButton(UiValueButton.Kind.EDIT, () -> String.valueOf(number.get()), () -> {
                    if (this.minecraft == null) return;
                    this.minecraft.setScreen(new TextPromptScreen(this, number.getName(), number.getDescription(),
                            String.valueOf(number.get()), value -> {
                        number.parse(value);
                        ConfigStore.save();
                    }));
                });
            }
            if (s instanceof StringSetting text) {
                UiValueButton button = new UiValueButton(UiValueButton.Kind.EDIT, text::display, () -> {
                    if (this.minecraft == null) return;
                    this.minecraft.setScreen(new TextPromptScreen(this, text.getName(), text.getDescription(), text.get(), value -> {
                        text.set(value);
                        ConfigStore.save();
                    }));
                });
                return new MutedWhenEmpty(button, text);
            }
            if (s instanceof KeybindSetting kb) {
                return new UiValueButton(UiValueButton.Kind.KEY, kb::keyName, () -> listeningKeybind = kb);
            }
            return null;
        });
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer(Woad.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    // ---- Input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        // An unfolded list takes the click first: a pick, or a click elsewhere that only closes it.
        UiDropdown unfolded = settingsDropdown();
        if (unfolded != null && unfolded.listMouseClicked(mx, my, button)) return true;

        if (close.mouseClicked(mx, my, button)) return true;
        for (SideRow row : sideRows) {
            if (row.mouseClicked(mx, my, button)) return true;
        }

        List<Feature> features = FeatureManager.getFeatures();
        if (!features.isEmpty()) {
            Feature feature = features.get(Math.min(selected, features.size() - 1));
            if (shownMaster != null && shownMaster.mouseClicked(mx, my, button)) return true;
            if (shownOpen != null && shownOpen.mouseClicked(mx, my, button)) return true;
            if (feature.extraMouseClicked(this, mx, my, button)) return true;
            if (feature.hasCustomPanel()) {
                if (feature.panelMouseClicked(this, mx, my, button)) return true;
            } else if (my >= viewTop && my < viewBottom) {
                if (scroll.mouseClicked(mx, my, button)) return true;
                for (UiWidget control : visibleControls) {
                    if (control.mouseClicked(mx, my, button)) {
                        dragging = control; // a slider keeps following the pointer from here
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (scroll.mouseDragged(click.x(), click.y(), click.button())) return true;
        if (dragging != null && dragging.mouseDragged(click.x(), click.y(), click.button())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        scroll.mouseReleased(click.x(), click.y(), click.button());
        if (dragging != null) {
            dragging.mouseReleased(click.x(), click.y(), click.button());
            dragging = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        UiDropdown unfolded = settingsDropdown();
        if (unfolded != null && unfolded.listMouseScrolled(mouseX, mouseY, vertical)) return true;
        if (scroll.mouseScrolled(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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
        // Escape folds an open list back up before it closes the menu.
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && settingsDropdown() != null) {
            closeDropdowns();
            return true;
        }
        return super.keyPressed(input);
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

    // ---- Local components ------------------------------------------------------------------------

    /** One feature in the sidebar: status dot and name, highlighted when selected. */
    private final class SideRow extends UiWidget {
        private final Feature feature;
        private final int index;
        private final Anim.Toggle selection = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);

        SideRow(Feature feature, int index) {
            this.feature = feature;
            this.index = index;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
            float s = selection.update(index == selected);
            float r = Theme.RADIUS_CONTROL + 0.5f;
            if (hv > 0.01f && s < 0.99f) {
                Draw.roundRect(ctx, x, y, w, h, r, Draw.withAlpha(Theme.HOVER, 0.75f * hv * (1 - s)));
            }
            if (s > 0.01f) {
                Draw.roundRectH(ctx, x, y, w, h, r,
                        Draw.withAlpha(Theme.SELECTED_FROM, s), Draw.withAlpha(Theme.SELECTED_TO, s));
                Draw.outline(ctx, x, y, w, h, r, 1f, Draw.withAlpha(0x4D3B82F6, s));
            }
            UiPanel.dot(ctx, x + 9, y + h / 2f, feature.isEnabled(), Theme.CYAN);
            int colour = Draw.mix(Draw.mix(Theme.TEXT_2, Theme.TEXT, hv), Theme.TEXT, s);
            UiText.draw(ctx, UiText.ellipsize(feature.getName(), UiText.Style.LABEL, (int) w - 22),
                    UiText.Style.LABEL, x + 17, UiText.centerY(UiText.Style.LABEL, y, h), colour);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !contains(mx, my)) return false;
            if (selected != index) {
                selected = index;
                scroll.scrollToTop();
                listeningKeybind = null;
            }
            return true;
        }
    }

    /** The cross in the header. Escape still closes the menu as before. */
    private final class CloseButton extends UiWidget {
        @Override
        protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
            if (hv > 0.01f) Draw.roundRect(ctx, x, y, w, h, Theme.RADIUS_CONTROL, Draw.withAlpha(Theme.HOVER, hv));
            Draw.cross(ctx, x + w / 2f, y + h / 2f, 6f, Draw.mix(Theme.TEXT_2, Theme.TEXT, hv));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !contains(mx, my)) return false;
            onClose();
            return true;
        }
    }

    /** A text value that shows dimmed while it has nothing in it ("&lt;not set&gt;"). */
    private static final class MutedWhenEmpty extends UiWidget {
        private final UiValueButton inner;
        private final StringSetting setting;

        MutedWhenEmpty(UiValueButton inner, StringSetting setting) {
            this.inner = inner;
            this.setting = setting;
        }

        int preferredWidth(int min, int max) {
            return inner.preferredWidth(min, max);
        }

        @Override
        public UiWidget bounds(float x, float y, float w, float h) {
            super.bounds(x, y, w, h);
            inner.bounds(x, y, w, h);
            return this;
        }

        @Override
        public UiWidget enabled(boolean enabled) {
            super.enabled(enabled);
            inner.enabled(enabled);
            return this;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hv) {
            inner.muted(setting.get() == null || setting.get().isBlank());
            inner.render(ctx, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return inner.mouseClicked(mx, my, button);
        }
    }
}
