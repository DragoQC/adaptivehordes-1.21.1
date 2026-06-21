package com.dragoqc.adaptivehordes.network;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AdaptiveHordesNetwork {
    private static final String NETWORK_VERSION = "1";

    private AdaptiveHordesNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(WaveHudUpdatePayload.TYPE, WaveHudUpdatePayload.STREAM_CODEC, AdaptiveHordesNetwork::handleWaveHudUpdate);
    }

    private static void handleWaveHudUpdate(WaveHudUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                applyClientWaveHudPayload(payload);
            }
        }).exceptionally(error -> {
            AdaptiveHordes.LOGGER.warn("Failed to handle Adaptive Hordes wave HUD payload", error);
            return null;
        });
    }

    private static void applyClientWaveHudPayload(WaveHudUpdatePayload payload) {
        try {
            // Keep common network registration dedicated-server safe by resolving the overlay only on the physical client.
            Class<?> overlayClass = Class.forName("com.dragoqc.adaptivehordes.client.WaveHudOverlay");
            overlayClass.getMethod("applyPayload", WaveHudUpdatePayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to update Adaptive Hordes client wave HUD", ex);
        }
    }
}
