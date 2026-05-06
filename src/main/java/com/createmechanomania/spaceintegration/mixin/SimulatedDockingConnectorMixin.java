package com.createmechanomania.spaceintegration.mixin;

import com.createmechanomania.spaceintegration.physics.SableSpacePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity", remap = false)
public abstract class SimulatedDockingConnectorMixin {
    @Shadow(remap = false)
    public @Nullable BlockPos otherConnectorPosition;

    @Shadow(remap = false)
    public @Nullable UUID otherConnectorSubLevelId;

    @Shadow(remap = false)
    public abstract boolean isLocked();

    @Inject(method = "setDock", at = @At("TAIL"), remap = false)
    private void createSpaceIntegration$rememberDockConnection(final CallbackInfo ci) {
        final BlockEntity self = (BlockEntity) (Object) this;
        if (this.isLocked() && this.otherConnectorPosition != null && self.getLevel() != null && self.getLevel().getBlockEntity(this.otherConnectorPosition) instanceof final BlockEntity otherBlockEntity) {
            SableSpacePhysics.rememberDockConnection((BlockEntity) (Object) this, otherBlockEntity);
        }
    }

    @Inject(method = "unDock", at = @At("HEAD"), remap = false)
    private void createSpaceIntegration$forgetDockConnection(final CallbackInfo ci) {
        SableSpacePhysics.forgetDockConnection((BlockEntity) (Object) this, this.otherConnectorSubLevelId, this.otherConnectorPosition);
    }

    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void createSpaceIntegration$forgetRemovedDockConnection(final CallbackInfo ci) {
        SableSpacePhysics.forgetDockConnection((BlockEntity) (Object) this, this.otherConnectorSubLevelId, this.otherConnectorPosition);
    }
}