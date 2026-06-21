package com.dragoqc.adaptivehordes.network;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaveHudUpdatePayload(
    boolean active,
    String waveDisplayName,
    int totalPlanned,
    int remainingUnspawned,
    int aliveSpawned
) implements CustomPacketPayload {
    public static final Type<WaveHudUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AdaptiveHordes.MODID, "wave_hud_update")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, WaveHudUpdatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaveHudUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
            return new WaveHudUpdatePayload(
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, WaveHudUpdatePayload payload) {
            buffer.writeBoolean(payload.active());
            buffer.writeUtf(payload.waveDisplayName());
            buffer.writeVarInt(payload.totalPlanned());
            buffer.writeVarInt(payload.remainingUnspawned());
            buffer.writeVarInt(payload.aliveSpawned());
        }
    };

    public static WaveHudUpdatePayload inactive() {
        return new WaveHudUpdatePayload(false, "", 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
