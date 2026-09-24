package net.teamaof.skylorebosses.bosses.staticdeacon.client;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconEntities;

/**
 * Static Deacon client wiring: renderers only (HUD is the vanilla bossbar pair; screen shake is core FX; cutout blocks
 * declare their render type in their model JSON).
 */
public final class DeaconClient {
    private DeaconClient() {}

    public static void init() {
        EntityRendererRegistry.register(DeaconEntities.STATIC_DEACON, DeaconRenderer::new);
        EntityRendererRegistry.register(DeaconEntities.PROJECTILE, DeaconProjectileRenderer::new);
    }
}
