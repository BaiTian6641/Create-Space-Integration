package com.createmechanomania.spaceintegration.physics;

import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

public final class ReferenceFrameOrientation {
    private ReferenceFrameOrientation() {
    }

    public static @Nullable Quaterniondc getServerOrientation(final Entity entity) {
        if (!SpaceIntegrationConfig.entityOrientationEnabled()) {
            return null;
        }
        if (!(entity.level() instanceof ServerLevel) || entity.level().isClientSide()) {
            return null;
        }

        final ServerSubLevel bodySubLevel = SableSpacePhysics.findEntitySubLevel(entity);
        final ServerSubLevel sourceSubLevel = bodySubLevel == null ? null : SableSpacePhysics.resolveViewAlignmentSource(bodySubLevel);
        if (bodySubLevel == null || sourceSubLevel == null) {
            return null;
        }

        return SableSpacePhysics.getReferenceFrameOrientation(sourceSubLevel, bodySubLevel, entity, new Quaterniond());
    }

    public static boolean hasServerOrientation(final Entity entity) {
        return getServerOrientation(entity) != null;
    }
}