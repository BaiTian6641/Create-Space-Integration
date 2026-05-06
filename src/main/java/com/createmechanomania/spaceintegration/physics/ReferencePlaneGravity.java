package com.createmechanomania.spaceintegration.physics;

import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReferencePlaneGravity {
    private static final double MIN_ACCELERATION = 1.0E-6D;
    private static final double MIN_VECTOR = 1.0E-8D;
    private static final int SUPPORT_GRACE_TICKS = 4;
    private static final Map<UUID, SurfaceSupportState> SURFACE_SUPPORT_STATES = new ConcurrentHashMap<>();

    private ReferencePlaneGravity() {
    }

    public static void onEntityTick(final EntityTickEvent.Post event) {
        if (!SpaceIntegrationConfig.referencePlaneGravityEnabled()) {
            return;
        }

        final Entity entity = event.getEntity();
        if (!(entity.level() instanceof final ServerLevel level) || !SableSpacePhysics.isAdAstraZeroGravity(level)) {
            clearEntityState(entity);
            return;
        }
        if (shouldSkipEntity(entity)) {
            clearEntityState(entity);
            return;
        }

        final ServerSubLevel bodySubLevel = SableSpacePhysics.findEntitySubLevel(entity);
        final ServerSubLevel sourceSubLevel = bodySubLevel == null ? null : SableSpacePhysics.resolveReferencePlaneSource(bodySubLevel);
        if (bodySubLevel == null || sourceSubLevel == null) {
            clearEntityState(entity);
            return;
        }

        final Vector3d down = SableSpacePhysics.getArtificialGravityDown(sourceSubLevel, bodySubLevel, entity, new Vector3d());
        if (down == null || down.lengthSquared() <= MIN_VECTOR) {
            clearEntityState(entity);
            return;
        }
        down.normalize();

        final boolean surfaceSupport = rememberSurfaceSupport(entity, bodySubLevel, down);
        if (!(entity instanceof final LivingEntity livingEntity) || shouldSkipArtificialGravity(livingEntity)) {
            return;
        }

        final Vec3 currentDelta = entity.getDeltaMovement();
        final Vector3d velocity = new Vector3d(currentDelta.x, currentDelta.y, currentDelta.z);
        final boolean changed = applyArtificialDown(livingEntity, sourceSubLevel, down, surfaceSupport, velocity);

        livingEntity.fallDistance = 0.0F;
        if (changed) {
            livingEntity.setDeltaMovement(velocity.x, velocity.y, velocity.z);
            livingEntity.hasImpulse = true;
        }
    }

    public static boolean hasReferenceSurfaceSupport(final Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }

        final SurfaceSupportState state = SURFACE_SUPPORT_STATES.get(entity.getUUID());
        return state != null && state.remainingTicks > 0;
    }

    private static boolean applyArtificialDown(final LivingEntity entity, final ServerSubLevel sourceSubLevel, final Vector3dc down, final boolean surfaceSupport, final Vector3d velocity) {
        if (!surfaceSupport && !SpaceIntegrationConfig.freeFlightArtificialGravityEnabled()) {
            return false;
        }

        final double acceleration = SpaceIntegrationConfig.referencePlaneAcceleration() * SableSpacePhysics.getGravityStrength(sourceSubLevel);
        if (acceleration < MIN_ACCELERATION) {
            return false;
        }

        final double terminalSpeed = surfaceSupport
                ? SpaceIntegrationConfig.referencePlaneContactSpeed()
                : SpaceIntegrationConfig.referencePlaneTerminalSpeed();
        if (terminalSpeed <= MIN_ACCELERATION) {
            return false;
        }

        final double downSpeed = velocity.dot(down);
        if (downSpeed > terminalSpeed) {
            velocity.fma(terminalSpeed - downSpeed, down);
            return true;
        }
        if (downSpeed < terminalSpeed) {
            final double accelerationStep = Math.min(acceleration, terminalSpeed - downSpeed);
            velocity.fma(accelerationStep, down);
            return accelerationStep > MIN_ACCELERATION;
        }

        return false;
    }

    private static boolean rememberSurfaceSupport(final Entity entity, final ServerSubLevel bodySubLevel, final Vector3dc down) {
        final Vector3d desiredUp = new Vector3d(down).negate().normalize();
        final Vector3d supportUp = SurfaceContactFrame.getSupportContactUp(entity, bodySubLevel, desiredUp, null, new Vector3d());
        if (supportUp != null) {
            SURFACE_SUPPORT_STATES.put(entity.getUUID(), new SurfaceSupportState(bodySubLevel.getUniqueId(), SUPPORT_GRACE_TICKS));
            return true;
        }

        final SurfaceSupportState previousState = SURFACE_SUPPORT_STATES.get(entity.getUUID());
        if (previousState == null || !previousState.subLevelId.equals(bodySubLevel.getUniqueId()) || previousState.remainingTicks <= 0) {
            SURFACE_SUPPORT_STATES.remove(entity.getUUID());
            return false;
        }

        SURFACE_SUPPORT_STATES.put(entity.getUUID(), new SurfaceSupportState(bodySubLevel.getUniqueId(), previousState.remainingTicks - 1));
        return true;
    }

    private static boolean shouldSkipEntity(final Entity entity) {
        if (entity.isRemoved() || entity.isPassenger() || entity.isInWater() || entity.isInLava()) {
            return true;
        }
        return entity instanceof final Player player && (player.isSpectator() || player.getAbilities().flying);
    }

    private static boolean shouldSkipArtificialGravity(final LivingEntity entity) {
        return entity.isFallFlying() || entity.hasEffect(MobEffects.SLOW_FALLING);
    }

    private static void clearEntityState(final Entity entity) {
        SURFACE_SUPPORT_STATES.remove(entity.getUUID());
        ReferenceFrameUpMemory.clear(entity);
    }

    private record SurfaceSupportState(UUID subLevelId, int remainingTicks) {
    }
}
