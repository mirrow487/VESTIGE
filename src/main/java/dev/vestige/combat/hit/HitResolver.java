package dev.vestige.combat.hit;

import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.HitShape;
import dev.vestige.combat.machine.RunningAction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Server-side hitbox sweep (ARCHITECTURE §7.1): each active tick tests only the slice of the arc
 * swept during that tick, so fast swings cannot tunnel and each target is hit once per action.
 */
public final class HitResolver {
    private HitResolver() {}

    /**
     * Returns entities newly hit by {@code action} on this tick and records them as hit.
     *
     * @param yaw    attacker facing (degrees, Minecraft convention)
     * @param filter which entities may be hit
     */
    public static List<Entity> sweep(LivingEntity attacker, RunningAction action, float yaw, Predicate<Entity> filter) {
        List<Entity> hits = new ArrayList<>();
        if (action.phase() != ActionPhase.ACTIVE || action.def.hit().isEmpty() || action.hitstop() > 0) {
            return hits;
        }
        HitShape shape = action.def.hit().get();
        int activeIndex = action.elapsed() - action.def.timing().windup();
        int activeCount = action.def.timing().active();
        double reach = switch (shape.kind()) {
            case ARC, THRUST -> shape.reach();
            case CIRCLE -> shape.offset() + shape.radius();
        };
        AABB area = attacker.getBoundingBox().inflate(reach + 1.0, Math.max(1.0, shape.yMax()), reach + 1.0);
        for (Entity e : attacker.level().getEntities(attacker, area, e -> e.isAlive() && !e.isSpectator())) {
            if (action.hitEntities.contains(e.getId()) || !filter.test(e)) {
                continue;
            }
            AABB box = e.getBoundingBox();
            double[] local = HitGeometry.toLocal(box.getCenter().x - attacker.getX(), box.getCenter().z - attacker.getZ(), yaw);
            HitGeometry.Target t = new HitGeometry.Target(local[0], local[1], Math.max(box.getXsize(), box.getZsize()) / 2.0,
                    box.minY - attacker.getY(), box.maxY - attacker.getY());
            boolean hit = switch (shape.kind()) {
                case ARC -> {
                    double[] slice = HitGeometry.arcSlice(shape.from(), shape.to(), activeIndex, activeCount);
                    yield HitGeometry.arc(t, shape.reach(), slice[0], slice[1], shape.yMin(), shape.yMax());
                }
                case THRUST -> HitGeometry.thrust(t, shape.reach(), shape.radius(), shape.yMin(), shape.yMax());
                case CIRCLE -> HitGeometry.circle(t, shape.radius(), shape.offset(), shape.yMin(), shape.yMax());
            };
            if (hit && attacker.hasLineOfSight(e)) {
                action.hitEntities.add(e.getId());
                hits.add(e);
            }
        }
        return hits;
    }
}
