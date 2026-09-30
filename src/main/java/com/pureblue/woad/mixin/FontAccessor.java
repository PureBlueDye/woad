package com.pureblue.woad.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the glyph provider behind the game's {@link Font}, so {@code ui.UiText} can build Font
 * instances that answer every "default font" lookup with Inter instead.
 */
@Mixin(Font.class)
public interface FontAccessor {

    @Accessor("provider")
    Font.Provider woad$provider();
}
