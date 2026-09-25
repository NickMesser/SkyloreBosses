package net.teamaof.skylorebosses.bosses.amanita.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaBoss;
import net.teamaof.skylorebosses.bosses.amanita.entity.HollowBoltEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** hollow_bolt: nose follows velocity. */
public class HollowBoltRenderer extends GeoEntityRenderer<HollowBoltEntity> {
    public HollowBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(SkyloreBosses.id(AmanitaBoss.ID + "/" + HollowBoltEntity.MODEL)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(HollowBoltEntity o, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(o.getViewYRot(partialTick) + 180f));
        pose.mulPose(Axis.XP.rotationDegrees(o.getViewXRot(partialTick)));
    }
}
