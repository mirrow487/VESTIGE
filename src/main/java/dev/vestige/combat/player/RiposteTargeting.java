package dev.vestige.combat.player;

import dev.vestige.combat.CombatTarget;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who a light attack becomes a critical against (GDD §7.7–7.8): a <b>riposte</b> on an enemy that is
 * reeling (parried or poise-broken) in front of the player, or a <b>backstab</b> on an enemy facing
 * away. Geometry helpers are pure and unit-tested.
 */
public final class RiposteTargeting {
    public static final double FRONT_RANGE = 2.8;
    public static final double FRONT_CONE_DEG = 50.0;
    public static final double BACK_RANGE = 2.2;
    /** The target must face this far away from the player for a backstab. */
    public static final double BACK_FACING_DEG = 110.0;

    private RiposteTargeting() {}

    /** Absolute angle (degrees) between a facing yaw and the direction from one point to another. */
    public static double angleOff(float facingYaw, double fromX, double fromZ, double toX, double toZ) {
        double yawTo = Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0;
        return Math.abs(Mth.wrapDegrees(yawTo - facingYaw));
    }

    public static boolean frontRiposte(double distance, double playerAngleToTarget) {
        return distance <= FRONT_RANGE && playerAngleToTarget <= FRONT_CONE_DEG;
    }

    public static boolean backstab(double distance, double playerAngleToTarget, double targetFacingAwayAngle) {
        return distance <= BACK_RANGE && playerAngleToTarget <= FRONT_CONE_DEG && targetFacingAwayAngle >= BACK_FACING_DEG;
    }

    /** Server-side: best critical target, using the targets' own combat state. */
    public static Optional<LivingEntity> find(Player player) {
        return find(player, e -> ((CombatTarget) e).isReeling(), e -> ((CombatTarget) e).canBeCritical());
    }

    /**
     * Best critical target for the player. The client passes predicates backed by its mirrored
     * action state, so prediction and the server apply the same geometry.
     */
    public static Optional<LivingEntity> find(Player player, java.util.function.Predicate<LivingEntity> reeling,
                                              java.util.function.Predicate<LivingEntity> criticalable) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FRONT_RANGE + 1.0),
                        e -> e != player && e.isAlive() && e instanceof CombatTarget && criticalable.test(e)).stream()
                .filter(e -> qualifies(player, e, reeling.test(e)))
                .min(Comparator.comparingDouble(e -> e.distanceTo(player)));
    }

    public static boolean qualifies(Player player, LivingEntity e, boolean reeling) {
        double dist = Math.sqrt(player.distanceToSqr(e.getX(), player.getY(), e.getZ())) - e.getBbWidth() / 2.0;
        double aim = angleOff(player.getYRot(), player.getX(), player.getZ(), e.getX(), e.getZ());
        if (reeling && frontRiposte(dist, aim)) {
            return true;
        }
        // How far the target's facing points away from the player: 0 = looking at them, 180 = back turned.
        double away = angleOff(e.getYRot(), e.getX(), e.getZ(), player.getX(), player.getZ());
        return backstab(dist, aim, away) && player.hasLineOfSight(e);
    }
}
