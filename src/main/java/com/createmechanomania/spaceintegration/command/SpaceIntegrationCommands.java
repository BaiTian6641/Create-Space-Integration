package com.createmechanomania.spaceintegration.command;

import com.createmechanomania.spaceintegration.physics.SableSpacePhysics;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.createmechanomania.spaceintegration.physics.GravityMode;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

public final class SpaceIntegrationCommands {
    private SpaceIntegrationCommands() {
    }

    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("create_space_integration")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("opt_in")
                        .executes(context -> optIn(context.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("opt_out")
                        .executes(context -> optOut(context.getSource().getPlayerOrException())))
                .then(Commands.literal("station_keeping")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> stationKeeping(context.getSource().getPlayerOrException(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("reference_plane")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> referencePlane(context.getSource().getPlayerOrException(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("view_alignment")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> viewAlignment(context.getSource().getPlayerOrException(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("gravity_mode")
                    .then(Commands.literal("local_down")
                        .executes(context -> gravityMode(context.getSource().getPlayerOrException(), GravityMode.LOCAL_DOWN)))
                    .then(Commands.literal("radial")
                        .executes(context -> gravityMode(context.getSource().getPlayerOrException(), GravityMode.RADIAL))))
                .then(Commands.literal("gravity_strength")
                    .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D, 4.0D))
                        .executes(context -> gravityStrength(context.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(context, "multiplier")))))
                .then(Commands.literal("gravity_radius")
                    .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0D, 512.0D))
                        .executes(context -> gravityRadius(context.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(context, "blocks")))))
                .then(Commands.literal("gravity_axis")
                    .then(Commands.literal("x")
                        .executes(context -> gravityAxis(context.getSource().getPlayerOrException(), new Vector3d(1.0D, 0.0D, 0.0D))))
                    .then(Commands.literal("y")
                        .executes(context -> gravityAxis(context.getSource().getPlayerOrException(), new Vector3d(0.0D, 1.0D, 0.0D))))
                    .then(Commands.literal("z")
                        .executes(context -> gravityAxis(context.getSource().getPlayerOrException(), new Vector3d(0.0D, 0.0D, 1.0D)))))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException()))));
    }

    private static int optIn(final ServerPlayer player, final boolean stationKeeping) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.markOptIn(subLevel, stationKeeping);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.opt_in", subLevel.getUniqueId().toString()));
        return 1;
    }

    private static int optOut(final ServerPlayer player) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.markOptOut(subLevel);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.opt_out", subLevel.getUniqueId().toString()));
        return 1;
    }

    private static int stationKeeping(final ServerPlayer player, final boolean enabled) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setStationKeeping(subLevel, enabled);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.station_keeping", subLevel.getUniqueId().toString(), Boolean.toString(enabled)));
        return 1;
    }

    private static int referencePlane(final ServerPlayer player, final boolean enabled) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setReferencePlane(subLevel, enabled);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.reference_plane", subLevel.getUniqueId().toString(), Boolean.toString(enabled)));
        return 1;
    }

    private static int viewAlignment(final ServerPlayer player, final boolean enabled) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setViewAlignment(subLevel, enabled);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.view_alignment", subLevel.getUniqueId().toString(), Boolean.toString(enabled)));
        return 1;
    }

    private static int gravityMode(final ServerPlayer player, final GravityMode mode) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setGravityMode(subLevel, mode);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.gravity_mode", subLevel.getUniqueId().toString(), mode.id()));
        return 1;
    }

    private static int gravityStrength(final ServerPlayer player, final double multiplier) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setGravityStrength(subLevel, multiplier);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.gravity_strength", subLevel.getUniqueId().toString(), Double.toString(multiplier)));
        return 1;
    }

    private static int gravityRadius(final ServerPlayer player, final double radius) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        SableSpacePhysics.setGravityRadius(subLevel, radius);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.gravity_radius", subLevel.getUniqueId().toString(), Double.toString(radius)));
        return 1;
    }

    private static int gravityAxis(final ServerPlayer player, final Vector3d axisDirection) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        final Vector3d axisOrigin = SableSpacePhysics.getLocalPosition(player, subLevel, new Vector3d());
        SableSpacePhysics.setGravityAxis(subLevel, axisOrigin, axisDirection);
        player.sendSystemMessage(Component.translatable("command.create_space_integration.gravity_axis", subLevel.getUniqueId().toString(), axisDirection.toString()));
        return 1;
    }

    private static int status(final ServerPlayer player) {
        final ServerSubLevel subLevel = requireSubLevel(player);
        if (subLevel == null) {
            return 0;
        }
        player.sendSystemMessage(Component.translatable(
                "command.create_space_integration.status",
                subLevel.getUniqueId().toString(),
                Boolean.toString(SableSpacePhysics.isOptedIn(subLevel)),
                Boolean.toString(SableSpacePhysics.isStationKeeping(subLevel)),
                Boolean.toString(SableSpacePhysics.isReferencePlane(subLevel)),
                Boolean.toString(SableSpacePhysics.isViewAlignment(subLevel)),
                SableSpacePhysics.getGravityMode(subLevel).id(),
                Double.toString(SableSpacePhysics.getGravityStrength(subLevel)),
                Double.toString(SableSpacePhysics.getGravityRadius(subLevel))));
        return 1;
    }

    private static ServerSubLevel requireSubLevel(final ServerPlayer player) {
        final ServerSubLevel subLevel = SableSpacePhysics.findPlayerSubLevel(player);
        if (subLevel == null) {
            player.sendSystemMessage(Component.translatable("command.create_space_integration.no_sublevel"));
        }
        return subLevel;
    }
}