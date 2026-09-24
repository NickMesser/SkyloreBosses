package net.teamaof.skylorebosses.bosses.overhead.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.overhead.OverheadBoss;
import net.teamaof.skylorebosses.bosses.overhead.entity.Ordnance;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Shells, flares, bolts and flak use the howitzer_shell model (scaled); missiles use seeker_missile. Nose follows velocity. */
public class OrdnanceRenderer extends GeoEntityRenderer<Ordnance> {
    public OrdnanceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(Ordnance o, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(o.getViewYRot(partialTick) + 180f));
        pose.mulPose(Axis.XP.rotationDegrees(o.getViewXRot(partialTick)));
        float s = switch (o.kind()) {
            case SHELL -> 1.6f;
            case MISSILE -> 1.3f;
            case BOLT -> 0.5f;
            case FLARE, FLAK -> 0.8f;
        };
        pose.scale(s, s, s);
    }

    private static final class Model extends GeoModel<Ordnance> {
        private static ResourceLocation r(String kind, String model, String ext) {
            return SkyloreBosses.id(kind + "/entity/" + OverheadBoss.ID + "/" + model + ext);
        }

        @Override
        public ResourceLocation getModelResource(Ordnance o) { return r("geo", o.model(), ".geo.json"); }

        @Override
        public ResourceLocation getTextureResource(Ordnance o) { return r("textures", o.model(), ".png"); }

        @Override
        public ResourceLocation getAnimationResource(Ordnance o) { return r("animations", o.model(), ".animation.json"); }
    }
}
