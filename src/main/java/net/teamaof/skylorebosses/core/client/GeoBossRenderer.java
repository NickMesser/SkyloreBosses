package net.teamaof.skylorebosses.core.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.teamaof.skylorebosses.SkyloreBosses;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Base renderer for boss models: Blockbench model at a render scale, emissive glowmask layer, and no vanilla
 * death tilt (death animations are authored). Assets live at {@code geo|animations|textures/entity/<boss>/<model>}.
 */
public class GeoBossRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    public GeoBossRenderer(EntityRendererProvider.Context ctx, String boss, String model, float scale) {
        this(ctx, new DefaultedEntityGeoModel<>(SkyloreBosses.id(boss + "/" + model)), scale);
    }

    public GeoBossRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float scale) {
        super(ctx, model);
        withScale(scale);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0;
    }
}
