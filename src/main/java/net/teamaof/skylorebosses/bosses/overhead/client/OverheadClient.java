package net.teamaof.skylorebosses.bosses.overhead.client;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlockEntities;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadEntities;

/** Overhead client wiring: renderers only (HUD is the vanilla bossbar pair; screen shake is the shared core FX). */
public final class OverheadClient {
    private OverheadClient() {}

    public static void init() {
        EntityRendererRegistry.register(OverheadEntities.OVERHEAD, OverheadRenderer::new);
        EntityRendererRegistry.register(OverheadEntities.ORDNANCE, OrdnanceRenderer::new);
        ClientLifecycleEvent.CLIENT_SETUP.register(mc ->
                BlockEntityRendererRegistry.register(OverheadBlockEntities.GENERATOR_PYLON.get(), ctx -> new PylonRenderer()));
    }
}
