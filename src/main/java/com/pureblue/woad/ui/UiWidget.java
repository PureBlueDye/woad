package com.pureblue.woad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Base of every interactive component.
 *
 * <p>Components are laid out every frame: the screen calls {@link #bounds} with wherever the
 * component belongs this frame, then {@link #render}. That fits screens whose layout depends on
 * scrolling or on content, and it keeps hit-testing on the same rectangle that was drawn.
 * State that animates (hover, press, focus) lives in the component, so it survives re-layout.
 */
public abstract class UiWidget {

    protected float x;
    protected float y;
    protected float w;
    protected float h;
    protected boolean enabled = true;
    protected boolean visible = true;

    protected final Anim.Toggle hover = new Anim.Toggle(Theme.MS_HOVER, Anim.Ease.OUT_CUBIC);

    public UiWidget bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return w; }
    public float height() { return h; }

    public UiWidget enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Hidden components neither draw nor take clicks — rows scrolled out of view, for instance. */
    public UiWidget visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public final void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!visible) return;
        float hv = hover.update(enabled && contains(mouseX, mouseY));
        draw(ctx, mouseX, mouseY, hv);
    }

    /**
     * Draws the component.
     *
     * @param hover eased hover progress, 0 at rest and 1 fully hovered
     */
    protected abstract void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float hover);

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }

    /** The same click vanilla buttons make. */
    protected static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
