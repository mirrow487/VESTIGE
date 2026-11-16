package dev.vestige.client.entity;

import dev.vestige.client.combat.ClientActions;
import dev.vestige.combat.anim.Channel;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.combat.anim.PoseTimeline;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.entity.mob.Hollowed;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Humanoid model posed from the shared combat timeline (same pose data as player animations).
 * Adds a slumped "hollow" idle when not acting.
 */
public class HollowedModel extends HumanoidModel<Hollowed> {
    private static final Map<String, PoseTimeline> TIMELINES = new HashMap<>();

    public HollowedModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 32);
    }

    public static void clearCache() {
        TIMELINES.clear();
    }

    @Override
    public void setupAnim(Hollowed entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Hollow idle: hunched, head low, arms hanging slightly forward and swaying.
        float sway = Mth.sin(ageInTicks * 0.067f) * 0.05f;
        rightArm.xRot += -0.25f + sway;
        leftArm.xRot += -0.20f - sway;
        rightArm.zRot += 0.06f;
        leftArm.zRot -= 0.06f;
        head.xRot += 0.25f;
        applyTorsoPitch(0.18f);

        RunningAction action = ClientActions.of(entity);
        if (action == null || action.def.pose().isEmpty()) {
            return;
        }
        PoseTimeline timeline = TIMELINES.computeIfAbsent(action.ref + "|" + action.def.total(), k ->
                PoseLibrary.get(action.def.pose()).map(keys -> PoseTimeline.build(keys,
                        action.def.timing().windup(), action.def.timing().active(), action.def.timing().recovery())).orElse(null));
        if (timeline == null) {
            return;
        }
        float t = ClientActions.animTick(action, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        Map<Channel, Float> v = timeline.sampleAll(t);
        resetCombatParts();
        applyTorsoPitch(rad(v, Channel.TORSO_PITCH) + 0.18f);
        set(v, Channel.HEAD_PITCH, head, 0, 0.25f);
        set(v, Channel.HEAD_YAW, head, 1, 0f);
        set(v, Channel.RIGHT_ARM_PITCH, rightArm, 0, 0f);
        set(v, Channel.RIGHT_ARM_YAW, rightArm, 1, 0f);
        set(v, Channel.RIGHT_ARM_ROLL, rightArm, 2, 0.06f);
        set(v, Channel.LEFT_ARM_PITCH, leftArm, 0, 0f);
        set(v, Channel.LEFT_ARM_YAW, leftArm, 1, 0f);
        set(v, Channel.LEFT_ARM_ROLL, leftArm, 2, -0.06f);
        set(v, Channel.RIGHT_LEG_PITCH, rightLeg, 0, 0f);
        set(v, Channel.LEFT_LEG_PITCH, leftLeg, 0, 0f);
        float torsoYaw = rad(v, Channel.TORSO_YAW);
        if (torsoYaw != 0f) {
            // Twist like vanilla's attack pose: shoulders follow the torso.
            body.yRot = torsoYaw;
            rightArm.z = Mth.sin(torsoYaw) * 5f;
            rightArm.x = -Mth.cos(torsoYaw) * 5f;
            leftArm.z = -Mth.sin(torsoYaw) * 5f;
            leftArm.x = Mth.cos(torsoYaw) * 5f;
            rightArm.yRot += torsoYaw;
            leftArm.yRot += torsoYaw;
        }
        hat.copyFrom(head);
    }

    private void resetCombatParts() {
        body.yRot = 0f;
        rightArm.x = -5f;
        leftArm.x = 5f;
        rightArm.z = 0f;
        leftArm.z = 0f;
    }

    /** Leans the upper body forward by moving parts the way vanilla's crouch does. */
    private void applyTorsoPitch(float pitch) {
        float s = Mth.clamp(pitch / 0.5f, -0.3f, 1.6f);
        body.xRot = pitch;
        body.y = 3.2f * Math.max(0f, s);
        head.y = 4.2f * Math.max(0f, s);
        rightArm.y = 2f + 3.2f * Math.max(0f, s);
        leftArm.y = 2f + 3.2f * Math.max(0f, s);
        rightLeg.z = 4f * Math.max(0f, s) * 0.6f;
        leftLeg.z = 4f * Math.max(0f, s) * 0.6f;
        hat.copyFrom(head);
    }

    private static float rad(Map<Channel, Float> v, Channel c) {
        Float d = v.get(c);
        return d == null ? 0f : (float) Math.toRadians(d);
    }

    /** Overrides an axis only for channels the timeline animates (others keep walk/idle). */
    private static void set(Map<Channel, Float> v, Channel c, ModelPart part, int axis, float base) {
        Float d = v.get(c);
        if (d == null) {
            return;
        }
        float value = (float) Math.toRadians(d) + base;
        switch (axis) {
            case 0 -> part.xRot = value;
            case 1 -> part.yRot = value;
            default -> part.zRot = value;
        }
    }
}
