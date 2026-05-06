package com.createmechanomania.spaceintegration.physics;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class GravityMath {
    private static final double MIN_AXIS_LENGTH = 1.0E-8D;
    private static final double MIN_RADIAL_DISTANCE = 1.0E-5D;

    private GravityMath() {
    }

    public static boolean getGravityDown(
            final Pose3dc pose,
            final GravityMode mode,
            final Vector3dc axisOrigin,
            final Vector3dc axisDirection,
            final double radius,
            final Vector3dc localPosition,
            final Vector3d destination
    ) {
        if (mode == GravityMode.LOCAL_DOWN) {
            return getLocalDown(pose, destination);
        }

        final Vector3d axis = normalizedAxis(axisDirection);
        final Vector3d radial = new Vector3d(localPosition).sub(axisOrigin);
        radial.fma(-radial.dot(axis), axis);

        final double radialDistance = radial.length();
        if (radialDistance < MIN_RADIAL_DISTANCE) {
            return getLocalDown(pose, destination);
        }
        if (radius > 0.0D && radialDistance > radius) {
            return false;
        }

        radial.div(radialDistance);
        destination.set(radial);
        pose.orientation().transform(destination);
        destination.normalize();
        return true;
    }

    public static boolean getReferenceFrameOrientation(
            final Pose3dc pose,
            final GravityMode mode,
            final Vector3dc axisOrigin,
            final Vector3dc axisDirection,
            final double radius,
            final Vector3dc localPosition,
            final Quaterniond destination
    ) {
        if (mode == GravityMode.LOCAL_DOWN) {
            final Vector3d up = new Vector3d(0.0D, 1.0D, 0.0D);
            pose.orientation().transform(up);
            up.normalize();
            return getReferenceFrameOrientationFromUp(pose, up, destination);
        }

        final Vector3d down = new Vector3d();
        if (!getGravityDown(pose, mode, axisOrigin, axisDirection, radius, localPosition, down)) {
            return false;
        }

        return getReferenceFrameOrientationFromUp(pose, down.negate(new Vector3d()).normalize(), destination);
    }

    public static boolean getReferenceFrameOrientationFromUp(final Pose3dc pose, final Vector3dc targetUp, final Quaterniond destination) {
        final Vector3d normalizedTargetUp = new Vector3d(targetUp);
        if (normalizedTargetUp.lengthSquared() < MIN_AXIS_LENGTH) {
            return false;
        }
        normalizedTargetUp.normalize();

        final Vector3d baseUp = new Vector3d(0.0D, 1.0D, 0.0D);
        pose.orientation().transform(baseUp);
        baseUp.normalize();

        final Quaterniond correction = getStableRotationTo(pose, baseUp, normalizedTargetUp, new Quaterniond());
        destination.set(correction);
        destination.mul(pose.orientation());
        destination.normalize();
        return true;
    }

    private static Quaterniond getStableRotationTo(final Pose3dc pose, final Vector3dc from, final Vector3dc to, final Quaterniond destination) {
        final double dot = Math.max(-1.0D, Math.min(1.0D, from.dot(to)));
        if (dot > 1.0D - 1.0E-6D) {
            return destination.identity();
        }
        if (dot < -1.0D + 1.0E-6D) {
            final Vector3d axis = getStablePerpendicularAxis(pose, from, new Vector3d());
            return destination.rotationAxis(Math.PI, axis.x, axis.y, axis.z);
        }
        return destination.rotationTo(from, to);
    }

    private static Vector3d getStablePerpendicularAxis(final Pose3dc pose, final Vector3dc from, final Vector3d destination) {
        destination.set(1.0D, 0.0D, 0.0D);
        pose.orientation().transform(destination);
        destination.fma(-destination.dot(from), from);
        if (destination.lengthSquared() >= MIN_AXIS_LENGTH) {
            return destination.normalize();
        }

        destination.set(0.0D, 0.0D, 1.0D);
        pose.orientation().transform(destination);
        destination.fma(-destination.dot(from), from);
        if (destination.lengthSquared() >= MIN_AXIS_LENGTH) {
            return destination.normalize();
        }

        destination.set(1.0D, 0.0D, 0.0D);
        destination.fma(-destination.dot(from), from);
        if (destination.lengthSquared() >= MIN_AXIS_LENGTH) {
            return destination.normalize();
        }
        return destination.set(0.0D, 0.0D, 1.0D);
    }

    private static boolean getLocalDown(final Pose3dc pose, final Vector3d destination) {
        destination.set(0.0D, -1.0D, 0.0D);
        pose.orientation().transform(destination);
        destination.normalize();
        return true;
    }

    private static Vector3d normalizedAxis(final Vector3dc axisDirection) {
        final Vector3d axis = new Vector3d(axisDirection);
        if (axis.lengthSquared() < MIN_AXIS_LENGTH) {
            return new Vector3d(0.0D, 1.0D, 0.0D);
        }
        return axis.normalize();
    }
}