package dev.vestige.client.entity;

import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.combat.anim.Channel;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.combat.anim.PoseTimeline;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.entity.mob.AnimatedMob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for VESTIGE enemies (geometry and keyframes from {@code tools/gen_models.py}).
 * Keyframed idle/walk/death come from the animation file; attack poses are sampled from the shared
 * combat timeline and blended over them per bone through a {@link Binding} table, so humanoids and
 * quadrupeds share one pose language (ADR-011 rev. 2).
 */
public class TimelineGeoModel extends GeoModel<AnimatedMob> {
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

    /** How one channel drives one bone: axis 0/1/2 = x/y/z rotation, 3 = y position (model pixels). */
    public record Binding(Channel channel, String bone, int axis, float sign) {
        static Binding rot(Channel c, String bone, int axis) {
            return new Binding(c, bone, axis, axis == 0 ? PITCH : axis == 1 ? YAW : ROLL);
        }

        /** Elbow/knee bends rotate a child bone about x; arms bend forward (+1), legs backward (-1). */
        static Binding bend(Channel c, String bone, float sign) {
            return new Binding(c, bone, 0, sign);
        }
    }

    /** Biped enemies (Hollowed family). */
    public static final List<Binding> HUMANOID = List.of(
            Binding.rot(Channel.HEAD_PITCH, "head", 0), Binding.rot(Channel.HEAD_YAW, "head", 1),
            Binding.rot(Channel.TORSO_PITCH, "torso", 0), Binding.rot(Channel.TORSO_YAW, "torso", 1), Binding.rot(Channel.TORSO_ROLL, "torso", 2),
            Binding.rot(Channel.RIGHT_ARM_PITCH, "right_arm", 0), Binding.rot(Channel.RIGHT_ARM_YAW, "right_arm", 1),
            Binding.rot(Channel.RIGHT_ARM_ROLL, "right_arm", 2),
            Binding.rot(Channel.LEFT_ARM_PITCH, "left_arm", 0), Binding.rot(Channel.LEFT_ARM_YAW, "left_arm", 1),
            Binding.rot(Channel.LEFT_ARM_ROLL, "left_arm", 2),
            Binding.rot(Channel.RIGHT_LEG_PITCH, "right_leg", 0), Binding.rot(Channel.LEFT_LEG_PITCH, "left_leg", 0),
            Binding.rot(Channel.BODY_PITCH, "root", 0), Binding.rot(Channel.BODY_ROLL, "root", 2),
            new Binding(Channel.BODY_Y, "root", 3, 1f),
            Binding.bend(Channel.RIGHT_ARM_BEND, "right_forearm", 1f), Binding.bend(Channel.LEFT_ARM_BEND, "left_forearm", 1f),
            Binding.bend(Channel.RIGHT_LEG_BEND, "right_shin", -1f), Binding.bend(Channel.LEFT_LEG_BEND, "left_shin", -1f));

    /**
     * Four-legged enemies: "arms" are the forelegs, "legs" the hind legs, the torso channels bend the
     * neck (positive pitch lowers the head), and the body channels move the whole animal.
     */
    public static final List<Binding> QUADRUPED = List.of(
            Binding.rot(Channel.HEAD_PITCH, "head", 0), Binding.rot(Channel.HEAD_YAW, "head", 1), Binding.rot(Channel.HEAD_ROLL, "head", 2),
            Binding.rot(Channel.TORSO_PITCH, "neck", 0), Binding.rot(Channel.TORSO_YAW, "neck", 1), Binding.rot(Channel.TORSO_ROLL, "body", 2),
            Binding.rot(Channel.RIGHT_ARM_PITCH, "front_right_leg", 0), Binding.rot(Channel.LEFT_ARM_PITCH, "front_left_leg", 0),
            Binding.rot(Channel.RIGHT_LEG_PITCH, "hind_right_leg", 0), Binding.rot(Channel.LEFT_LEG_PITCH, "hind_left_leg", 0),
            Binding.rot(Channel.BODY_PITCH, "root", 0), Binding.rot(Channel.BODY_ROLL, "root", 2), Binding.rot(Channel.BODY_YAW, "root", 1),
            new Binding(Channel.BODY_Y, "root", 3, 1f),
            Binding.bend(Channel.RIGHT_ARM_BEND, "front_right_shin", -1f), Binding.bend(Channel.LEFT_ARM_BEND, "front_left_shin", -1f),
            Binding.bend(Channel.RIGHT_LEG_BEND, "hind_right_shin", 1f), Binding.bend(Channel.LEFT_LEG_BEND, "hind_left_shin", 1f));

    private final List<Binding> bindings;

    public TimelineGeoModel(List<Binding> bindings) {
        this.bindings = bindings;
    }

    public static void clearCache() {
        TIMELINES.clear();
    }

    private static String path(AnimatedMob e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    @Override
    public ResourceLocation getModelResource(AnimatedMob e) {
        return VestigeIds.id("geo/entity/" + path(e) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AnimatedMob e) {
        return VestigeIds.id("textures/entity/" + path(e) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(AnimatedMob e) {
        return VestigeIds.id("animations/entity/" + path(e) + ".animation.json");
    }

    @Override
    public void setCustomAnimations(AnimatedMob entity, long instanceId, AnimationState<AnimatedMob> state) {
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
        for (Binding b : bindings) {
            Float value = v.get(b.channel());
            if (value == null) {
                continue;
            }
            getBone(b.bone()).ifPresent(bone -> {
                if (b.axis() == 3) {
                    bone.setPosY(Mth.lerp(w, bone.getPosY(), value * b.sign()));
                } else {
                    float target = (float) Math.toRadians(value) * b.sign();
                    set(bone, b.axis(), Mth.lerp(w, get(bone, b.axis()), target));
                }
            });
        }
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
