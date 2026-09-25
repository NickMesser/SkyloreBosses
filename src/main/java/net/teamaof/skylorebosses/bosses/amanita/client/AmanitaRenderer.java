package net.teamaof.skylorebosses.bosses.amanita.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaBoss;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaEntity;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/** Amanita at 1x (about 3.2 blocks tall); gills, eyes and spore motes glow from the glowmask. */
public class AmanitaRenderer extends GeoBossRenderer<AmanitaEntity> {
    public AmanitaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, AmanitaBoss.ID, AmanitaEntity.MODEL, AmanitaEntity.SCALE);
        this.shadowRadius = 1.0f;
    }
}
