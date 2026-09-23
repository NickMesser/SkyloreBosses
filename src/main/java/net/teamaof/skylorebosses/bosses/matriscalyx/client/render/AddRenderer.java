package net.teamaof.skylorebosses.bosses.matriscalyx.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisCalyxBoss;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.AbstractAdd;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

public class AddRenderer<T extends AbstractAdd> extends GeoBossRenderer<T> {
    public AddRenderer(EntityRendererProvider.Context ctx, String model, float scale) {
        super(ctx, MatrisCalyxBoss.ID, model, scale);
        this.shadowRadius = 0.5f * scale;
    }
}
