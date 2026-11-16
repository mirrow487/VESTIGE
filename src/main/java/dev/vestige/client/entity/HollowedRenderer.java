package dev.vestige.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vestige.VestigeIds;
import dev.vestige.entity.mob.Hollowed;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders Hollowed and Hollowed Brutes (scaled up, different skin). */
public class HollowedRenderer extends HumanoidMobRenderer<Hollowed, HollowedModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VestigeIds.id("hollowed"), "main");
    private final ResourceLocation texture;
    private final float scale;

    public HollowedRenderer(EntityRendererProvider.Context ctx, String textureName, float scale) {
        super(ctx, new HollowedModel(ctx.bakeLayer(LAYER)), 0.5f * scale);
        this.texture = VestigeIds.id("textures/entity/" + textureName + ".png");
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(Hollowed entity) {
        return texture;
    }

    @Override
    protected void scale(Hollowed entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }
}
