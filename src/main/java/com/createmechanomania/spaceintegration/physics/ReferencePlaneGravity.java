package com.createmechanomania.spaceintegration.physics;

import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReferencePlaneGravity {
    private static final double MIN_ACCELERATION = 1.0E-6D;
    private static final double MIN_VECTOR = 1.0E-8D;
    private static final double MAX_CARRIER_STEP = 5.0D;
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

        final Vector3d supportUp = rememberSurfaceSupport(entity, bodySubLevel, down);
        final boolean surfaceSupport = supportUp != null;
        if (!(entity instanceof final LivingEntity livingEntity) || shouldSkipArtificialGravity(livingEntity)) {
            return;
        }

        final Vec3 currentDelta = entity.getDeltaMovement();
        final Vector3d velocity = new Vector3d(currentDelta.x, currentDelta.y, currentDelta.z);
        boolean changed = applyArtificialDown(livingEntity, sourceSubLevel, down, supportUp, velocity);
        if (surfaceSupport && !(livingEntity instanceof ServerPlayer)) {
            changed |= matchSupportedFrameVelocity(livingEntity, bodySubLevel, sourceSubLevel, supportUp, velocity);
        }

        livingEntity.fallDistance = 0.0F;
        if (changed) {
            livingEntity.setDeltaMovement(velocity.x, velocity.y, velocity.z);
            livingEntity.hasImpulse = true;
        }
        if (surfaceSupport && livingEntity instanceof final ServerPlayer serverPlayer) {
            carrySupportedPlayer(serverPlayer, bodySubLevel, sourceSubLevel, supportUp);
        }
    }

    public static boolean hasReferenceSurfaceSupport(final Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }

        final SurfaceSupportState state = SURFACE_SUPPORT_STATES.get(entity.getUUID());
        return state != null && state.remainingTicks > 0;
    }

    private static boolean applyArtificialDown(final LivingEntity entity, final ServerSubLevel sourceSubLevel, final Vector3dc down, @Nullable final Vector3dc supportUp, final Vector3d velocity) {
        final boolean surfaceSupport = supportUp != null;
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

        final Vector3d forceDown = surfaceSupport ? new Vector3d(supportUp).negate().normalize() : new Vector3d(down).normalize();
        final double downSpeed = velocity.dot(forceDown);
        if (downSpeed > terminalSpeed) {
            velocity.fma(terminalSpeed - downSpeed, forceDown);
            return true;
        }
        if (downSpeed < terminalSpeed) {
            final double accelerationStep = Math.min(acceleration, terminalSpeed - downSpeed);
            velocity.fma(accelerationStep, forceDown);
            return accelerationStep > MIN_ACCELERATION;
        }

        return false;
    }

    private static boolean matchSupportedFrameVelocity(final Entity entity, final ServerSubLevel bodySubLevel, final ServerSubLevel sourceSubLevel, final Vector3dc supportUp, final Vector3d velocity) {
        final Vector3d supportNormal = new Vector3d(supportUp);
        if (supportNormal.lengthSquared() <= MIN_VECTOR) {
            return false;
        }
        supportNormal.normalize();

        final Vector3d carrierVelocity = getCarrierFrameDelta(entity, bodySubLevel, sourceSubLevel, new Vector3d());
        projectOntoPlane(carrierVelocity, supportNormal);
        if (carrierVelocity.lengthSquared() <= MIN_VECTOR) {
            return false;
        }

        final Vector3d carrierDirection = new Vector3d(carrierVelocity).normalize();
        final Vector3d currentTangent = projectOntoPlane(new Vector3d(velocity), supportNormal);
        final double missingCarrierSpeed = carrierVelocity.dot(carrierDirection) - currentTangent.dot(carrierDirection);
        if (missingCarrierSpeed <= MIN_ACCELERATION) {
            return false;
        }

        final double correctionSpeed = Math.min(missingCarrierSpeed, SpaceIntegrationConfig.frameVelocityMatchLimit());
        if (correctionSpeed <= MIN_ACCELERATION) {
            return false;
        }

        velocity.fma(correctionSpeed, carrierDirection);
        return true;
    }

    private static boolean carrySupportedPlayer(final ServerPlayer player, final ServerSubLevel bodySubLevel, final ServerSubLevel sourceSubLevel, final Vector3dc supportUp) {
        final Vector3d supportNormal = new Vector3d(supportUp);
        if (supportNormal.lengthSquared() <= MIN_VECTOR) {
            return false;
        }
        supportNormal.normalize();

        final Vector3d carrierStep = getCarrierFrameDelta(player, bodySubLevel, sourceSubLevel, new Vector3d());
        projectOntoPlane(carrierStep, supportNormal);
        final double carrierStepLengthSquared = carrierStep.lengthSquared();
        if (carrierStepLengthSquared <= MIN_VECTOR || carrierStepLengthSquared > MAX_CARRIER_STEP * MAX_CARRIER_STEP) {
            return false;
        }

        final Vec3 requestedStep = new Vec3(carrierStep.x, carrierStep.y, carrierStep.z);
        final Vec3 resolvedStep = ((EntityExtension) player).sable$vanillaCollide(requestedStep);
        if (resolvedStep.lengthSqr() <= MIN_VECTOR) {
            return false;
        }

        player.setPos(player.position().add(resolvedStep));
        player.hasImpulse = true;
        return true;
    }

    private static Vector3d getCarrierFrameDelta(final Entity entity, final ServerSubLevel bodySubLevel, final ServerSubLevel sourceSubLevel, final Vector3d destination) {
        if (!sourceSubLevel.getUniqueId().equals(bodySubLevel.getUniqueId())) {
            getFrameDelta(entity, sourceSubLevel, destination);
            if (destination.lengthSquared() > MIN_VECTOR) {
                return destination;
            }
        }
        return getFrameDelta(entity, bodySubLevel, destination);
    }

    private static Vector3d getFrameDelta(final Entity entity, final ServerSubLevel subLevel, final Vector3d destination) {
        final Vector3d globalPosition = SableSpacePhysics.getGlobalPosition(entity, subLevel, new Vector3d());
        final Vector3d previousLocalPosition = subLevel.lastPose().transformPositionInverse(globalPosition, new Vector3d());
        subLevel.logicalPose().transformPosition(previousLocalPosition, destination);
        return destination.sub(globalPosition);
    }

    private static Vector3d projectOntoPlane(final Vector3d vector, final Vector3dc normal) {
        return vector.fma(-vector.dot(normal), normal);
    }

    private static @Nullable Vector3d rememberSurfaceSupport(final Entity entity, final ServerSubLevel bodySubLevel, final Vector3dc down) {
        final Vector3d desiredUp = new Vector3d(down).negate().normalize();
        final Vector3d supportUp = SurfaceContactFrame.getSupportContactUp(entity, bodySubLevel, desiredUp, null, new Vector3d());
        if (supportUp != null) {
            SURFACE_SUPPORT_STATES.put(entity.getUUID(), new SurfaceSupportState(bodySubLevel.getUniqueId(), SUPPORT_GRACE_TICKS, new Vector3d(supportUp)));
            return supportUp;
        }

        final SurfaceSupportState previousState = SURFACE_SUPPORT_STATES.get(entity.getUUID());
        if (previousState == null || !previousState.subLevelId.equals(bodySubLevel.getUniqueId()) || previousState.remainingTicks <= 0) {
            SURFACE_SUPPORT_STATES.remove(entity.getUUID());
            return null;
        }

        SURFACE_SUPPORT_STATES.put(entity.getUUID(), new SurfaceSupportState(bodySubLevel.getUniqueId(), previousState.remainingTicks - 1, new Vector3d(previousState.supportUp)));
        return new Vector3d(previousState.supportUp);
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

    private record SurfaceSupportState(UUID subLevelId, int remainingTicks, Vector3d supportUp) {
    }
}
