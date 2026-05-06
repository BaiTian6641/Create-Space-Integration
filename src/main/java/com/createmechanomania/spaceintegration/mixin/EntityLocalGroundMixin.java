package com.createmechanomania.spaceintegration.mixin;

import com.createmechanomania.spaceintegration.physics.ReferencePlaneGravity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 1200)
public abstract class EntityLocalGroundMixin {
    @Inject(method = "onGround", at = @At("HEAD"), cancellable = true)
    private void createSpaceIntegration$onReferenceSurface(final CallbackInfoReturnable<Boolean> cir) {
        if (ReferencePlaneGravity.hasReferenceSurfaceSupport((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}