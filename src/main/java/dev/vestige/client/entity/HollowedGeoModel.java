package dev.vestige.client.entity;

import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.combat.anim.Channel;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.combat.anim.PoseTimeline;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.entity.mob.Hollowed;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Hollowed and Hollowed Brute (geometry and keyframes from
 * {@code tools/gen_models.py}). Keyframed idle/walk/death come from the animation file; attack
 * poses are sampled from the shared combat timeline and blended over them per bone.
 */
public class HollowedGeoModel extends GeoModel<Hollowed> {
    /** Ticks to blend into and out of an action pose. */
    private static final float BLEND_IN = 2.5f;
    private static final float BLEND_OUT = 4f;
    private static final Map<String, PoseTimeline> TIMELINES = new HashMap<>();

    /**
     * Bone-space signs relative to {@link Channel}'s convention (GeckoLib bones are y-up, facing -Z).
     * Verified in-game with the "models" driver scenario: yaw is mirrored, pitch and roll inverted.
     */
    static final float PITCH = -1f;
    static final float YAW = -1f;
    static final float ROLL = -1f;

    public static void clearCache() {
        TIMELINES.clear();
    }

    private static String path(Hollowed e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    @Override
    public ResourceLocation getModelResource(Hollowed e) {
        return VestigeIds.id("geo/entity/" + path(e) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(Hollowed e) {
        return VestigeIds.id("textures/entity/" + path(e) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(Hollowed e) {
        return VestigeIds.id("animations/entity/" + path(e) + ".animation.json");
    }

    @Override
    public void setCustomAnimations(Hollowed entity, long instanceId, AnimationState<Hollowed> state) {
        super.setCustomAnimations(entity, instanceId, state);
        RunningAction action = ClientActions.of(entity);
        if (action == null || action.def.pose().isEmpty() || entity.isDeadOrDying()) {
            return;
        }
        PoseTimeline timeline = TIMELINES.computeIfAbsent(action.ref + "|" + action.def.total(), k ->
                PoseLibrary.get(action.def.pose()).map(keys -> PoseTimeline.build(keys,
                        action.def.timing().windup(), action.def.timing().active(), action.def.timing().recovery())).orElse(null));
        if (timeline == null) {
            return;
        }
        float t = ClientActions.animTick(action, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        float raw = Mth.clamp(Math.min(t / BLEND_IN, (timeline.length() - t) / BLEND_OUT), 0f, 1f);
        final float w = raw * raw * (3f - 2f * raw); // smoothstep
        Map<Channel, Float> v = timeline.sampleAll(t);
        apply(v, w, Channel.HEAD_PITCH, "head", 0);
        apply(v, w, Channel.HEAD_YAW, "head", 1);
        apply(v, w, Channel.TORSO_PITCH, "torso", 0);
        apply(v, w, Channel.TORSO_YAW, "torso", 1);
        apply(v, w, Channel.TORSO_ROLL, "torso", 2);
        apply(v, w, Channel.RIGHT_ARM_PITCH, "right_arm", 0);
        apply(v, w, Channel.RIGHT_ARM_YAW, "right_arm", 1);
        apply(v, w, Channel.RIGHT_ARM_ROLL, "right_arm", 2);
        apply(v, w, Channel.LEFT_ARM_PITCH, "left_arm", 0);
        apply(v, w, Channel.LEFT_ARM_YAW, "left_arm", 1);
        apply(v, w, Channel.LEFT_ARM_ROLL, "left_arm", 2);
        apply(v, w, Channel.RIGHT_LEG_PITCH, "right_leg", 0);
        apply(v, w, Channel.LEFT_LEG_PITCH, "left_leg", 0);
        apply(v, w, Channel.BODY_PITCH, "root", 0);
        apply(v, w, Channel.BODY_ROLL, "root", 2);
        Float drop = v.get(Channel.BODY_Y);
        if (drop != null) {
            // Root height offset in model pixels (kneels, collapses).
            getBone("root").ifPresent(bone -> bone.setPosY(Mth.lerp(w, bone.getPosY(), drop)));
        }
        bend(v, w, Channel.RIGHT_ARM_BEND, "right_forearm", 1f);
        bend(v, w, Channel.LEFT_ARM_BEND, "left_forearm", 1f);
        bend(v, w, Channel.RIGHT_LEG_BEND, "right_shin", -1f);
        bend(v, w, Channel.LEFT_LEG_BEND, "left_shin", -1f);
    }

    private void apply(Map<Channel, Float> v, float w, Channel c, String boneName, int axis) {
        Float deg = v.get(c);
        if (deg == null) {
            return;
        }
        getBone(boneName).ifPresent(bone -> {
            float target = (float) Math.toRadians(deg) * (axis == 0 ? PITCH : axis == 1 ? YAW : ROLL);
            set(bone, axis, Mth.lerp(w, get(bone, axis), target));
        });
    }

    private void bend(Map<Channel, Float> v, float w, Channel c, String boneName, float sign) {
        Float deg = v.get(c);
        if (deg == null) {
            return;
        }
        getBone(boneName).ifPresent(bone -> bone.setRotX(Mth.lerp(w, bone.getRotX(), (float) Math.toRadians(deg) * sign)));
    }

    private static float get(GeoBone b, int axis) {
        return axis == 0 ? b.getRotX() : axis == 1 ? b.getRotY() : b.getRotZ();
    }

    private static void set(GeoBone b, int axis, float value) {
        switch (axis) {
            case 0 -> b.setRotX(value);
            case 1 -> b.setRotY(value);
            default -> b.setRotZ(value);
        }
    }
}
