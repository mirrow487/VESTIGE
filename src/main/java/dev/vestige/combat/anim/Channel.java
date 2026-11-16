package dev.vestige.combat.anim;

/**
 * Animatable channels, one convention for players and enemies (verified in-game by the calibration
 * driver scenario). Rotations in degrees:
 * <ul>
 *   <li>pitch: negative raises a limb forward/up; positive torso pitch leans forward; positive
 *       body pitch tips the whole model forward (forward roll).</li>
 *   <li>yaw: positive turns toward the entity's right (arm sweeps right, torso twists right).</li>
 *   <li>roll: positive moves the right arm outward; positive torso/body roll leans right.</li>
 *   <li>item pitch: positive tips the held item's tip forward (a sword held out points ahead).</li>
 * </ul>
 * Back ends translate where needed (playerAnimator mirrors torso/body yaw and roll).
 */
public enum Channel {
    HEAD_PITCH("head", "pitch"), HEAD_YAW("head", "yaw"), HEAD_ROLL("head", "roll"),
    TORSO_PITCH("torso", "pitch"), TORSO_YAW("torso", "yaw"), TORSO_ROLL("torso", "roll"),
    RIGHT_ARM_PITCH("rightArm", "pitch"), RIGHT_ARM_YAW("rightArm", "yaw"), RIGHT_ARM_ROLL("rightArm", "roll"), RIGHT_ARM_BEND("rightArm", "bend"),
    LEFT_ARM_PITCH("leftArm", "pitch"), LEFT_ARM_YAW("leftArm", "yaw"), LEFT_ARM_ROLL("leftArm", "roll"), LEFT_ARM_BEND("leftArm", "bend"),
    RIGHT_LEG_PITCH("rightLeg", "pitch"), RIGHT_LEG_YAW("rightLeg", "yaw"), RIGHT_LEG_ROLL("rightLeg", "roll"), RIGHT_LEG_BEND("rightLeg", "bend"),
    LEFT_LEG_PITCH("leftLeg", "pitch"), LEFT_LEG_YAW("leftLeg", "yaw"), LEFT_LEG_ROLL("leftLeg", "roll"), LEFT_LEG_BEND("leftLeg", "bend"),
    BODY_PITCH("body", "pitch"), BODY_YAW("body", "yaw"), BODY_ROLL("body", "roll"),
    BODY_X("body", "x"), BODY_Y("body", "y"), BODY_Z("body", "z"),
    ITEM_PITCH("rightItem", "pitch"), ITEM_YAW("rightItem", "yaw"), ITEM_ROLL("rightItem", "roll");

    public final String part;
    public final String axis;

    Channel(String part, String axis) {
        this.part = part;
        this.axis = axis;
    }

    public boolean isRotation() {
        return !axis.equals("x") && !axis.equals("y") && !axis.equals("z");
    }
}
