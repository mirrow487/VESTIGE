package dev.vestige.client.rpg;

import dev.vestige.VestigeIds;
import dev.vestige.player.EmberGhost;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The Ember-Ghost: a translucent, self-lit hooded shade of embers (GeckoLib, tools/gen_models.py). */
public class EmberGhostRenderer extends GeoEntityRenderer<EmberGhost> {
    public EmberGhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(VestigeIds.id("ember_ghost")));
        this.shadowRadius = 0f;
    }

    @Override
    public RenderType getRenderType(EmberGhost animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public int getPackedOverlay(EmberGhost animatable, float u, float partialTick) {
        return net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
    }
}
