package net.teamaof.skylorebosses.bosses.staticdeacon.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.staticdeacon.StaticDeaconBoss;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.StaticDeaconEntity;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/** The Deacon at 1x (about 3.4 blocks tall); glyphs, halo and the static core glow from the glowmask. */
public class DeaconRenderer extends GeoBossRenderer<StaticDeaconEntity> {
    public DeaconRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, StaticDeaconBoss.ID, StaticDeaconEntity.MODEL, StaticDeaconEntity.SCALE);
        this.shadowRadius = 0.9f;
    }
}
