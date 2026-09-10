package com.pureblue.woad.mixin;

import com.pureblue.woad.lava.LavaColorManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Intercepts the creation of every sprite to grab the live NativeImage of lava/water (whether it
 * comes from vanilla or the active resource pack). Targets the 6-arg constructor; the 3-arg one
 * delegates to it, so all sprite creations pass through here.
 */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {

    @Inject(
        method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
        at = @At("TAIL")
    )
    private void woad$captureLavaSprite(Identifier id,
                                            FrameSize dimensions,
                                            NativeImage image,
                                            Optional<?> animationMetadata,
                                            List<?> metadata,
                                            Optional<?> textureMetadata,
                                            CallbackInfo ci) {
        LavaColorManager.INSTANCE.tryCapture(id, image);
    }
}
