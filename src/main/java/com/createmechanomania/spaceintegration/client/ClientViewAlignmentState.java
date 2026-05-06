package com.createmechanomania.spaceintegration.client;

import com.createmechanomania.spaceintegration.physics.GravityMode;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class ClientViewAlignmentState {
    private static final Map<UUID, State> VIEW_ALIGNMENT = new ConcurrentHashMap<>();
    private static final State DISABLED = new State(false, new UUID(0L, 0L), GravityMode.LOCAL_DOWN, 0.0D, new Vector3d(), new Vector3d(0.0D, 1.0D, 0.0D));

    private ClientViewAlignmentState() {
    }

    public static void set(final UUID subLevelId, final UUID sourceSubLevelId, final boolean enabled, final GravityMode gravityMode, final double gravityRadius, final Vector3dc axisOrigin, final Vector3dc axisDirection) {
        VIEW_ALIGNMENT.put(subLevelId, new State(enabled, sourceSubLevelId, gravityMode, gravityRadius, new Vector3d(axisOrigin), new Vector3d(axisDirection)));
    }

    public static boolean isEnabled(final UUID subLevelId) {
        return get(subLevelId).enabled();
    }

    public static State get(final UUID subLevelId) {
        return VIEW_ALIGNMENT.getOrDefault(subLevelId, DISABLED);
    }

    public record State(boolean enabled, UUID sourceSubLevelId, GravityMode gravityMode, double gravityRadius, Vector3d axisOrigin, Vector3d axisDirection) {
        public Vector3d axisOriginCopy() {
            return new Vector3d(this.axisOrigin);
        }

        public Vector3d axisDirectionCopy() {
            return new Vector3d(this.axisDirection);
        }
    }
}