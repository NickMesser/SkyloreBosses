package net.teamaof.skylorebosses.bosses.matriscalyx.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.render.AddRenderer;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.render.ArmRenderer;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.render.BileGlobRenderer;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.render.BloomRenderer;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.render.SporeVentRenderer;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlockEntities;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisEntities;

/** Matris Calyx client wiring (renderers + infection HUD). Shared particles/screen FX live in the core. */
public final class MatrisClient {
    private MatrisClient() {}

    public static void init() {
        EntityRendererRegistry.register(MatrisEntities.NERVE_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.NERVE));
        EntityRendererRegistry.register(MatrisEntities.GRASPING_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.GRASPING));
        EntityRendererRegistry.register(MatrisEntities.SLAM_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.SLAM));
        EntityRendererRegistry.register(MatrisEntities.CHARGING_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.CHARGING));
        EntityRendererRegistry.register(MatrisEntities.MOUTH_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.MOUTH));
        EntityRendererRegistry.register(MatrisEntities.SPITTING_ARM, ctx -> new ArmRenderer<>(ctx, ArmType.SPITTING));
        EntityRendererRegistry.register(MatrisEntities.CALYX_BLOOM, BloomRenderer::new);
        EntityRendererRegistry.register(MatrisEntities.BILE_GLOB, BileGlobRenderer::new);
        EntityRendererRegistry.register(MatrisEntities.SPORE_THRALL, ctx -> new AddRenderer<>(ctx, "spore_thrall", 1.0f));
        EntityRendererRegistry.register(MatrisEntities.SPORE_MITE, ctx -> new AddRenderer<>(ctx, "spore_mite", 1.0f));
        EntityRendererRegistry.register(MatrisEntities.INFECTED_DRIFTER, ctx -> new AddRenderer<>(ctx, "infected_drifter", 1.5f));
        ClientLifecycleEvent.CLIENT_SETUP.register(mc ->
                BlockEntityRendererRegistry.register(MatrisBlockEntities.SPORE_VENT.get(), ctx -> new SporeVentRenderer()));
        ClientGuiEvent.RENDER_HUD.register(InfectionHud::render);
    }
}
