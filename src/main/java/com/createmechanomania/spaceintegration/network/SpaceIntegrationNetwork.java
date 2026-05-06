package com.createmechanomania.spaceintegration.network;

import com.createmechanomania.spaceintegration.network.packet.ClientboundSubLevelViewAlignmentPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SpaceIntegrationNetwork {
    private SpaceIntegrationNetwork() {
    }

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("3");

        registrar.playToClient(
                ClientboundSubLevelViewAlignmentPacket.TYPE,
                ClientboundSubLevelViewAlignmentPacket.STREAM_CODEC,
                ClientboundSubLevelViewAlignmentPacket::handle
        );
    }

    public static void sendToClients(final CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}