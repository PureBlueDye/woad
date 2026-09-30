package com.pureblue.woad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of Woad's screens: the blurred, darkened world behind, an opening animation, tooltips on
 * top, and input routed to the screen's components.
 *
 * <p>Subclasses draw in {@link #renderContent} and register their components with {@link #add};
 * clicks, drags, releases and wheel turns reach those components before anything else. The
 * opening (fade, short upward slide, slight zoom) plays once per screen instance, not on every
 * window resize.
 */
public abstract class UiScreen extends Screen {

    protected final List<UiWidget> components = new ArrayList<>();
    protected final List<UiScroll> scrolls = new ArrayList<>();
    protected final UiTooltip tooltip = new UiTooltip();
    private double openedAt = -1;

    protected UiScreen(Component title) {
        super(title);
    }

    protected <T extends UiWidget> T add(T component) {
        components.add(component);
        return component;
    }

    /** Lets panels drawn inside this screen (a feature's own panel, say) offer tooltips. */
    public UiTooltip tooltip() {
        return tooltip;
    }

    protected UiScroll addScroll(UiScroll scroll) {
        scrolls.add(scroll);
        return scroll;
    }

    /** Draws the screen's own content. The pose already carries the opening animation. */
    protected abstract void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta);

    /** Opening progress, 0 → 1. */
    protected float openProgress() {
        if (openedAt < 0) openedAt = Anim.nowMs();
        return Anim.Ease.OUT_CUBIC.apply(Anim.clamp01((float) ((Anim.nowMs() - openedAt) / Theme.MS_OPEN)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Keep the game's world blur, but not its menu texture: the backdrop is the theme's.
        if (this.minecraft != null && this.minecraft.level != null) {
            extractBlurredBackground(ctx);
            UiPanel.backdrop(ctx, this.width, this.height);
        } else {
            UiPanel.solidBackdrop(ctx, this.width, this.height);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        float p = openProgress();
        float cx = this.width / 2f;
        float cy = this.height / 2f;
        float scale = 0.98f + 0.02f * p;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy + (1 - p) * Theme.OPEN_SLIDE);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-cx, -cy);
        float previous = Draw.pushAlpha(p);
        try {
            renderContent(ctx, mouseX, mouseY, delta);
        } finally {
            Draw.popAlpha(previous);
            ctx.pose().popMatrix();
        }
        tooltip.render(ctx, mouseX, mouseY, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        for (UiScroll scroll : scrolls) {
            if (scroll.mouseClicked(click.x(), click.y(), click.button())) return true;
        }
        for (int i = components.size() - 1; i >= 0; i--) {
            if (components.get(i).mouseClicked(click.x(), click.y(), click.button())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        for (UiScroll scroll : scrolls) {
            if (scroll.mouseDragged(click.x(), click.y(), click.button())) return true;
        }
        for (UiWidget component : components) {
            if (component.mouseDragged(click.x(), click.y(), click.button())) return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        boolean handled = false;
        for (UiScroll scroll : scrolls) handled |= scroll.mouseReleased(click.x(), click.y(), click.button());
        for (UiWidget component : components) handled |= component.mouseReleased(click.x(), click.y(), click.button());
        return super.mouseReleased(click) || handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (UiScroll scroll : scrolls) {
            if (scroll.mouseScrolled(mx, my, vertical)) return true;
        }
        for (UiWidget component : components) {
            if (component.mouseScrolled(mx, my, vertical)) return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
}
