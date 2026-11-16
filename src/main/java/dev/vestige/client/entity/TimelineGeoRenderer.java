package dev.vestige.client.entity;

import dev.vestige.entity.mob.AnimatedMob;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for VESTIGE enemies; their own death animations replace vanilla's sideways tip-over. */
public class TimelineGeoRenderer extends GeoEntityRenderer<AnimatedMob> {
    public TimelineGeoRenderer(EntityRendererProvider.Context ctx, List<TimelineGeoModel.Binding> bindings, float shadow, float scale) {
        super(ctx, new TimelineGeoModel(bindings));
        this.shadowRadius = shadow;
        withScale(scale);
    }

    @Override
    protected float getDeathMaxRotation(AnimatedMob animatable) {
        return 0f;
    }
}
