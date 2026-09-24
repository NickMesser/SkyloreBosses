package net.teamaof.skylorebosses.bosses.staticdeacon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.staticdeacon.StaticDeaconBoss;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.DeaconProjectile;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Static bolts use static_bolt, homing shards homing_shard. Nose follows velocity. */
public class DeaconProjectileRenderer extends GeoEntityRenderer<DeaconProjectile> {
    public DeaconProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(DeaconProjectile o, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(o.getViewYRot(partialTick) + 180f));
        pose.mulPose(Axis.XP.rotationDegrees(o.getViewXRot(partialTick)));
        float s = o.kind() == DeaconProjectile.Kind.BOLT ? 1.0f : 1.4f;
        pose.scale(s, s, s);
    }

    private static final class Model extends GeoModel<DeaconProjectile> {
        private static ResourceLocation r(String kind, String model, String ext) {
            return SkyloreBosses.id(kind + "/entity/" + StaticDeaconBoss.ID + "/" + model + ext);
        }

        @Override
        public ResourceLocation getModelResource(DeaconProjectile o) { return r("geo", o.model(), ".geo.json"); }

        @Override
        public ResourceLocation getTextureResource(DeaconProjectile o) { return r("textures", o.model(), ".png"); }

        @Override
        public ResourceLocation getAnimationResource(DeaconProjectile o) { return r("animations", o.model(), ".animation.json"); }
    }
}
