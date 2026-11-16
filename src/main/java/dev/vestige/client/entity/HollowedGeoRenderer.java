package dev.vestige.client.entity;

import dev.vestige.entity.mob.Hollowed;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for Hollowed-family enemies; their own death animation replaces vanilla's sideways tip-over. */
public class HollowedGeoRenderer extends GeoEntityRenderer<Hollowed> {
    public HollowedGeoRenderer(EntityRendererProvider.Context ctx, float shadow, float scale) {
        super(ctx, new HollowedGeoModel());
        this.shadowRadius = shadow;
        withScale(scale);
    }

    @Override
    protected float getDeathMaxRotation(Hollowed animatable) {
        return 0f;
    }
}
