package net.teamaof.skylorebosses.bosses.matriscalyx.client;

import net.teamaof.skylorebosses.core.net.SBNetwork;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.bosses.matriscalyx.net.MatrisNetwork;
import net.teamaof.skylorebosses.core.client.SBClient;

/** Infection clock bar above the hotbar, plus the heavy-infection vignette. */
public final class InfectionHud {
    private InfectionHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int v = MatrisNetwork.clientInfection;

        if (v >= 75) {
            float pulse = 0.5f + 0.5f * Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.15f);
            int a = (int) (40 + 50 * pulse * (v - 75) / 25f);
            SBClient.vignette(g, w, h, (a << 24) | 0x3A0A14);
        }
        if (v <= 0) return;
        int bw = 81, bx = w / 2 - 91, by = h - 39 - 10;
        if (mc.player.isCreative()) by = h - 32;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xC0100008);
        int fill = Math.round(bw * v / (float) Infection.MAX);
        int col = v >= 75 ? 0xFFB02040 : v >= 50 ? 0xFFC87838 : v >= 25 ? 0xFFB8A040 : 0xFF88B040;
        g.fill(bx, by, bx + fill, by + 4, col);
        for (int k = 1; k < 4; k++) g.fill(bx + bw * k / 4, by, bx + bw * k / 4 + 1, by + 4, 0x80000000);
        g.drawString(mc.font, Component.translatable("matris_calyx.hud.infection", v), bx, by - 10, 0xFFD8B0A0, true);
    }

}
