package com.pureblue.woad.mixin;

import com.pureblue.woad.core.FeatureManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Render Optimizer skip entities. Answering "don't render" here, where the game decides which
 * entities to draw this frame, drops them before any of their drawing work is done.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void woad$skipHidden(E entity, Frustum frustum, double camX, double camY, double camZ,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (FeatureManager.RENDER_OPTIMIZER.shouldHide(entity)) cir.setReturnValue(false);
    }
}
