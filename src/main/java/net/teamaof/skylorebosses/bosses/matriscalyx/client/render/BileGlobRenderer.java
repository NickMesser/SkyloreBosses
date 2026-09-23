package net.teamaof.skylorebosses.bosses.matriscalyx.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisCalyxBoss;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.projectile.BileGlob;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

public class BileGlobRenderer extends GeoBossRenderer<BileGlob> {
    public BileGlobRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MatrisCalyxBoss.ID, "bile_glob", 2.0f);
    }
}
