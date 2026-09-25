package net.teamaof.skylorebosses.bosses.nullrouter.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.teamaof.skylorebosses.bosses.nullrouter.NullRouterBoss;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.NullRouterEntity;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;
import software.bernie.geckolib.util.Color;

/**
 * The chassis (DESIGN.md §14, the critical read). A ghost is a flickering cyan hologram at about a third opacity with
 * only its glowmask at full strength; a solid chassis is the fully opaque, lit machine. Docking and releasing fade
 * between the two over the edge i-frames, so "can I hit it" is readable from across the vault without the bar.
 */
public class NullRouterRenderer extends GeoBossRenderer<NullRouterEntity> {
    public NullRouterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, NullRouterBoss.ID, NullRouterEntity.MODEL, NullRouterEntity.SCALE);
        this.shadowRadius = 1.3f;
    }

    @Override
    public RenderType getRenderType(NullRouterEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(NullRouterEntity e, float partialTick, int packedLight) {
        float s = e.isDeadOrDying() ? 1f : e.solidity(partialTick);
        float t = e.tickCount + partialTick;
        // ghost flicker: a slow shimmer plus an occasional scanline dropout
        float flicker = 0.08f * Mth.sin(t * 0.35f) + ((e.tickCount / 3) % 23 == 0 ? -0.12f : 0f);
        float alpha = Mth.lerp(s, 0.32f + flicker, 1f);
        float r = Mth.lerp(s, 0.55f, 1f), g = Mth.lerp(s, 0.95f, 1f), b = 1f;
        if (e.wetSynced()) { r *= 0.8f; g *= 0.92f; }
        return Color.ofRGBA(r, g, b, Mth.clamp(alpha, 0.12f, 1f));
    }
}
