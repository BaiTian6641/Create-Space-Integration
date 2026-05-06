package com.createmechanomania.spaceintegration.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpaceIntegrationConfig {
    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.BooleanValue REFERENCE_PLANE_GRAVITY_ENABLED;
    private static final ModConfigSpec.BooleanValue VIEW_ALIGNMENT_DEFAULT_ENABLED;
    private static final ModConfigSpec.BooleanValue ENTITY_ORIENTATION_ENABLED;
    private static final ModConfigSpec.BooleanValue REFERENCE_FRAME_FORCES_ENABLED;
    private static final ModConfigSpec.BooleanValue ANCHORED_ENTITY_PSEUDO_FORCES_ENABLED;
    private static final ModConfigSpec.BooleanValue FREE_FLIGHT_ARTIFICIAL_GRAVITY_ENABLED;
    private static final ModConfigSpec.DoubleValue REFERENCE_PLANE_ACCELERATION;
    private static final ModConfigSpec.DoubleValue REFERENCE_PLANE_CONTACT_SPEED;
    private static final ModConfigSpec.DoubleValue REFERENCE_PLANE_TERMINAL_SPEED;
    private static final ModConfigSpec.DoubleValue FRAME_VELOCITY_MATCH_LIMIT;
    private static final ModConfigSpec.DoubleValue CENTRIFUGAL_FORCE_SCALE;
    private static final ModConfigSpec.DoubleValue CORIOLIS_FORCE_SCALE;
    private static final ModConfigSpec.DoubleValue EULER_FORCE_SCALE;
    private static final ModConfigSpec.DoubleValue MAX_PSEUDO_ACCELERATION;
    private static final ModConfigSpec.BooleanValue STATION_CONTROLLER_ENABLED;
    private static final ModConfigSpec.BooleanValue STATION_CONTROLLER_STATION_KEEPING_DEFAULT;
    private static final ModConfigSpec.IntValue STATION_CONTROLLER_REFRESH_INTERVAL;
    private static final ModConfigSpec.DoubleValue STATION_CONTROLLER_GRAVITY_STRENGTH;
    private static final ModConfigSpec.DoubleValue STATION_CONTROLLER_DEFAULT_RADIUS;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("physics");
        REFERENCE_PLANE_GRAVITY_ENABLED = builder
                .comment("Default-enable local reference-plane gravity for opted-in Sable sub-levels in Ad Astra zero-gravity dimensions.")
                .define("referencePlaneGravityEnabled", true);
        VIEW_ALIGNMENT_DEFAULT_ENABLED = builder
            .comment("Default-enable view and entity orientation alignment for opted-in Sable sub-levels.")
                .define("viewAlignmentDefaultEnabled", true);
        ENTITY_ORIENTATION_ENABLED = builder
            .comment("Rotate player/entity orientation through Sable's custom entity-orientation hook while reference-plane view alignment is active.")
            .define("entityOrientationEnabled", true);
        REFERENCE_FRAME_FORCES_ENABLED = builder
            .comment("Apply the experimental alpha.20 moving-reference-frame carrier velocity and pseudo-force terms. Disabled in the alpha.26 dock-inheritance path; supported server players now receive source-frame pose carry while other supported living entities keep the narrow tangent correction.")
            .define("referenceFrameForcesEnabled", false);
        ANCHORED_ENTITY_PSEUDO_FORCES_ENABLED = builder
            .comment("Apply centrifugal, Coriolis, and Euler pseudo-forces to entities already anchored to a Sable sub-level surface. Keep disabled for stable station-relative standing.")
            .define("anchoredEntityPseudoForcesEnabled", false);
        FREE_FLIGHT_ARTIFICIAL_GRAVITY_ENABLED = builder
            .comment("Apply reference-plane artificial gravity to airborne living entities so the alpha.24 path behaves like a normal switched local-gravity frame.")
            .define("freeFlightArtificialGravityEnabled", true);
        REFERENCE_PLANE_ACCELERATION = builder
            .comment("Acceleration in blocks per tick squared applied along the opted-in station's effective artificial down vector while surface-contacted, and optionally while airborne.")
                .defineInRange("referencePlaneAcceleration", 0.08D, 0.0D, 0.5D);
        REFERENCE_PLANE_CONTACT_SPEED = builder
            .comment("Maximum inward holding speed in blocks per tick while an entity is already touching a reference-plane surface. This keeps wall-standing stable without accumulating large downward velocity.")
            .defineInRange("referencePlaneContactSpeed", 0.12D, 0.0D, 1.0D);
        REFERENCE_PLANE_TERMINAL_SPEED = builder
            .comment("Maximum airborne speed in blocks per tick along the artificial down vector when freeFlightArtificialGravityEnabled is true.")
                .defineInRange("referencePlaneTerminalSpeed", 3.5D, 0.1D, 20.0D);
        FRAME_VELOCITY_MATCH_LIMIT = builder
            .comment("Maximum per-tick tangent velocity correction used for non-player supported living entities. Server players are carried by the resolved Sable source-frame pose delta instead.")
            .defineInRange("frameVelocityMatchLimit", 0.45D, 0.0D, 5.0D);
        CENTRIFUGAL_FORCE_SCALE = builder
            .comment("Scale for the rotating-frame centrifugal acceleration term.")
            .defineInRange("centrifugalForceScale", 1.0D, 0.0D, 4.0D);
        CORIOLIS_FORCE_SCALE = builder
            .comment("Scale for the rotating-frame Coriolis acceleration term. Lower values are easier on players while still affecting thrown/dropped entities.")
            .defineInRange("coriolisForceScale", 0.5D, 0.0D, 4.0D);
        EULER_FORCE_SCALE = builder
            .comment("Scale for the Euler acceleration term caused by changing station angular velocity.")
            .defineInRange("eulerForceScale", 0.5D, 0.0D, 4.0D);
        MAX_PSEUDO_ACCELERATION = builder
            .comment("Maximum combined rotating-frame pseudo-acceleration applied to an entity in one tick.")
            .defineInRange("maxPseudoAcceleration", 0.35D, 0.0D, 5.0D);
        builder.pop();
        builder.push("stationController");
        STATION_CONTROLLER_ENABLED = builder
            .comment("Enable the station gravity controller block.")
            .define("enabled", true);
        STATION_CONTROLLER_STATION_KEEPING_DEFAULT = builder
            .comment("Have newly applied station gravity controllers enable Sable station-keeping damping by default. Keep disabled for mobile Aeronautics vessels.")
            .define("stationKeepingDefault", false);
        STATION_CONTROLLER_REFRESH_INTERVAL = builder
            .comment("Server ticks between station gravity controller refreshes.")
            .defineInRange("refreshInterval", 20, 1, 200);
        STATION_CONTROLLER_GRAVITY_STRENGTH = builder
            .comment("Artificial gravity acceleration multiplier applied by station gravity controllers.")
            .defineInRange("gravityStrengthMultiplier", 1.0D, 0.0D, 4.0D);
        STATION_CONTROLLER_DEFAULT_RADIUS = builder
            .comment("Default radial gravity reach in blocks. A value of 0 means unlimited radial reach.")
            .defineInRange("defaultRadialRadius", 96.0D, 0.0D, 512.0D);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private SpaceIntegrationConfig() {
    }

    public static void register(final ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static boolean referencePlaneGravityEnabled() {
        return REFERENCE_PLANE_GRAVITY_ENABLED.get();
    }

    public static boolean viewAlignmentDefaultEnabled() {
        return VIEW_ALIGNMENT_DEFAULT_ENABLED.get();
    }

    public static boolean entityOrientationEnabled() {
        return ENTITY_ORIENTATION_ENABLED.get();
    }

    public static boolean referenceFrameForcesEnabled() {
        return REFERENCE_FRAME_FORCES_ENABLED.get();
    }

    public static boolean anchoredEntityPseudoForcesEnabled() {
        return ANCHORED_ENTITY_PSEUDO_FORCES_ENABLED.get();
    }

    public static boolean freeFlightArtificialGravityEnabled() {
        return FREE_FLIGHT_ARTIFICIAL_GRAVITY_ENABLED.get();
    }

    public static double referencePlaneAcceleration() {
        return REFERENCE_PLANE_ACCELERATION.get();
    }

    public static double referencePlaneContactSpeed() {
        return REFERENCE_PLANE_CONTACT_SPEED.get();
    }

    public static double referencePlaneTerminalSpeed() {
        return REFERENCE_PLANE_TERMINAL_SPEED.get();
    }

    public static double frameVelocityMatchLimit() {
        return FRAME_VELOCITY_MATCH_LIMIT.get();
    }

    public static double centrifugalForceScale() {
        return CENTRIFUGAL_FORCE_SCALE.get();
    }

    public static double coriolisForceScale() {
        return CORIOLIS_FORCE_SCALE.get();
    }

    public static double eulerForceScale() {
        return EULER_FORCE_SCALE.get();
    }

    public static double maxPseudoAcceleration() {
        return MAX_PSEUDO_ACCELERATION.get();
    }

    public static boolean stationControllerEnabled() {
        return STATION_CONTROLLER_ENABLED.get();
    }

    public static boolean stationControllerStationKeepingDefault() {
        return STATION_CONTROLLER_STATION_KEEPING_DEFAULT.get();
    }

    public static int stationControllerRefreshInterval() {
        return STATION_CONTROLLER_REFRESH_INTERVAL.get();
    }

    public static double stationControllerGravityStrength() {
        return STATION_CONTROLLER_GRAVITY_STRENGTH.get();
    }

    public static double stationControllerDefaultRadius() {
        return STATION_CONTROLLER_DEFAULT_RADIUS.get();
    }
}