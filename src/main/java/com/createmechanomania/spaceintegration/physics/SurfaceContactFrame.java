package com.createmechanomania.spaceintegration.physics;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;

public final class SurfaceContactFrame {
    private static final double MIN_CONTACT_UP_ALIGNMENT = 0.55D;
    private static final double MIN_STICKY_CONTACT_UP_ALIGNMENT = 0.42D;
    private static final double MIN_PREVIOUS_UP_ALIGNMENT = 0.82D;
    private static final double MIN_NORMAL_VECTOR = 1.0E-8D;

    private SurfaceContactFrame() {
    }

    public static @Nullable Vector3d getContactUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return getSupportContactUp(entity, subLevel, desiredUp, null, destination);
    }

    public static @Nullable Vector3d getContactUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final boolean allowUnalignedFallback, final Vector3d destination) {
        if (!allowUnalignedFallback) {
            return getSupportContactUp(entity, subLevel, desiredUp, null, destination);
        }
        final Vector3d supportUp = getSupportContactUp(entity, subLevel, desiredUp, null, destination);
        if (supportUp != null) {
            return supportUp;
        }
        return getAnyContactUp(entity, subLevel, destination);
    }

    public static @Nullable Vector3d getSupportContactUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final @Nullable Vector3dc previousUp, final Vector3d destination) {
        if (!(entity instanceof final EntityMovementExtension movementExtension)) {
            return null;
        }
        if (desiredUp.lengthSquared() <= MIN_NORMAL_VECTOR) {
            return null;
        }
        final SubLevelEntityCollision.CollisionInfo collisionInfo = movementExtension.sable$getCollisionInfo();
        if (collisionInfo == null || collisionInfo.firstCollisions == null || collisionInfo.firstCollisions.isEmpty()) {
            return null;
        }

        final Vector3d normalizedDesiredUp = new Vector3d(desiredUp).normalize();
        final Vector3d normalizedPreviousUp = previousUp != null && previousUp.lengthSquared() > MIN_NORMAL_VECTOR ? new Vector3d(previousUp).normalize() : null;
        double bestAlignment = MIN_CONTACT_UP_ALIGNMENT;
        double bestStickyAlignment = MIN_STICKY_CONTACT_UP_ALIGNMENT;
        Vector3d bestNormal = null;
        Vector3d stickyNormal = null;
        for (final Map.Entry<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> entry : collisionInfo.firstCollisions.entrySet()) {
            final SubLevel collidedSubLevel = entry.getKey();
            if (collidedSubLevel == null || !collidedSubLevel.getUniqueId().equals(subLevel.getUniqueId())) {
                continue;
            }
            final SubLevelEntityCollision.FirstCollisionInfo firstCollision = entry.getValue();
            if (firstCollision == null || firstCollision.globalDirection() == null) {
                continue;
            }
            final Vector3dc normal = firstCollision.globalDirection();
            if (normal.lengthSquared() <= MIN_NORMAL_VECTOR) {
                continue;
            }
            final Vector3d normalizedNormal = new Vector3d(normal).normalize();
            final double alignment = normalizedNormal.dot(normalizedDesiredUp);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                bestNormal = new Vector3d(normalizedNormal);
            }
            if (normalizedPreviousUp != null && alignment > bestStickyAlignment && normalizedNormal.dot(normalizedPreviousUp) > MIN_PREVIOUS_UP_ALIGNMENT) {
                bestStickyAlignment = alignment;
                stickyNormal = new Vector3d(normalizedNormal);
            }
        }

        final Vector3d resolvedNormal = bestNormal != null ? bestNormal : stickyNormal;
        return resolvedNormal == null ? null : destination.set(resolvedNormal).normalize();
    }

    public static @Nullable Vector3d getAlignedContactUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return getSupportContactUp(entity, subLevel, desiredUp, null, destination);
    }

    public static @Nullable Vector3d getAnyContactUp(final Entity entity, final SubLevel subLevel, final Vector3d destination) {
        if (!(entity instanceof final EntityMovementExtension movementExtension)) {
            return null;
        }
        final SubLevelEntityCollision.CollisionInfo collisionInfo = movementExtension.sable$getCollisionInfo();
        if (collisionInfo == null || collisionInfo.firstCollisions == null || collisionInfo.firstCollisions.isEmpty()) {
            return null;
        }

        for (final Map.Entry<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> entry : collisionInfo.firstCollisions.entrySet()) {
            final SubLevel collidedSubLevel = entry.getKey();
            if (collidedSubLevel == null || !collidedSubLevel.getUniqueId().equals(subLevel.getUniqueId())) {
                continue;
            }
            final SubLevelEntityCollision.FirstCollisionInfo firstCollision = entry.getValue();
            if (firstCollision == null || firstCollision.globalDirection() == null || firstCollision.globalDirection().lengthSquared() <= MIN_NORMAL_VECTOR) {
                return null;
            }
            return destination.set(firstCollision.globalDirection()).normalize();
        }

        return null;
    }
}