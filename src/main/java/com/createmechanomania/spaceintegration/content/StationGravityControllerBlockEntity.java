package com.createmechanomania.spaceintegration.content;

import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import com.createmechanomania.spaceintegration.physics.GravityMode;
import com.createmechanomania.spaceintegration.physics.SableSpacePhysics;
import com.createmechanomania.spaceintegration.registry.SpaceIntegrationRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public class StationGravityControllerBlockEntity extends BlockEntity {
    private static final String ENABLED_TAG = "Enabled";
    private static final String GRAVITY_MODE_TAG = "GravityMode";
    private static final String STRENGTH_TAG = "StrengthMultiplier";
    private static final String RADIUS_TAG = "Radius";

    private GravityMode gravityMode = GravityMode.RADIAL;
    private boolean enabled = true;
    private double strengthMultiplier = -1.0D;
    private double radius = -1.0D;
    private int tickCounter;

    public StationGravityControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(SpaceIntegrationRegistry.STATION_GRAVITY_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final StationGravityControllerBlockEntity blockEntity) {
        if (!SpaceIntegrationConfig.stationControllerEnabled() || !blockEntity.enabled) {
            return;
        }
        blockEntity.tickCounter++;
        if (blockEntity.tickCounter < SpaceIntegrationConfig.stationControllerRefreshInterval()) {
            return;
        }
        blockEntity.tickCounter = 0;
        blockEntity.applyToContainingSubLevel(null);
    }

    public void handleUse(final Player player, final boolean alternateAction) {
        if (alternateAction) {
            this.gravityMode = this.gravityMode.next();
            player.sendSystemMessage(Component.translatable("block.create_space_integration.station_gravity_controller.mode", this.gravityMode.id()));
        } else {
            this.enabled = !this.enabled;
            player.sendSystemMessage(Component.translatable("block.create_space_integration.station_gravity_controller.enabled", Boolean.toString(this.enabled)));
        }

        this.setChanged();
        if (this.level instanceof final ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(this.worldPosition);
        }
        this.applyToContainingSubLevel(player);
    }

    private boolean applyToContainingSubLevel(final Player player) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return false;
        }
        if (!this.enabled) {
            return false;
        }

        final SubLevel containing = Sable.HELPER.getContaining(this);
        if (!(containing instanceof final ServerSubLevel subLevel) || subLevel.isRemoved() || !SableSpacePhysics.isAdAstraZeroGravity(serverLevel)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("block.create_space_integration.station_gravity_controller.no_sublevel"));
            }
            return false;
        }

        final Direction facing = this.getBlockState().getValue(StationGravityControllerBlock.FACING);
        final Vector3d axisOrigin = new Vector3d(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D);
        final Vector3d axisDirection = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        SableSpacePhysics.configureStationGravity(
                subLevel,
                SpaceIntegrationConfig.stationControllerStationKeepingDefault(),
                true,
                SpaceIntegrationConfig.viewAlignmentDefaultEnabled(),
                this.gravityMode,
                this.getStrengthMultiplier(),
                this.getRadius(),
                axisOrigin,
                axisDirection
        );

        if (player != null) {
            player.sendSystemMessage(Component.translatable("block.create_space_integration.station_gravity_controller.applied", subLevel.getUniqueId().toString()));
        }
        return true;
    }

    private double getStrengthMultiplier() {
        return this.strengthMultiplier > 0.0D ? this.strengthMultiplier : SpaceIntegrationConfig.stationControllerGravityStrength();
    }

    private double getRadius() {
        return this.radius >= 0.0D ? this.radius : SpaceIntegrationConfig.stationControllerDefaultRadius();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ENABLED_TAG, this.enabled);
        tag.putString(GRAVITY_MODE_TAG, this.gravityMode.id());
        tag.putDouble(STRENGTH_TAG, this.strengthMultiplier);
        tag.putDouble(RADIUS_TAG, this.radius);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ENABLED_TAG, Tag.TAG_BYTE)) {
            this.enabled = tag.getBoolean(ENABLED_TAG);
        }
        if (tag.contains(GRAVITY_MODE_TAG, Tag.TAG_STRING)) {
            this.gravityMode = GravityMode.byId(tag.getString(GRAVITY_MODE_TAG));
        }
        if (tag.contains(STRENGTH_TAG, Tag.TAG_DOUBLE)) {
            this.strengthMultiplier = tag.getDouble(STRENGTH_TAG);
        }
        if (tag.contains(RADIUS_TAG, Tag.TAG_DOUBLE)) {
            this.radius = tag.getDouble(RADIUS_TAG);
        }
    }
}