package com.createmechanomania.spaceintegration.physics;

import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import com.createmechanomania.spaceintegration.network.SpaceIntegrationNetwork;
import com.createmechanomania.spaceintegration.network.packet.ClientboundSubLevelViewAlignmentPacket;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class SableSpacePhysics {
    private static final String ROOT_TAG = "create_space_integration";
    private static final String OPT_IN_TAG = "opt_in";
    private static final String STATION_KEEPING_TAG = "station_keeping";
    private static final String REFERENCE_PLANE_TAG = "reference_plane";
    private static final String VIEW_ALIGNMENT_TAG = "view_alignment";
    private static final String GRAVITY_MODE_TAG = "gravity_mode";
    private static final String GRAVITY_STRENGTH_TAG = "gravity_strength";
    private static final String GRAVITY_RADIUS_TAG = "gravity_radius";
    private static final String GRAVITY_AXIS_ORIGIN_TAG = "gravity_axis_origin";
    private static final String GRAVITY_AXIS_DIRECTION_TAG = "gravity_axis_direction";
    private static final double STATION_KEEPING_DAMPING = 0.35;
    private static final double MAX_DAMPING_STEP = 0.18;
    private static final Map<ServerLevel, LevelTracker> TRACKERS = new ConcurrentHashMap<>();

    private SableSpacePhysics() {
    }

    public static void register() {
        SableEventPlatform.INSTANCE.onSubLevelContainerReady(SableSpacePhysics::onContainerReady);
        SableEventPlatform.INSTANCE.onPhysicsTick(SableSpacePhysics::onPrePhysicsTick);
    }

    private static void onContainerReady(final net.minecraft.world.level.Level level, final SubLevelContainer container) {
        if (level instanceof final ServerLevel serverLevel && container instanceof final ServerSubLevelContainer serverContainer) {
            final LevelTracker tracker = new LevelTracker(serverLevel);
            TRACKERS.put(serverLevel, tracker);
            serverContainer.addObserver(tracker);
        }
    }

    private static void onPrePhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        final ServerLevel level = physicsSystem.getLevel();
        final LevelTracker tracker = TRACKERS.get(level);
        if (tracker == null) {
            return;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        tracker.resolvePending(container);
        tracker.applyForces(physicsSystem, container, timeStep);
    }

    public static boolean markOptIn(final ServerSubLevel subLevel, final boolean stationKeeping) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        integrationTag.putBoolean(STATION_KEEPING_TAG, stationKeeping);
        integrationTag.putBoolean(REFERENCE_PLANE_TAG, true);
        integrationTag.putBoolean(VIEW_ALIGNMENT_TAG, SpaceIntegrationConfig.viewAlignmentDefaultEnabled());
        ensureDefaultFeatureTags(integrationTag);
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean markOptOut(final ServerSubLevel subLevel) {
        final CompoundTag userData = subLevel.getUserDataTag();
        if (userData != null) {
            userData.remove(ROOT_TAG);
        }
        final LevelTracker tracker = getTracker(subLevel);
        tracker.optedIn.remove(subLevel.getUniqueId());
        tracker.pending.remove(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean configureStationGravity(
            final ServerSubLevel subLevel,
            final boolean stationKeeping,
            final boolean referencePlane,
            final boolean viewAlignment,
            final GravityMode gravityMode,
            final double gravityStrength,
            final double gravityRadius,
            final Vector3dc axisOrigin,
            final Vector3dc axisDirection
    ) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        integrationTag.putBoolean(STATION_KEEPING_TAG, stationKeeping);
        integrationTag.putBoolean(REFERENCE_PLANE_TAG, referencePlane);
        integrationTag.putBoolean(VIEW_ALIGNMENT_TAG, viewAlignment);
        integrationTag.putString(GRAVITY_MODE_TAG, gravityMode.id());
        integrationTag.putDouble(GRAVITY_STRENGTH_TAG, clamp(gravityStrength, 0.0D, 4.0D));
        integrationTag.putDouble(GRAVITY_RADIUS_TAG, clamp(gravityRadius, 0.0D, 512.0D));
        putVector(integrationTag, GRAVITY_AXIS_ORIGIN_TAG, axisOrigin);
        putVector(integrationTag, GRAVITY_AXIS_DIRECTION_TAG, normalizeAxis(axisDirection));
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setGravityMode(final ServerSubLevel subLevel, final GravityMode gravityMode) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        ensureDefaultFeatureTags(integrationTag);
        integrationTag.putString(GRAVITY_MODE_TAG, gravityMode.id());
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setGravityStrength(final ServerSubLevel subLevel, final double gravityStrength) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        ensureDefaultFeatureTags(integrationTag);
        integrationTag.putDouble(GRAVITY_STRENGTH_TAG, clamp(gravityStrength, 0.0D, 4.0D));
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setGravityRadius(final ServerSubLevel subLevel, final double gravityRadius) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        ensureDefaultFeatureTags(integrationTag);
        integrationTag.putDouble(GRAVITY_RADIUS_TAG, clamp(gravityRadius, 0.0D, 512.0D));
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setGravityAxis(final ServerSubLevel subLevel, final Vector3dc axisOrigin, final Vector3dc axisDirection) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        ensureDefaultFeatureTags(integrationTag);
        putVector(integrationTag, GRAVITY_AXIS_ORIGIN_TAG, axisOrigin);
        putVector(integrationTag, GRAVITY_AXIS_DIRECTION_TAG, normalizeAxis(axisDirection));
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setStationKeeping(final ServerSubLevel subLevel, final boolean enabled) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        integrationTag.putBoolean(STATION_KEEPING_TAG, enabled);
        ensureDefaultFeatureTags(integrationTag);
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        return true;
    }

    public static boolean setReferencePlane(final ServerSubLevel subLevel, final boolean enabled) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        integrationTag.putBoolean(REFERENCE_PLANE_TAG, enabled);
        ensureDefaultFeatureTags(integrationTag);
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean setViewAlignment(final ServerSubLevel subLevel, final boolean enabled) {
        final CompoundTag integrationTag = getOrCreateIntegrationTag(subLevel);
        integrationTag.putBoolean(OPT_IN_TAG, true);
        integrationTag.putBoolean(VIEW_ALIGNMENT_TAG, enabled);
        ensureDefaultFeatureTags(integrationTag);
        getTracker(subLevel).optedIn.add(subLevel.getUniqueId());
        syncViewAlignmentState(subLevel);
        return true;
    }

    public static boolean isOptedIn(final ServerSubLevel subLevel) {
        return getIntegrationTag(subLevel) != null && getIntegrationTag(subLevel).getBoolean(OPT_IN_TAG);
    }

    public static boolean isStationKeeping(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return integrationTag != null && integrationTag.getBoolean(STATION_KEEPING_TAG);
    }

    public static boolean isReferencePlane(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return integrationTag != null && integrationTag.getBoolean(OPT_IN_TAG) && getBooleanOrDefault(integrationTag, REFERENCE_PLANE_TAG, true);
    }

    public static boolean isViewAlignment(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return integrationTag != null && integrationTag.getBoolean(OPT_IN_TAG) && getBooleanOrDefault(integrationTag, VIEW_ALIGNMENT_TAG, true);
    }

    public static boolean isViewAlignmentActive(final ServerSubLevel subLevel) {
        return isAdAstraZeroGravity(subLevel.getLevel()) && isOptedIn(subLevel) && isReferencePlane(subLevel) && isViewAlignment(subLevel);
    }

    public static GravityMode getGravityMode(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        if (integrationTag == null || !integrationTag.contains(GRAVITY_MODE_TAG, Tag.TAG_STRING)) {
            return GravityMode.RADIAL;
        }
        return GravityMode.byId(integrationTag.getString(GRAVITY_MODE_TAG));
    }

    public static double getGravityStrength(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return integrationTag != null && integrationTag.contains(GRAVITY_STRENGTH_TAG, Tag.TAG_DOUBLE)
                ? integrationTag.getDouble(GRAVITY_STRENGTH_TAG)
                : 1.0D;
    }

    public static double getGravityRadius(final ServerSubLevel subLevel) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return integrationTag != null && integrationTag.contains(GRAVITY_RADIUS_TAG, Tag.TAG_DOUBLE)
                ? integrationTag.getDouble(GRAVITY_RADIUS_TAG)
                : SpaceIntegrationConfig.stationControllerDefaultRadius();
    }

    public static Vector3d getGravityAxisOrigin(final ServerSubLevel subLevel, final Vector3d destination) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return readVector(integrationTag, GRAVITY_AXIS_ORIGIN_TAG, destination.zero());
    }

    public static Vector3d getGravityAxisDirection(final ServerSubLevel subLevel, final Vector3d destination) {
        final CompoundTag integrationTag = getIntegrationTag(subLevel);
        return readVector(integrationTag, GRAVITY_AXIS_DIRECTION_TAG, destination.set(0.0D, 1.0D, 0.0D));
    }

    public static @Nullable ServerSubLevel findPlayerSubLevel(final net.minecraft.server.level.ServerPlayer player) {
        return findEntitySubLevel(player);
    }

    public static @Nullable ServerSubLevel findReferencePlaneSourceSubLevel(final Entity entity) {
        final ServerSubLevel subLevel = findEntitySubLevel(entity);
        return subLevel == null ? null : resolveReferencePlaneSource(subLevel);
    }

    public static @Nullable ServerSubLevel resolveReferencePlaneSource(final ServerSubLevel subLevel) {
        return resolveAttachedSource(subLevel, candidate -> isOptedIn(candidate) && isReferencePlane(candidate));
    }

    public static @Nullable ServerSubLevel resolveViewAlignmentSource(final ServerSubLevel subLevel) {
        return resolveAttachedSource(subLevel, SableSpacePhysics::isViewAlignmentActive);
    }

    public static @Nullable ServerSubLevel resolveStationKeepingSource(final ServerSubLevel subLevel) {
        return resolveAttachedSource(subLevel, candidate -> isOptedIn(candidate) && isStationKeeping(candidate));
    }

    private static @Nullable ServerSubLevel resolveAttachedSource(final ServerSubLevel subLevel, final Predicate<ServerSubLevel> predicate) {
        if (subLevel.isRemoved()) {
            return null;
        }
        if (predicate.test(subLevel)) {
            return subLevel;
        }

        final LevelTracker tracker = TRACKERS.get(subLevel.getLevel());
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (tracker == null || container == null) {
            return null;
        }
        return tracker.resolveAttachedSource(container, subLevel, predicate);
    }

    public static void rememberDockConnection(final BlockEntity firstConnector, final BlockEntity secondConnector) {
        if (!(firstConnector.getLevel() instanceof final ServerLevel level) || secondConnector.getLevel() != level) {
            return;
        }

        final ServerSubLevel firstSubLevel = findBlockEntitySubLevel(firstConnector);
        final ServerSubLevel secondSubLevel = findBlockEntitySubLevel(secondConnector);
        if (firstSubLevel == null || secondSubLevel == null || firstSubLevel.getUniqueId().equals(secondSubLevel.getUniqueId())) {
            return;
        }

        getTracker(firstSubLevel).rememberDockConnection(firstSubLevel, firstConnector.getBlockPos(), secondSubLevel, secondConnector.getBlockPos());
    }

    public static void forgetDockConnection(final BlockEntity connector, @Nullable final UUID otherSubLevelId, @Nullable final BlockPos otherConnectorPosition) {
        if (!(connector.getLevel() instanceof ServerLevel)) {
            return;
        }

        final ServerSubLevel subLevel = findBlockEntitySubLevel(connector);
        if (subLevel == null) {
            return;
        }

        getTracker(subLevel).forgetDockConnection(subLevel, connector.getBlockPos(), otherSubLevelId, otherConnectorPosition);
    }

    public static @Nullable ServerSubLevel findEntitySubLevel(final Entity entity) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        if (!(subLevel instanceof ServerSubLevel)) {
            subLevel = Sable.HELPER.getContaining(entity);
        }
        if (!(subLevel instanceof ServerSubLevel)) {
            subLevel = findCollisionSubLevel(entity);
        }
        return subLevel instanceof final ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    private static @Nullable ServerSubLevel findCollisionSubLevel(final Entity entity) {
        if (!(entity instanceof final EntityMovementExtension movementExtension)) {
            return null;
        }
        final SubLevelEntityCollision.CollisionInfo collisionInfo = movementExtension.sable$getCollisionInfo();
        if (collisionInfo == null) {
            return null;
        }
        if (collisionInfo.trackingSubLevel instanceof final ServerSubLevel trackingSubLevel && !trackingSubLevel.isRemoved()) {
            return trackingSubLevel;
        }
        if (collisionInfo.firstCollisions == null || collisionInfo.firstCollisions.isEmpty()) {
            return null;
        }

        for (final SubLevel collidedSubLevel : collisionInfo.firstCollisions.keySet()) {
            if (collidedSubLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
                return serverSubLevel;
            }
        }
        return null;
    }

    private static @Nullable ServerSubLevel findBlockEntitySubLevel(final BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel)) {
            return null;
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        return subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() ? serverSubLevel : null;
    }

    public static Vector3d getReferencePlaneDown(final ServerSubLevel subLevel, final Vector3d destination) {
        destination.set(0.0D, -1.0D, 0.0D);
        subLevel.logicalPose().orientation().transform(destination);
        return destination.normalize();
    }

    public static @Nullable Vector3d getArtificialGravityDown(final ServerSubLevel subLevel, final Entity entity, final Vector3d destination) {
        return getArtificialGravityDown(subLevel, subLevel, entity, destination);
    }

    public static @Nullable Vector3d getArtificialGravityDown(final ServerSubLevel sourceSubLevel, final ServerSubLevel bodySubLevel, final Entity entity, final Vector3d destination) {
        final GravityMode gravityMode = getGravityMode(sourceSubLevel);
        final boolean resolved = GravityMath.getGravityDown(
                sourceSubLevel.logicalPose(),
                gravityMode,
                getGravityAxisOrigin(sourceSubLevel, new Vector3d()),
                getGravityAxisDirection(sourceSubLevel, new Vector3d()),
                getGravityRadius(sourceSubLevel),
                getLocalPosition(entity, sourceSubLevel, new Vector3d()),
                destination
        );
        return resolved ? destination : null;
    }

    public static @Nullable Vector3d getSurfaceContactDown(final ServerSubLevel subLevel, final Entity entity, final Vector3dc configuredDown, final Vector3d destination) {
        if (configuredDown.lengthSquared() < 1.0E-8D) {
            return null;
        }

        final Vector3d desiredUp = new Vector3d(configuredDown).negate().normalize();
        final Vector3d contactUp = ReferenceFrameUpMemory.findServerSupportUp(entity, subLevel, desiredUp, new Vector3d());
        return contactUp == null ? null : destination.set(contactUp).negate().normalize();
    }

    public static @Nullable Quaterniond getReferenceFrameOrientation(final ServerSubLevel subLevel, final Entity entity, final Quaterniond destination) {
        return getReferenceFrameOrientation(subLevel, subLevel, entity, destination);
    }

    public static @Nullable Quaterniond getReferenceFrameOrientation(final ServerSubLevel sourceSubLevel, final ServerSubLevel bodySubLevel, final Entity entity, final Quaterniond destination) {
        final Vector3d down = getArtificialGravityDown(sourceSubLevel, bodySubLevel, entity, new Vector3d());
        if (down == null) {
            return null;
        }

        final Vector3d targetUp = down.negate(new Vector3d()).normalize();
        return GravityMath.getReferenceFrameOrientationFromUp(bodySubLevel.logicalPose(), targetUp, destination) ? destination : null;
    }

    public static Vector3d getLocalPosition(final Entity entity, final ServerSubLevel subLevel, final Vector3d destination) {
        final Vec3 entityPosition = entity.position();
        destination.set(entityPosition.x, entityPosition.y, entityPosition.z);
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(entity.level(), destination);
        if (containingSubLevel != null && containingSubLevel.getUniqueId().equals(subLevel.getUniqueId())) {
            return destination;
        }
        if (containingSubLevel != null) {
            containingSubLevel.logicalPose().transformPosition(destination, destination);
        }
        return subLevel.logicalPose().transformPositionInverse(destination, destination);
    }

    public static Vector3d getGlobalPosition(final Entity entity, final ServerSubLevel subLevel, final Vector3d destination) {
        final Vec3 entityPosition = entity.position();
        destination.set(entityPosition.x, entityPosition.y, entityPosition.z);
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(entity.level(), destination);
        if (containingSubLevel != null) {
            return containingSubLevel.logicalPose().transformPosition(destination, destination);
        }
        return destination;
    }

    public static @Nullable FrameMotion getFrameMotion(final ServerSubLevel subLevel) {
        final LevelTracker tracker = TRACKERS.get(subLevel.getLevel());
        if (tracker == null) {
            return null;
        }
        final FrameMotion motion = tracker.frameMotions.get(subLevel.getUniqueId());
        return motion != null && motion.isInitialized() ? motion : null;
    }

    private static LevelTracker getTracker(final ServerSubLevel subLevel) {
        return TRACKERS.computeIfAbsent(subLevel.getLevel(), LevelTracker::new);
    }

    private static @Nullable CompoundTag getIntegrationTag(final ServerSubLevel subLevel) {
        final CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null || !userData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return userData.getCompound(ROOT_TAG);
    }

    private static CompoundTag getOrCreateIntegrationTag(final ServerSubLevel subLevel) {
        CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null) {
            userData = new CompoundTag();
            subLevel.setUserDataTag(userData);
        }
        if (!userData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            userData.put(ROOT_TAG, new CompoundTag());
        }
        return userData.getCompound(ROOT_TAG);
    }

    public static boolean isAdAstraZeroGravity(final ServerLevel level) {
        final ResourceLocation dimension = level.dimension().location();
        return "ad_astra".equals(dimension.getNamespace()) && DimensionPhysicsData.getGravity(level).lengthSquared() < 1.0E-6;
    }

    private static void ensureDefaultFeatureTags(final CompoundTag integrationTag) {
        if (!integrationTag.contains(STATION_KEEPING_TAG, Tag.TAG_BYTE)) {
            integrationTag.putBoolean(STATION_KEEPING_TAG, false);
        }
        if (!integrationTag.contains(REFERENCE_PLANE_TAG, Tag.TAG_BYTE)) {
            integrationTag.putBoolean(REFERENCE_PLANE_TAG, true);
        }
        if (!integrationTag.contains(VIEW_ALIGNMENT_TAG, Tag.TAG_BYTE)) {
            integrationTag.putBoolean(VIEW_ALIGNMENT_TAG, SpaceIntegrationConfig.viewAlignmentDefaultEnabled());
        }
        if (!integrationTag.contains(GRAVITY_MODE_TAG, Tag.TAG_STRING)) {
            integrationTag.putString(GRAVITY_MODE_TAG, GravityMode.RADIAL.id());
        }
        if (!integrationTag.contains(GRAVITY_STRENGTH_TAG, Tag.TAG_DOUBLE)) {
            integrationTag.putDouble(GRAVITY_STRENGTH_TAG, 1.0D);
        }
        if (!integrationTag.contains(GRAVITY_RADIUS_TAG, Tag.TAG_DOUBLE)) {
            integrationTag.putDouble(GRAVITY_RADIUS_TAG, SpaceIntegrationConfig.stationControllerDefaultRadius());
        }
        if (!integrationTag.contains(GRAVITY_AXIS_ORIGIN_TAG, Tag.TAG_COMPOUND)) {
            putVector(integrationTag, GRAVITY_AXIS_ORIGIN_TAG, new Vector3d());
        }
        if (!integrationTag.contains(GRAVITY_AXIS_DIRECTION_TAG, Tag.TAG_COMPOUND)) {
            putVector(integrationTag, GRAVITY_AXIS_DIRECTION_TAG, new Vector3d(0.0D, 1.0D, 0.0D));
        }
    }

    private static boolean getBooleanOrDefault(final CompoundTag tag, final String key, final boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vector3d normalizeAxis(final Vector3dc axisDirection) {
        final Vector3d normalized = new Vector3d(axisDirection);
        if (normalized.lengthSquared() < 1.0E-8D) {
            return normalized.set(0.0D, 1.0D, 0.0D);
        }
        return normalized.normalize();
    }

    private static void putVector(final CompoundTag tag, final String key, final Vector3dc vector) {
        final CompoundTag vectorTag = new CompoundTag();
        vectorTag.putDouble("x", vector.x());
        vectorTag.putDouble("y", vector.y());
        vectorTag.putDouble("z", vector.z());
        tag.put(key, vectorTag);
    }

    private static Vector3d readVector(@Nullable final CompoundTag tag, final String key, final Vector3d fallback) {
        if (tag == null || !tag.contains(key, Tag.TAG_COMPOUND)) {
            return fallback;
        }
        final CompoundTag vectorTag = tag.getCompound(key);
        return fallback.set(vectorTag.getDouble("x"), vectorTag.getDouble("y"), vectorTag.getDouble("z"));
    }

    private static void syncViewAlignmentState(final ServerSubLevel subLevel) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container != null) {
            syncViewAlignmentComponent(container, subLevel);
            return;
        }
        syncViewAlignmentState(subLevel, subLevel, isViewAlignmentActive(subLevel));
    }

    private static void syncViewAlignmentState(final ServerSubLevel subLevel, final boolean enabled) {
        syncViewAlignmentState(subLevel, subLevel, enabled);
    }

    private static void syncViewAlignmentState(final ServerSubLevel targetSubLevel, final ServerSubLevel sourceSubLevel, final boolean enabled) {
        final LevelTracker tracker = TRACKERS.get(targetSubLevel.getLevel());
        if (tracker != null) {
            tracker.rememberViewTarget(targetSubLevel.getUniqueId(), enabled);
        }
        SpaceIntegrationNetwork.sendToClients(new ClientboundSubLevelViewAlignmentPacket(
                targetSubLevel.getUniqueId(),
                sourceSubLevel.getUniqueId(),
                enabled,
                getGravityMode(sourceSubLevel),
                getGravityRadius(sourceSubLevel),
                getViewAlignmentAxisOrigin(sourceSubLevel, new Vector3d()),
                getViewAlignmentAxisDirection(sourceSubLevel, new Vector3d())
        ));
    }

    private static void syncViewAlignmentDisabled(final UUID subLevelId) {
        SpaceIntegrationNetwork.sendToClients(new ClientboundSubLevelViewAlignmentPacket(
                subLevelId,
                subLevelId,
                false,
                GravityMode.LOCAL_DOWN,
                0.0D,
                new Vector3d(),
                new Vector3d(0.0D, 1.0D, 0.0D)
        ));
    }

    private static void syncViewAlignmentComponent(final ServerSubLevelContainer container, final ServerSubLevel seedSubLevel) {
        final LevelTracker tracker = TRACKERS.get(seedSubLevel.getLevel());
        if (tracker == null) {
            syncResolvedViewAlignmentState(seedSubLevel);
            return;
        }

        for (final ServerSubLevel subLevel : tracker.getDockComponent(container, seedSubLevel)) {
            syncResolvedViewAlignmentState(subLevel);
        }
    }

    private static boolean syncResolvedViewAlignmentState(final ServerSubLevel targetSubLevel) {
        final ServerSubLevel sourceSubLevel = resolveViewAlignmentSource(targetSubLevel);
        if (sourceSubLevel == null) {
            syncViewAlignmentState(targetSubLevel, false);
            return false;
        }
        syncViewAlignmentState(targetSubLevel, sourceSubLevel, true);
        return true;
    }

    private static Vector3d getViewAlignmentAxisOrigin(final ServerSubLevel subLevel, final Vector3d destination) {
        return getGravityAxisOrigin(subLevel, destination);
    }

    private static Vector3d getViewAlignmentAxisDirection(final ServerSubLevel subLevel, final Vector3d destination) {
        return getGravityAxisDirection(subLevel, destination);
    }

    public static final class FrameMotion {
        private final Vector3d linearVelocity = new Vector3d();
        private final Vector3d angularVelocity = new Vector3d();
        private final Vector3d angularAcceleration = new Vector3d();
        private boolean initialized;

        private synchronized void capture(final RigidBodyHandle handle, final double timeStep) {
            final Vector3d previousAngularVelocity = new Vector3d(this.angularVelocity);
            handle.getLinearVelocity(this.linearVelocity);
            handle.getAngularVelocity(this.angularVelocity);

            if (this.initialized && timeStep > 1.0E-6D) {
                this.angularAcceleration.set(this.angularVelocity).sub(previousAngularVelocity).div(timeStep);
            } else {
                this.angularAcceleration.zero();
            }
            this.initialized = true;
        }

        private synchronized void clear() {
            this.linearVelocity.zero();
            this.angularVelocity.zero();
            this.angularAcceleration.zero();
            this.initialized = false;
        }

        public synchronized boolean isInitialized() {
            return this.initialized;
        }

        public synchronized Vector3d linearVelocity(final Vector3d destination) {
            return destination.set(this.linearVelocity);
        }

        public synchronized Vector3d angularVelocity(final Vector3d destination) {
            return destination.set(this.angularVelocity);
        }

        public synchronized Vector3d angularAcceleration(final Vector3d destination) {
            return destination.set(this.angularAcceleration);
        }
    }

    private static final class LevelTracker implements SubLevelObserver {
        private final ServerLevel level;
        private final Set<UUID> optedIn = ConcurrentHashMap.newKeySet();
        private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
        private final Set<UUID> syncedViewTargets = ConcurrentHashMap.newKeySet();
        private final Set<DockConnection> dockConnections = ConcurrentHashMap.newKeySet();
        private final Map<UUID, FrameMotion> frameMotions = new ConcurrentHashMap<>();
        private int syncTicks;

        private LevelTracker(final ServerLevel level) {
            this.level = level;
        }

        private void rememberViewTarget(final UUID subLevelId, final boolean enabled) {
            if (enabled) {
                this.syncedViewTargets.add(subLevelId);
            } else {
                this.syncedViewTargets.remove(subLevelId);
            }
        }

        @Override
        public void onSubLevelAdded(final SubLevel subLevel) {
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                this.pending.add(serverSubLevel.getUniqueId());
                if (isOptedIn(serverSubLevel)) {
                    this.optedIn.add(serverSubLevel.getUniqueId());
                    syncViewAlignmentState(serverSubLevel);
                }
            }
        }

        @Override
        public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                    syncViewAlignmentState(serverSubLevel, false);
                }
                this.optedIn.remove(subLevel.getUniqueId());
                this.pending.remove(subLevel.getUniqueId());
                this.syncedViewTargets.remove(subLevel.getUniqueId());
                this.frameMotions.remove(subLevel.getUniqueId());
                this.forgetDockConnectionsFor(subLevel.getUniqueId());
            }
        }

        private void rememberDockConnection(final ServerSubLevel firstSubLevel, final BlockPos firstConnectorPosition, final ServerSubLevel secondSubLevel, final BlockPos secondConnectorPosition) {
            final DockConnection connection = DockConnection.of(firstSubLevel.getUniqueId(), firstConnectorPosition, secondSubLevel.getUniqueId(), secondConnectorPosition);
            if (this.dockConnections.add(connection)) {
                final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
                if (container != null) {
                    syncViewAlignmentComponent(container, firstSubLevel);
                    syncViewAlignmentComponent(container, secondSubLevel);
                }
            }
        }

        private void forgetDockConnection(final ServerSubLevel subLevel, final BlockPos connectorPosition, @Nullable final UUID otherSubLevelId, @Nullable final BlockPos otherConnectorPosition) {
            final boolean removed;
            if (otherSubLevelId != null && otherConnectorPosition != null) {
                removed = this.dockConnections.remove(DockConnection.of(subLevel.getUniqueId(), connectorPosition, otherSubLevelId, otherConnectorPosition));
            } else {
                removed = this.dockConnections.removeIf(connection -> connection.containsEndpoint(subLevel.getUniqueId(), connectorPosition));
            }

            if (removed) {
                final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
                if (container != null) {
                    syncViewAlignmentComponent(container, subLevel);
                }
            }
        }

        private void forgetDockConnectionsFor(final UUID subLevelId) {
            this.dockConnections.removeIf(connection -> connection.containsSubLevel(subLevelId));
        }

        private @Nullable ServerSubLevel resolveAttachedSource(final ServerSubLevelContainer container, final ServerSubLevel seedSubLevel, final Predicate<ServerSubLevel> predicate) {
            for (final ServerSubLevel subLevel : this.getDockComponent(container, seedSubLevel)) {
                if (predicate.test(subLevel)) {
                    return subLevel;
                }
            }
            return null;
        }

        private List<ServerSubLevel> getDockComponent(final ServerSubLevelContainer container, final ServerSubLevel seedSubLevel) {
            final List<ServerSubLevel> component = new ArrayList<>();
            final Set<UUID> visited = new HashSet<>();
            final ArrayDeque<UUID> queue = new ArrayDeque<>();
            queue.add(seedSubLevel.getUniqueId());

            while (!queue.isEmpty()) {
                final UUID subLevelId = queue.removeFirst();
                if (!visited.add(subLevelId)) {
                    continue;
                }

                final SubLevel subLevel = container.getSubLevel(subLevelId);
                if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                    continue;
                }
                component.add(serverSubLevel);

                for (final UUID neighborId : this.getDockNeighbors(subLevelId)) {
                    if (!visited.contains(neighborId)) {
                        queue.add(neighborId);
                    }
                }
            }

            return component;
        }

        private List<UUID> getDockNeighbors(final UUID subLevelId) {
            final List<UUID> neighbors = new ArrayList<>();
            for (final DockConnection connection : this.dockConnections) {
                final UUID neighbor = connection.otherSubLevel(subLevelId);
                if (neighbor != null) {
                    neighbors.add(neighbor);
                }
            }
            neighbors.sort(Comparator.comparing(UUID::toString));
            return neighbors;
        }

        private void resolvePending(final ServerSubLevelContainer container) {
            final Iterator<UUID> pendingIterator = this.pending.iterator();
            while (pendingIterator.hasNext()) {
                final UUID subLevelId = pendingIterator.next();
                final SubLevel subLevel = container.getSubLevel(subLevelId);
                if (!(subLevel instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                    pendingIterator.remove();
                    continue;
                }

                if (isOptedIn(serverSubLevel)) {
                    this.optedIn.add(subLevelId);
                    pendingIterator.remove();
                    continue;
                }

                final UUID splitParentId = serverSubLevel.getSplitFromSubLevel();
                if (splitParentId != null) {
                    final SubLevel parent = container.getSubLevel(splitParentId);
                    final boolean parentOptedIn = this.optedIn.contains(splitParentId) || parent instanceof final ServerSubLevel parentSubLevel && isOptedIn(parentSubLevel);
                    if (parentOptedIn) {
                        if (parent instanceof final ServerSubLevel parentSubLevel) {
                            inheritIntegrationState(serverSubLevel, parentSubLevel);
                        } else {
                            markOptIn(serverSubLevel, false);
                        }
                    }
                }

                pendingIterator.remove();
            }
        }

        private void inheritIntegrationState(final ServerSubLevel child, final ServerSubLevel parent) {
            final CompoundTag parentTag = getIntegrationTag(parent);
            final CompoundTag childTag = getOrCreateIntegrationTag(child);
            childTag.putBoolean(OPT_IN_TAG, true);
            childTag.putBoolean(STATION_KEEPING_TAG, parentTag != null && getBooleanOrDefault(parentTag, STATION_KEEPING_TAG, false));
            childTag.putBoolean(REFERENCE_PLANE_TAG, parentTag == null || getBooleanOrDefault(parentTag, REFERENCE_PLANE_TAG, true));
            childTag.putBoolean(VIEW_ALIGNMENT_TAG, parentTag == null || getBooleanOrDefault(parentTag, VIEW_ALIGNMENT_TAG, true));
            childTag.putString(GRAVITY_MODE_TAG, parentTag != null && parentTag.contains(GRAVITY_MODE_TAG, Tag.TAG_STRING) ? parentTag.getString(GRAVITY_MODE_TAG) : GravityMode.RADIAL.id());
            childTag.putDouble(GRAVITY_STRENGTH_TAG, parentTag != null && parentTag.contains(GRAVITY_STRENGTH_TAG, Tag.TAG_DOUBLE) ? parentTag.getDouble(GRAVITY_STRENGTH_TAG) : 1.0D);
            childTag.putDouble(GRAVITY_RADIUS_TAG, parentTag != null && parentTag.contains(GRAVITY_RADIUS_TAG, Tag.TAG_DOUBLE) ? parentTag.getDouble(GRAVITY_RADIUS_TAG) : SpaceIntegrationConfig.stationControllerDefaultRadius());
            putVector(childTag, GRAVITY_AXIS_ORIGIN_TAG, readVector(parentTag, GRAVITY_AXIS_ORIGIN_TAG, new Vector3d()));
            putVector(childTag, GRAVITY_AXIS_DIRECTION_TAG, readVector(parentTag, GRAVITY_AXIS_DIRECTION_TAG, new Vector3d(0.0D, 1.0D, 0.0D)));
            this.optedIn.add(child.getUniqueId());
            syncViewAlignmentState(child);
        }

        private void applyForces(final SubLevelPhysicsSystem physicsSystem, final ServerSubLevelContainer container, final double timeStep) {
            this.captureFrameMotions(physicsSystem, container, timeStep);
            this.syncViewAlignmentStates(container);

            if (!isAdAstraZeroGravity(this.level)) {
                return;
            }

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved() || resolveStationKeepingSource(subLevel) == null) {
                    continue;
                }
                applyStationKeeping(physicsSystem, subLevel, timeStep);
            }
        }

        private void captureFrameMotions(final SubLevelPhysicsSystem physicsSystem, final ServerSubLevelContainer container, final double timeStep) {
            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                final UUID subLevelId = subLevel.getUniqueId();
                if (subLevel.isRemoved()) {
                    this.frameMotions.remove(subLevelId);
                    continue;
                }

                final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
                final FrameMotion motion = this.frameMotions.computeIfAbsent(subLevelId, ignored -> new FrameMotion());
                if (handle == null) {
                    motion.clear();
                } else {
                    motion.capture(handle, timeStep);
                }
            }

            this.frameMotions.keySet().removeIf(subLevelId -> container.getSubLevel(subLevelId) == null);
        }

        private void syncViewAlignmentStates(final ServerSubLevelContainer container) {
            this.syncTicks++;
            if (this.syncTicks < 40) {
                return;
            }
            this.syncTicks = 0;

            final Set<UUID> enabledTargets = new HashSet<>();
            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) {
                    continue;
                }
                final ServerSubLevel sourceSubLevel = resolveViewAlignmentSource(subLevel);
                if (sourceSubLevel == null) {
                    continue;
                }
                syncViewAlignmentState(subLevel, sourceSubLevel, true);
                enabledTargets.add(subLevel.getUniqueId());
            }

            for (final UUID subLevelId : this.syncedViewTargets) {
                if (!enabledTargets.contains(subLevelId)) {
                    syncViewAlignmentDisabled(subLevelId);
                }
            }
            this.syncedViewTargets.clear();
            this.syncedViewTargets.addAll(enabledTargets);
        }

        private static void applyStationKeeping(final SubLevelPhysicsSystem physicsSystem, final ServerSubLevel subLevel, final double timeStep) {
            final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
            if (handle == null || !handle.isValid() || subLevel.getMassTracker().isInvalid()) {
                return;
            }

            final Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
            if (linearVelocity.lengthSquared() < 1.0E-8) {
                return;
            }

            final double dampingStep = Math.min(MAX_DAMPING_STEP, STATION_KEEPING_DAMPING * timeStep);
            final Vector3d localImpulse = linearVelocity.mul(-subLevel.getMassTracker().getMass() * dampingStep);
            subLevel.logicalPose().orientation().transformInverse(localImpulse);

            final ForceTotal forceTotal = subLevel.getOrCreateQueuedForceGroup(ForceGroups.LEVITATION.get()).getForceTotal();
            forceTotal.applyLinearImpulse(localImpulse);
        }
    }

    private record DockEndpoint(UUID subLevelId, BlockPos connectorPosition) {
        private DockEndpoint {
            connectorPosition = connectorPosition.immutable();
        }

        private int compareTo(final DockEndpoint other) {
            final int subLevelComparison = this.subLevelId.compareTo(other.subLevelId);
            if (subLevelComparison != 0) {
                return subLevelComparison;
            }
            final int xComparison = Integer.compare(this.connectorPosition.getX(), other.connectorPosition.getX());
            if (xComparison != 0) {
                return xComparison;
            }
            final int yComparison = Integer.compare(this.connectorPosition.getY(), other.connectorPosition.getY());
            if (yComparison != 0) {
                return yComparison;
            }
            return Integer.compare(this.connectorPosition.getZ(), other.connectorPosition.getZ());
        }
    }

    private record DockConnection(DockEndpoint first, DockEndpoint second) {
        private static DockConnection of(final UUID firstSubLevelId, final BlockPos firstConnectorPosition, final UUID secondSubLevelId, final BlockPos secondConnectorPosition) {
            final DockEndpoint firstEndpoint = new DockEndpoint(firstSubLevelId, firstConnectorPosition);
            final DockEndpoint secondEndpoint = new DockEndpoint(secondSubLevelId, secondConnectorPosition);
            return firstEndpoint.compareTo(secondEndpoint) <= 0 ? new DockConnection(firstEndpoint, secondEndpoint) : new DockConnection(secondEndpoint, firstEndpoint);
        }

        private boolean containsSubLevel(final UUID subLevelId) {
            return this.first.subLevelId().equals(subLevelId) || this.second.subLevelId().equals(subLevelId);
        }

        private boolean containsEndpoint(final UUID subLevelId, final BlockPos connectorPosition) {
            return matches(this.first, subLevelId, connectorPosition) || matches(this.second, subLevelId, connectorPosition);
        }

        private @Nullable UUID otherSubLevel(final UUID subLevelId) {
            if (this.first.subLevelId().equals(subLevelId)) {
                return this.second.subLevelId();
            }
            if (this.second.subLevelId().equals(subLevelId)) {
                return this.first.subLevelId();
            }
            return null;
        }

        private static boolean matches(final DockEndpoint endpoint, final UUID subLevelId, final BlockPos connectorPosition) {
            return endpoint.subLevelId().equals(subLevelId) && endpoint.connectorPosition().equals(connectorPosition);
        }
    }
}