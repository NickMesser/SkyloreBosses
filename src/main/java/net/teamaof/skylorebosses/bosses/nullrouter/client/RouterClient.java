package net.teamaof.skylorebosses.bosses.nullrouter.client;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterEntities;

/**
 * Null Router client wiring: renderers only. The request display is block states (lamps and consoles), the HUD is the
 * vanilla bossbar pair, screen shake is core FX, cutout blocks declare their render type in their model JSON.
 */
public final class RouterClient {
    private RouterClient() {}

    public static void init() {
        EntityRendererRegistry.register(RouterEntities.NULL_ROUTER, NullRouterRenderer::new);
        EntityRendererRegistry.register(RouterEntities.RETRY_PACKET, RetryPacketRenderer::new);
    }
}
