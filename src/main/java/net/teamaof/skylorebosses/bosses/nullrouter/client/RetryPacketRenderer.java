package net.teamaof.skylorebosses.bosses.nullrouter.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.nullrouter.NullRouterBoss;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RetryPacketEntity;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;
import software.bernie.geckolib.util.Color;

/** Retry packets: amber while hunting a console, tinted red when they turn on a player. */
public class RetryPacketRenderer extends GeoBossRenderer<RetryPacketEntity> {
    public RetryPacketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, NullRouterBoss.ID, RetryPacketEntity.MODEL, 1.0f);
        this.shadowRadius = 0.35f;
    }

    @Override
    public Color getRenderColor(RetryPacketEntity e, float partialTick, int packedLight) {
        return e.aggroSynced() ? Color.ofRGBA(1f, 0.55f, 0.5f, 1f) : Color.WHITE;
    }
}
