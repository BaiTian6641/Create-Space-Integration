package com.createmechanomania.spaceintegration.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReferenceFrameUpMemory {
    private static final int SUPPORT_GRACE_TICKS = 4;
    private static final double MIN_VECTOR = 1.0E-8D;
    private static final double MIN_STORED_SUPPORT_ALIGNMENT = 0.35D;
    private static final double CLIENT_SMOOTHING = 0.38D;
    private static final Map<UUID, UpState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, UpState> CLIENT_STATES = new ConcurrentHashMap<>();

    private ReferenceFrameUpMemory() {
    }

    public static @Nullable Vector3d findServerSupportUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return findSupportUp(SERVER_STATES, entity, subLevel, desiredUp, destination);
    }

    public static @Nullable Vector3d findClientSupportUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return findSupportUp(CLIENT_STATES, entity, subLevel, desiredUp, destination);
    }

    public static Vector3d selectServerUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return selectUp(SERVER_STATES, entity, subLevel, desiredUp, false, destination);
    }

    public static Vector3d selectClientUp(final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        return selectUp(CLIENT_STATES, entity, subLevel, desiredUp, true, destination);
    }

    public static void clear(final Entity entity) {
        SERVER_STATES.remove(entity.getUUID());
        CLIENT_STATES.remove(entity.getUUID());
    }

    private static @Nullable Vector3d findSupportUp(final Map<UUID, UpState> states, final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final Vector3d destination) {
        if (desiredUp.lengthSquared() <= MIN_VECTOR) {
            return null;
        }
        final UpState state = states.get(entity.getUUID());
        final Vector3dc previousUp = state != null && state.isSameSubLevel(subLevel) ? state.supportUp : null;
        return SurfaceContactFrame.getSupportContactUp(entity, subLevel, desiredUp, previousUp, destination);
    }

    private static Vector3d selectUp(final Map<UUID, UpState> states, final Entity entity, final SubLevel subLevel, final Vector3dc desiredUp, final boolean smooth, final Vector3d destination) {
        final Vector3d normalizedDesiredUp = new Vector3d(desiredUp);
        if (normalizedDesiredUp.lengthSquared() <= MIN_VECTOR) {
            return destination.set(0.0D, 1.0D, 0.0D);
        }
        normalizedDesiredUp.normalize();

        final UpState state = states.computeIfAbsent(entity.getUUID(), ignored -> new UpState());
        state.beginTick(entity.tickCount);
        if (!state.isSameSubLevel(subLevel)) {
            state.resetFor(subLevel);
        }

        final Vector3d supportUp = SurfaceContactFrame.getSupportContactUp(entity, subLevel, normalizedDesiredUp, state.supportUp, new Vector3d());
        final Vector3d targetUp;
        if (supportUp != null) {
            state.rememberSupport(supportUp);
            targetUp = supportUp;
        } else if (state.supportGraceTicks > 0 && state.supportUp.lengthSquared() > MIN_VECTOR && state.supportUp.dot(normalizedDesiredUp) > MIN_STORED_SUPPORT_ALIGNMENT) {
            targetUp = new Vector3d(state.supportUp).normalize();
        } else {
            targetUp = normalizedDesiredUp;
        }

        if (!smooth || state.smoothedUp.lengthSquared() <= MIN_VECTOR || state.smoothedUp.dot(targetUp) < 0.0D) {
            state.smoothedUp.set(targetUp).normalize();
            return destination.set(state.smoothedUp);
        }

        state.smoothedUp.lerp(targetUp, CLIENT_SMOOTHING).normalize();
        return destination.set(state.smoothedUp);
    }

    private static final class UpState {
        private @Nullable UUID subLevelId;
        private final Vector3d supportUp = new Vector3d();
        private final Vector3d smoothedUp = new Vector3d();
        private int supportGraceTicks;
        private int lastTick = Integer.MIN_VALUE;
        private boolean sawSupportThisTick;

        private void beginTick(final int tick) {
            if (this.lastTick == tick) {
                return;
            }
            if (this.lastTick != Integer.MIN_VALUE && !this.sawSupportThisTick && this.supportGraceTicks > 0) {
                this.supportGraceTicks--;
            }
            this.sawSupportThisTick = false;
            this.lastTick = tick;
        }

        private boolean isSameSubLevel(final SubLevel subLevel) {
            return this.subLevelId != null && this.subLevelId.equals(subLevel.getUniqueId());
        }

        private void resetFor(final SubLevel subLevel) {
            this.subLevelId = subLevel.getUniqueId();
            this.supportUp.zero();
            this.smoothedUp.zero();
            this.supportGraceTicks = 0;
            this.sawSupportThisTick = false;
        }

        private void rememberSupport(final Vector3dc up) {
            this.supportUp.set(up).normalize();
            this.supportGraceTicks = SUPPORT_GRACE_TICKS;
            this.sawSupportThisTick = true;
        }
    }
}