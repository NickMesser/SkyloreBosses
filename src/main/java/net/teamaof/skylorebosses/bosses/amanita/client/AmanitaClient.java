package net.teamaof.skylorebosses.bosses.amanita.client;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaBoss;
import net.teamaof.skylorebosses.bosses.amanita.entity.HollowSpawnEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.LampEaterEntity;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaEntities;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/**
 * Amanita client wiring: renderers only (HUD is the vanilla bossbar pair; Darkness is vanilla; screen shake is core FX;
 * cutout blocks declare their render type in their model JSON). Gills, eyes, spores and a lamp-eater's full belly glow
 * through the glowmask layer, so every silhouette reads in a pitch-dark hollow without emitting block light.
 */
public final class AmanitaClient {
    private AmanitaClient() {}

    public static void init() {
        EntityRendererRegistry.register(AmanitaEntities.AMANITA, AmanitaRenderer::new);
        EntityRendererRegistry.register(AmanitaEntities.LAMP_EATER, ctx -> new GeoBossRenderer<LampEaterEntity>(ctx, AmanitaBoss.ID, LampEaterEntity.MODEL, 1f));
        EntityRendererRegistry.register(AmanitaEntities.HOLLOW_SPAWN, ctx -> new GeoBossRenderer<HollowSpawnEntity>(ctx, AmanitaBoss.ID, HollowSpawnEntity.MODEL, 1f));
        EntityRendererRegistry.register(AmanitaEntities.HOLLOW_BOLT, HollowBoltRenderer::new);
    }
}
