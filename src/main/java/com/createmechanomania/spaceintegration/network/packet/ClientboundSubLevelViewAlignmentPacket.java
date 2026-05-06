package com.createmechanomania.spaceintegration.network.packet;

import com.createmechanomania.spaceintegration.CreateSpaceIntegration;
import com.createmechanomania.spaceintegration.client.ClientViewAlignmentState;
import com.createmechanomania.spaceintegration.physics.GravityMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public record ClientboundSubLevelViewAlignmentPacket(
    UUID subLevelId,
    UUID sourceSubLevelId,
    boolean enabled,
    GravityMode gravityMode,
    double gravityRadius,
    double axisOriginX,
    double axisOriginY,
    double axisOriginZ,
    double axisDirectionX,
    double axisDirectionY,
    double axisDirectionZ
) implements CustomPacketPayload {
    public static final Type<ClientboundSubLevelViewAlignmentPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSpaceIntegration.MOD_ID, "sub_level_view_alignment")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSubLevelViewAlignmentPacket> STREAM_CODEC = StreamCodec.of(
        ClientboundSubLevelViewAlignmentPacket::write,
        ClientboundSubLevelViewAlignmentPacket::read
    );

    public ClientboundSubLevelViewAlignmentPacket(final UUID subLevelId, final boolean enabled, final GravityMode gravityMode, final double gravityRadius, final Vector3dc axisOrigin, final Vector3dc axisDirection) {
    this(
        subLevelId,
        subLevelId,
        enabled,
        gravityMode,
        gravityRadius,
        axisOrigin.x(),
        axisOrigin.y(),
        axisOrigin.z(),
        axisDirection.x(),
        axisDirection.y(),
        axisDirection.z()
    );
    }

    public ClientboundSubLevelViewAlignmentPacket(final UUID subLevelId, final UUID sourceSubLevelId, final boolean enabled, final GravityMode gravityMode, final double gravityRadius, final Vector3dc axisOrigin, final Vector3dc axisDirection) {
    this(
        subLevelId,
        sourceSubLevelId,
        enabled,
        gravityMode,
        gravityRadius,
        axisOrigin.x(),
        axisOrigin.y(),
        axisOrigin.z(),
        axisDirection.x(),
        axisDirection.y(),
        axisDirection.z()
    );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ClientboundSubLevelViewAlignmentPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    private static void write(final RegistryFriendlyByteBuf buffer, final ClientboundSubLevelViewAlignmentPacket packet) {
        buffer.writeUUID(packet.subLevelId());
        buffer.writeUUID(packet.sourceSubLevelId());
        buffer.writeBoolean(packet.enabled());
        buffer.writeUtf(packet.gravityMode().id());
        buffer.writeDouble(packet.gravityRadius());
        buffer.writeDouble(packet.axisOriginX());
        buffer.writeDouble(packet.axisOriginY());
        buffer.writeDouble(packet.axisOriginZ());
        buffer.writeDouble(packet.axisDirectionX());
        buffer.writeDouble(packet.axisDirectionY());
        buffer.writeDouble(packet.axisDirectionZ());
    }

    private static ClientboundSubLevelViewAlignmentPacket read(final RegistryFriendlyByteBuf buffer) {
        return new ClientboundSubLevelViewAlignmentPacket(
                buffer.readUUID(),
            buffer.readUUID(),
                buffer.readBoolean(),
                GravityMode.byId(buffer.readUtf()),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public Vector3d axisOrigin() {
        return new Vector3d(this.axisOriginX, this.axisOriginY, this.axisOriginZ);
    }

    public Vector3d axisDirection() {
        return new Vector3d(this.axisDirectionX, this.axisDirectionY, this.axisDirectionZ);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(final ClientboundSubLevelViewAlignmentPacket packet) {
            ClientViewAlignmentState.set(packet.subLevelId(), packet.sourceSubLevelId(), packet.enabled(), packet.gravityMode(), packet.gravityRadius(), packet.axisOrigin(), packet.axisDirection());
        }
    }
}