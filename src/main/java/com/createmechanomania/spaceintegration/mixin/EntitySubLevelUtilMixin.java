package com.createmechanomania.spaceintegration.mixin;

import com.createmechanomania.spaceintegration.physics.ReferenceFrameOrientation;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySubLevelUtil.class)
public abstract class EntitySubLevelUtilMixin {
    @Inject(method = "getCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void createSpaceIntegration$getServerReferenceFrameOrientation(final Entity entity, final float partialTicks, final CallbackInfoReturnable<Quaterniondc> cir) {
        final Quaterniondc orientation = ReferenceFrameOrientation.getServerOrientation(entity);
        if (orientation != null) {
            cir.setReturnValue(orientation);
        }
    }

    @Inject(method = "hasCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void createSpaceIntegration$hasServerReferenceFrameOrientation(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (ReferenceFrameOrientation.hasServerOrientation(entity)) {
            cir.setReturnValue(true);
        }
    }
}