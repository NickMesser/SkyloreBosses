package net.teamaof.skylorebosses.bosses.matriscalyx.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.bloom.CalyxBloom;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import net.teamaof.skylorebosses.core.client.GeoBossRenderer;

/** The Calyx Bloom: building scale, emissive iris, and the eye bone pitched along the laser aim. */
public class BloomRenderer extends GeoBossRenderer<CalyxBloom> {
    public BloomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(), CalyxBloom.SCALE);
        this.shadowRadius = 8f;
    }

    private static final class Model extends DefaultedEntityGeoModel<CalyxBloom> {
        Model() {
            super(SkyloreBosses.id("matris_calyx/" + CalyxBloom.MODEL));
        }

        @Override
        public void setCustomAnimations(CalyxBloom bloom, long instanceId, AnimationState<CalyxBloom> state) {
            super.setCustomAnimations(bloom, instanceId, state);
            GeoBone eye = getAnimationProcessor().getBone("eye");
            if (eye != null && !bloom.isDeadOrDying()) {
                eye.setRotX(eye.getRotX() - (float) Math.toRadians(bloom.beamPitch() * 0.6f));
            }
        }
    }
}
