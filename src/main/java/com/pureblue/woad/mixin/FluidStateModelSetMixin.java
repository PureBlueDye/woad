package com.pureblue.woad.mixin;

import com.pureblue.woad.lava.LavaColorManager;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Decides what lava is drawn with, by swapping the model the chunk mesher asks for.
 *
 * <p>26.x rebuilt fluid rendering: a fluid's sprites and tint now come from a baked
 * {@link FluidModel} looked up here, and the old {@code FluidRenderHandlerRegistry} that used to let
 * a mod substitute lava's sprites is gone. Handing back water's model instead is the replacement —
 * it is also what makes a colour change instant, since nothing about the texture atlas changes and
 * only the chunk meshes have to be rebuilt.
 */
@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void woad$recolorLava(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
        FluidModel replacement = LavaColorManager.INSTANCE.modelFor(
                (FluidStateModelSet) (Object) this, state, cir.getReturnValue());
        if (replacement != null) cir.setReturnValue(replacement);
    }
}
