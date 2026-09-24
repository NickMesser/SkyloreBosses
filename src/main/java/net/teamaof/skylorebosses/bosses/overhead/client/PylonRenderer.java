package net.teamaof.skylorebosses.bosses.overhead.client;

import net.minecraft.world.phys.AABB;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.overhead.OverheadBoss;
import net.teamaof.skylorebosses.bosses.overhead.block.GeneratorPylonBlockEntity;
import net.teamaof.skylorebosses.bosses.overhead.encounter.PylonState;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The 3x3x5 generator tower from one block. While REBUILDING the energy core grows with integrity. */
public class PylonRenderer extends GeoBlockRenderer<GeneratorPylonBlockEntity> {
    public PylonRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(GeneratorPylonBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(GeneratorPylonBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    private static final class Model extends DefaultedBlockGeoModel<GeneratorPylonBlockEntity> {
        Model() {
            super(SkyloreBosses.id(OverheadBoss.ID + "/generator_pylon"));
        }

        @Override
        public void setCustomAnimations(GeneratorPylonBlockEntity be, long instanceId, AnimationState<GeneratorPylonBlockEntity> state) {
            super.setCustomAnimations(be, instanceId, state);
            if (be.state() != PylonState.REBUILDING) return;
            GeoBone core = getAnimationProcessor().getBone("core");
            if (core != null) {
                float s = 0.2f + 0.8f * be.fraction();
                core.setScaleX(s);
                core.setScaleY(s);
                core.setScaleZ(s);
            }
        }
    }
}
