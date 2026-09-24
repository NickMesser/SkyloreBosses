package net.teamaof.skylorebosses.bosses.overhead.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.overhead.OverheadBoss;
import net.teamaof.skylorebosses.bosses.overhead.entity.OverheadEntity;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/** The chassis at 1.5x (about 7.5 blocks across), emissive lens, beacon and thrusters from the glowmask. */
public class OverheadRenderer extends GeoBossRenderer<OverheadEntity> {
    public OverheadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, OverheadBoss.ID, OverheadEntity.MODEL, OverheadEntity.SCALE);
        this.shadowRadius = 3f;
    }
}
