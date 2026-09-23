package net.teamaof.skylorebosses.core.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.teamaof.skylorebosses.core.net.SBNetwork.ClientFxState;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/** Shared client wiring: particle providers, screen effect overlay, camera shake. */
public final class SBClient {
    private SBClient() {}

    public static void init() {
        for (String name : SBParticles.NAMES) {
            ParticleProviderRegistry.register(SBParticles.BY_NAME.get(name), sprites -> new SBParticle.Provider(sprites, SBParticle.Style.of(name)));
        }
        ClientTickEvent.CLIENT_POST.register(mc -> ClientFxState.tick());
        ClientGuiEvent.RENDER_HUD.register(SBClient::renderOverlay);
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeCameraAngles e) -> {
            int s = ClientFxState.shake;
            if (s <= 0) return;
            double t = e.getPartialTick() + s;
            float amp = Math.min(1f, s / 20f) * 1.5f;
            e.setPitch(e.getPitch() + (float) Math.sin(t * 1.7) * amp);
            e.setYaw(e.getYaw() + (float) Math.cos(t * 1.3) * amp);
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.9) * amp * 0.6f);
        });
    }

    private static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientFxState.taken > 0) {
            g.fill(0, 0, w, h, (Math.min(220, ClientFxState.taken * 6) << 24) | 0x200006);
        }
        if (ClientFxState.whiteout > 0 && ClientFxState.whiteoutMax > 0) {
            int a = (int) (235f * ClientFxState.whiteout / ClientFxState.whiteoutMax);
            g.fill(0, 0, w, h, (a << 24) | 0xFFF4DC);
        }
    }

    /** Edge vignette helper for boss HUDs. */
    public static void vignette(GuiGraphics g, int w, int h, int color) {
        int t = Math.max(8, h / 6);
        g.fillGradient(0, 0, w, t, color, color & 0x00FFFFFF);
        g.fillGradient(0, h - t, w, h, color & 0x00FFFFFF, color);
        g.fill(0, 0, t / 2, h, color);
        g.fill(w - t / 2, 0, w, h, color);
    }
}
