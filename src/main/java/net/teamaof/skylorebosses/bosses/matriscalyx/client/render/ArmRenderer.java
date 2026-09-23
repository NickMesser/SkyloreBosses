package net.teamaof.skylorebosses.bosses.matriscalyx.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisCalyxBoss;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.AbstractRootedArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/** An arm's Blockbench model at its map scale, with the glowmask (core, nerves, bile) emissive. */
public class ArmRenderer<T extends AbstractRootedArm> extends GeoBossRenderer<T> {
    public ArmRenderer(EntityRendererProvider.Context ctx, ArmType type) {
        super(ctx, MatrisCalyxBoss.ID, type.model, type.renderScale);
        this.shadowRadius = type.width * 0.6f;
    }
}
