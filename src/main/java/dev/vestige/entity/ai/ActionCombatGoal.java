package dev.vestige.entity.ai;

import dev.vestige.entity.EnemyProfile;
import dev.vestige.entity.VestigeMob;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Combat behaviour shared by VESTIGE enemies (GDD §7.13): close in, hold spacing, strafe, then
 * commit to a telegraphed attack picked from the profile's table by range, weight and cooldown.
 * The pause between attacks and the attack-token limit keep fights readable.
 */
public final class ActionCombatGoal extends Goal {
    private final VestigeMob mob;
    private int pause;
    private int strafeTimer;
    private float strafeSide = 1f;
    private int repathTimer;

    public ActionCombatGoal(VestigeMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.profile().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pause = 10 + mob.getRandom().nextInt(10);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0f, 0f);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        EnemyProfile profile = mob.profile().orElse(null);
        if (target == null || profile == null || mob.busy()) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
        double dist = Math.sqrt(mob.distanceToSqr(target.getX(), mob.getY(), target.getZ()));
        long now = mob.level().getGameTime();

        if (pause > 0) {
            pause--;
        } else if (mob.hasLineOfSight(target)) {
            Optional<EnemyProfile.Attack> attack = pick(profile.attacks(), dist, now);
            if (attack.isPresent() && AttackTokens.acquire(target.getUUID(), mob.getId(), now)) {
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(0f, 0f);
                mob.startAttack(attack.get(), target);
                EnemyProfile.Engage e = profile.engage();
                pause = e.pauseMin() + mob.getRandom().nextInt(Math.max(1, e.pauseMax() - e.pauseMin() + 1));
                return;
            }
        }
        move(target, profile.engage(), dist);
    }

    private void move(LivingEntity target, EnemyProfile.Engage engage, double dist) {
        if (--strafeTimer <= 0) {
            strafeTimer = 40 + mob.getRandom().nextInt(40);
            strafeSide = mob.getRandom().nextBoolean() ? 1f : -1f;
        }
        if (dist > engage.max()) {
            if (--repathTimer <= 0) {
                repathTimer = 8;
                mob.getNavigation().moveTo(target, 1.0);
            }
            return;
        }
        mob.getNavigation().stop();
        float forward = dist < engage.min() ? -0.5f : 0f;
        mob.getMoveControl().strafe(forward, strafeSide * engage.strafeSpeed());
        mob.setYRot(Mth.rotateIfNecessary(mob.getYRot(), mob.yHeadRot, 30f));
    }

    private Optional<EnemyProfile.Attack> pick(List<EnemyProfile.Attack> table, double dist, long now) {
        int total = 0;
        for (EnemyProfile.Attack a : table) {
            if (usable(a, dist, now)) {
                total += a.weight();
            }
        }
        if (total == 0) {
            return Optional.empty();
        }
        int roll = mob.getRandom().nextInt(total);
        for (EnemyProfile.Attack a : table) {
            if (usable(a, dist, now)) {
                roll -= a.weight();
                if (roll < 0) {
                    return Optional.of(a);
                }
            }
        }
        return Optional.empty();
    }

    private boolean usable(EnemyProfile.Attack a, double dist, long now) {
        return dist >= a.minRange() && dist <= a.maxRange() && mob.cooldownReady(a.action(), now);
    }
}
