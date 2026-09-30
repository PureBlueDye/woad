package com.pureblue.woad.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the render state a GUI frame is collected into, so {@code ui.Draw} can submit its own
 * geometry. The public drawing API only offers axis-aligned fills and vertical gradients; rounded
 * corners, horizontal gradients and soft shadows need vertices of their own.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("guiRenderState")
    GuiRenderState woad$guiRenderState();
}
