package net.teamaof.skylorebosses.bosses.matriscalyx.client.render;

import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SporeVentBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class SporeVentRenderer extends GeoBlockRenderer<SporeVentBlockEntity> {
    public SporeVentRenderer() {
        super(new DefaultedBlockGeoModel<>(SkyloreBosses.id("matris_calyx/spore_vent")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
