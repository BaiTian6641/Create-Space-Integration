package com.createmechanomania.spaceintegration.client;

import com.createmechanomania.spaceintegration.physics.GravityMath;
import com.createmechanomania.spaceintegration.physics.GravityMode;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

@OnlyIn(Dist.CLIENT)
public final class ClientReferenceFrameOrientation {
    private ClientReferenceFrameOrientation() {
    }

    public static @Nullable Quaterniondc getEntityOrientation(final Entity entity, final float partialTicks) {
        final SubLevel trackingSubLevel = findClientSubLevel(entity);
        if (!(trackingSubLevel instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            return null;
        }
        final ClientViewAlignmentState.State state = ClientViewAlignmentState.get(subLevel.getUniqueId());
        if (!state.enabled()) {
            return null;
        }
        final ClientSubLevel sourceSubLevel = resolveSourceSubLevel(entity, subLevel, state);
        if (sourceSubLevel == null) {
            return null;
        }

        final Pose3dc pose = sourceSubLevel.renderPose(partialTicks);
        final Vector3d targetUp = getEntityUp(entity, subLevel, sourceSubLevel, state, partialTicks, new Vector3d());
        if (targetUp == null) {
            return null;
        }

        final Quaterniond orientation = new Quaterniond();
        if (!GravityMath.getReferenceFrameOrientationFromUp(
            pose,
            targetUp,
            orientation
        )) {
            return null;
        }
        return orientation;
    }

    public static boolean hasEntityOrientation(final Entity entity) {
        return getEntityOrientation(entity, 1.0F) != null;
    }

    public static @Nullable Vector3d getEntityUp(final Entity entity, final ClientSubLevel subLevel, final float partialTicks, final Vector3d destination) {
        final ClientViewAlignmentState.State state = ClientViewAlignmentState.get(subLevel.getUniqueId());
        if (!state.enabled()) {
            return null;
        }
        final ClientSubLevel sourceSubLevel = resolveSourceSubLevel(entity, subLevel, state);
        if (sourceSubLevel == null) {
            return null;
        }
        return getEntityUp(entity, subLevel, sourceSubLevel, state, partialTicks, destination);
    }

    public static @Nullable SubLevel findClientSubLevel(final Entity entity) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        if (subLevel instanceof ClientSubLevel) {
            return subLevel;
        }

        subLevel = Sable.HELPER.getContaining(entity);
        if (subLevel instanceof ClientSubLevel) {
            return subLevel;
        }

        if (!(entity instanceof final EntityMovementExtension movementExtension)) {
            return subLevel;
        }
        final SubLevelEntityCollision.CollisionInfo collisionInfo = movementExtension.sable$getCollisionInfo();
        if (collisionInfo == null) {
            return subLevel;
        }
        if (collisionInfo.trackingSubLevel instanceof ClientSubLevel) {
            return collisionInfo.trackingSubLevel;
        }
        if (collisionInfo.firstCollisions == null || collisionInfo.firstCollisions.isEmpty()) {
            return subLevel;
        }

        for (final SubLevel collidedSubLevel : collisionInfo.firstCollisions.keySet()) {
            if (collidedSubLevel instanceof ClientSubLevel) {
                return collidedSubLevel;
            }
        }
        return subLevel;
    }

    private static @Nullable ClientSubLevel resolveSourceSubLevel(final Entity entity, final ClientSubLevel subLevel, final ClientViewAlignmentState.State state) {
        if (state.sourceSubLevelId().equals(subLevel.getUniqueId())) {
            return subLevel;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
        if (container == null) {
            return null;
        }
        final SubLevel sourceSubLevel = container.getSubLevel(state.sourceSubLevelId());
        return sourceSubLevel instanceof final ClientSubLevel clientSource && !clientSource.isRemoved() ? clientSource : null;
    }

    private static @Nullable Vector3d getEntityUp(final Entity entity, final ClientSubLevel bodySubLevel, final ClientSubLevel sourceSubLevel, final ClientViewAlignmentState.State state, final float partialTicks, final Vector3d destination) {
        final Vector3d localPosition = getLocalPosition(entity, sourceSubLevel, partialTicks);
        final Pose3dc pose = sourceSubLevel.renderPose(partialTicks);
        final Vector3d down = new Vector3d();
        if (!GravityMath.getGravityDown(
            pose,
            state.gravityMode(),
            state.axisOriginCopy(),
            state.axisDirectionCopy(),
            state.gravityRadius(),
            localPosition,
            down
        )) {
            return null;
        }

        final Vector3d desiredUp = down.negate(new Vector3d()).normalize();
        return destination.set(desiredUp);
    }

    private static Vector3d getGlobalPosition(final Entity entity, final ClientSubLevel subLevel, final float partialTicks, final Vector3d destination) {
        final Vec3 entityPosition = entity.getPosition(partialTicks);
        destination.set(entityPosition.x, entityPosition.y, entityPosition.z);
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(entity.level(), destination);
        if (containingSubLevel instanceof final ClientSubLevel containingClientSubLevel) {
            return containingClientSubLevel.renderPose(partialTicks).transformPosition(destination, destination);
        }
        return destination;
    }

    private static Vector3d getLocalPosition(final Entity entity, final ClientSubLevel subLevel, final float partialTicks) {
        final Vec3 entityPosition = entity.getPosition(partialTicks);
        final Vector3d position = new Vector3d(entityPosition.x, entityPosition.y, entityPosition.z);
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(entity.level(), position);
        if (containingSubLevel != null && containingSubLevel.getUniqueId().equals(subLevel.getUniqueId())) {
            return position;
        }
        if (containingSubLevel instanceof final ClientSubLevel containingClientSubLevel) {
            containingClientSubLevel.renderPose(partialTicks).transformPosition(position, position);
        }
        return subLevel.renderPose(partialTicks).transformPositionInverse(position, position);
    }
}