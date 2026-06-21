package com.dragoqc.adaptivehordes.client;

import com.dragoqc.adaptivehordes.AdaptiveHordes;
import com.dragoqc.adaptivehordes.network.WaveHudUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class WaveHudOverlay {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(AdaptiveHordes.MODID, "wave_hud");
    private static WaveHudState state = WaveHudState.INACTIVE;

    private WaveHudOverlay() {}

    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, LAYER_ID, (guiGraphics, deltaTracker) -> render(guiGraphics));
    }

    public static void applyPayload(WaveHudUpdatePayload payload) {
        if (payload == null || !payload.active()) {
            state = WaveHudState.INACTIVE;
            return;
        }
        state = new WaveHudState(
            true,
            payload.waveDisplayName(),
            Math.max(0, payload.totalPlanned()),
            Math.max(0, payload.remainingUnspawned()),
            Math.max(0, payload.aliveSpawned())
        );
    }

    private static void render(GuiGraphics guiGraphics) {
        if (!state.active()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;

        int remainingTotal = Math.max(0, state.remainingUnspawned() + state.aliveSpawned());
        String waveName = (state.waveDisplayName() == null || state.waveDisplayName().isBlank()) ? "Wave" : state.waveDisplayName();
        String count = "Enemies: " + remainingTotal + "/" + Math.max(0, state.totalPlanned());

        int x = 8;
        int y = 88;
        int width = Math.max(minecraft.font.width(waveName), minecraft.font.width(count)) + 12;
        int height = 28;

        drawRoundedPanel(guiGraphics, x - 4, y - 4, width + 4, height + 4, 4, 0x88000000);
        guiGraphics.drawString(minecraft.font, Component.literal(waveName), x, y, 0xFFE55D5D, true);
        guiGraphics.drawString(minecraft.font, Component.literal(count), x, y + 12, 0xFFFFFFFF, true);
    }

    private static void drawRoundedPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (safeRadius <= 0) {
            guiGraphics.fill(x, y, x + width, y + height, color);
            return;
        }

        for (int row = 0; row < height; row++) {
            int inset = roundedInset(row, height, safeRadius);
            guiGraphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int roundedInset(int row, int height, int radius) {
        if (row >= radius && row < height - radius) return 0;

        int edgeRow = row < radius ? row : height - row - 1;
        int distance = radius - edgeRow - 1;
        double inside = Math.max(0.0D, (radius * radius) - (distance * distance));
        return Math.max(0, radius - (int) Math.sqrt(inside));
    }

    private record WaveHudState(
        boolean active,
        String waveDisplayName,
        int totalPlanned,
        int remainingUnspawned,
        int aliveSpawned
    ) {
        private static final WaveHudState INACTIVE = new WaveHudState(false, "", 0, 0, 0);
    }
}
