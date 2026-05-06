package com.createmechanomania.spaceintegration.mixin;

import com.createmechanomania.spaceintegration.client.ClientReferenceFrameOrientation;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySubLevelUtil.class)
public abstract class ClientEntitySubLevelUtilMixin {
    @Inject(method = "getCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void createSpaceIntegration$getClientReferenceFrameOrientation(final Entity entity, final float partialTicks, final CallbackInfoReturnable<Quaterniondc> cir) {
        final Quaterniondc orientation = ClientReferenceFrameOrientation.getEntityOrientation(entity, partialTicks);
        if (orientation != null) {
            cir.setReturnValue(orientation);
        }
    }

    @Inject(method = "hasCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void createSpaceIntegration$hasClientReferenceFrameOrientation(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (ClientReferenceFrameOrientation.hasEntityOrientation(entity)) {
            cir.setReturnValue(true);
        }
    }
}