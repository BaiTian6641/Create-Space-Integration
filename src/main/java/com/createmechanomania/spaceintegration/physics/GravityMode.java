package com.createmechanomania.spaceintegration.physics;

import java.util.Locale;

public enum GravityMode {
    LOCAL_DOWN("local_down"),
    RADIAL("radial");

    private final String id;

    GravityMode(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public GravityMode next() {
        return switch (this) {
            case LOCAL_DOWN -> RADIAL;
            case RADIAL -> LOCAL_DOWN;
        };
    }

    public static GravityMode byId(final String id) {
        final String normalized = id.toLowerCase(Locale.ROOT);
        for (final GravityMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return LOCAL_DOWN;
    }
}