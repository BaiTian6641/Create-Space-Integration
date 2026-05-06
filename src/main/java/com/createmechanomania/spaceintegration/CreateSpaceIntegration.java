package com.createmechanomania.spaceintegration;

import com.mojang.logging.LogUtils;
import com.createmechanomania.spaceintegration.command.SpaceIntegrationCommands;
import com.createmechanomania.spaceintegration.config.SpaceIntegrationConfig;
import com.createmechanomania.spaceintegration.network.SpaceIntegrationNetwork;
import com.createmechanomania.spaceintegration.physics.ReferencePlaneGravity;
import com.createmechanomania.spaceintegration.physics.SableSpacePhysics;
import com.createmechanomania.spaceintegration.registry.SpaceIntegrationRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateSpaceIntegration.MOD_ID)
public final class CreateSpaceIntegration {
    public static final String MOD_ID = "create_space_integration";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateSpaceIntegration(final IEventBus modEventBus, final ModContainer modContainer) {
        SpaceIntegrationConfig.register(modContainer);
        SpaceIntegrationRegistry.register(modEventBus);
        modEventBus.addListener(SpaceIntegrationNetwork::registerPayloads);
        SableSpacePhysics.register();
        NeoForge.EVENT_BUS.addListener(SpaceIntegrationCommands::register);
        NeoForge.EVENT_BUS.addListener(ReferencePlaneGravity::onEntityTick);
    }
}