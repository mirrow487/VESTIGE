package dev.vestige.combat.damage;

import dev.vestige.core.Net;
import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Applies one action hit: damage formula, poise, hitstop and client feedback. */
public final class CombatDamage {
    /** Critical multiplier on staggered targets until ripostes arrive (GDD §7.8). */
    public static final float STAGGER_CRIT = 1.5f;

    private CombatDamage() {}

    /** Result of a hit, for callers and tests. */
    public record Outcome(boolean landed, float damage, boolean critical) {}

    /**
     * @param attackRating weapon AR (players) or enemy action damage
     * @param poiseScale   multiplier on the action's poise damage
     */
    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, double attackRating,
                                float poiseScale, ResourceKey<DamageType> type) {
        var power = action.def.power();
        double motion = power.motionValue() + action.def.charge().map(c -> c.motionBonus() * action.chargeRatio()).orElse(0f);
        double raw = DamageCalculator.raw(attackRating, motion, 0.0);
        double defense = target instanceof CombatTarget ct ? ct.combatDefense()
                : DamageCalculator.vanillaDefense(target.getAttributeValue(Attributes.ARMOR), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        boolean critical = target instanceof CombatTarget ct && ct.isStaggered();
        float damage = (float) (DamageCalculator.afterDefense(raw, defense) * (critical ? STAGGER_CRIT : 1f));

        boolean landed = target.hurt(CombatDamageTypes.source(attacker, type), damage);
        if (!landed) {
            return new Outcome(false, 0f, false);
        }
        float poise = (power.poiseDamage() + action.def.charge().map(c -> c.poiseBonus() * action.chargeRatio()).orElse(0f)) * poiseScale;
        if (target instanceof CombatTarget ct) {
            ct.receivePoiseDamage(poise, attacker);
        } else {
            Vec3 push = target.position().subtract(attacker.position());
            target.knockback(0.25 + 0.01 * poise, -push.x, -push.z);
        }
        int hitstop = action.def.timing().hitstop();
        action.addHitstop(hitstop);
        if (target instanceof CombatTarget ct) {
            ct.applyHitstop(hitstop);
        }
        Vec3 at = target.getBoundingBox().getCenter();
        boolean heavy = power.motionValue() >= 1.4f || action.chargeRatio() > 0f;
        Net.sendAround(target, new CombatPayloads.HitFeedback(
                attacker.getId(), target.getId(), (float) at.x, (float) at.y, (float) at.z, hitstop, heavy, critical), true);
        return new Outcome(true, damage, critical);
    }
}
