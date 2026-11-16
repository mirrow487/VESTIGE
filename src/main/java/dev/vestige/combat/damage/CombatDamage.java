package dev.vestige.combat.damage;

import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.combat.player.PlayerDefense;
import dev.vestige.core.Net;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Applies one action hit: defence (parry/guard), damage formula, poise, hitstop and client feedback. */
public final class CombatDamage {
    /** Critical multiplier on staggered targets hit by ordinary attacks (ripostes use their own motion value). */
    public static final float STAGGER_CRIT = 1.5f;

    private CombatDamage() {}

    /** Result of a hit, for callers and tests. */
    public record Outcome(boolean landed, float damage, boolean critical, PlayerDefense.Result.Kind defense) {}

    /**
     * How a hit is delivered.
     *
     * @param attackerHitstop the attacker's action pauses on impact (melee only)
     * @param motionOverride  replaces the action's motion value when not NaN (clouds, keen criticals)
     * @param forceCritical   counts as a critical even on a target that is not reeling (Quickstep)
     * @param poise           deals poise damage and stagger
     * @param feedback        sends hit sparks/sound to clients
     */
    public record Options(boolean attackerHitstop, float motionOverride, boolean forceCritical, boolean poise, boolean feedback) {
        public static final Options MELEE = new Options(true, Float.NaN, false, true, true);
        public static final Options RANGED = new Options(false, Float.NaN, false, true, true);

        public static Options cloud(float motionValue) {
            return new Options(false, motionValue, false, false, false);
        }

        public Options keen(float criticalMotion) {
            return new Options(attackerHitstop, criticalMotion, true, poise, feedback);
        }
    }

    /**
     * @param attackRating weapon AR (players) or enemy action damage
     * @param poiseScale   multiplier on the action's poise damage (weapon factor, Resonant bonus)
     * @param damageBonus  additive damage bonus (e.g. 0.3 for a Crescendo art)
     */
    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, double attackRating,
                                float poiseScale, float damageBonus, ResourceKey<DamageType> type) {
        return apply(attacker, target, action, attackRating, poiseScale, damageBonus, type, Options.MELEE);
    }

    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, double attackRating,
                                float poiseScale, float damageBonus, ResourceKey<DamageType> type, Options opt) {
        var power = action.def.power();
        boolean reeling = target instanceof CombatTarget rt && rt.isReeling();
        // A keen strike on a reeling target lands with the weapon's riposte motion value instead of x1.5.
        boolean keenRiposte = opt.forceCritical() && reeling && !Float.isNaN(opt.motionOverride());
        double motion = !Float.isNaN(opt.motionOverride()) && (!opt.forceCritical() || keenRiposte) ? opt.motionOverride()
                : power.motionValue() + action.def.charge().map(c -> c.motionBonus() * action.chargeRatio()).orElse(0f);
        double raw = DamageCalculator.raw(attackRating, motion, damageBonus);
        double defense = target instanceof CombatTarget ct ? ct.combatDefense()
                : DamageCalculator.vanillaDefense(target.getAttributeValue(Attributes.ARMOR), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        boolean riposte = action.kind == RunningAction.Kind.RIPOSTE || keenRiposte;
        boolean critical = riposte || reeling || opt.forceCritical();
        float damage = (float) (DamageCalculator.afterDefense(raw, defense) * (critical && !riposte ? STAGGER_CRIT : 1f));

        PlayerDefense.Result.Kind defended = PlayerDefense.Result.Kind.NONE;
        if (target instanceof ServerPlayer player) {
            PlayerDefense.Result r = PlayerDefense.resolve(player, attacker, action, damage);
            defended = r.kind();
            if (r.kind() == PlayerDefense.Result.Kind.PARRIED) {
                return new Outcome(false, 0f, false, defended);
            }
            damage *= r.damageFactor();
        }

        boolean landed = damage > 0f && target.hurt(CombatDamageTypes.source(attacker, type), damage);
        if (!landed) {
            return new Outcome(false, 0f, false, defended);
        }
        boolean blocked = defended == PlayerDefense.Result.Kind.BLOCKED;
        float poise = (power.poiseDamage() + action.def.charge().map(c -> c.poiseBonus() * action.chargeRatio()).orElse(0f)) * poiseScale;
        if (!opt.poise()) {
            // clouds: damage only
        } else if (target instanceof CombatTarget ct) {
            if (action.def.inflictStagger() > 0) {
                ct.forceStagger(action.def.inflictStagger());
            } else if (!riposte) {
                ct.receivePoiseDamage(poise, attacker);
            }
        } else if (!blocked && !(target instanceof ServerPlayer)) {
            Vec3 push = target.position().subtract(attacker.position());
            target.knockback(0.25 + 0.01 * poise, -push.x, -push.z);
        }
        int hitstop = opt.poise() ? action.def.timing().hitstop() : 0;
        if (opt.attackerHitstop()) {
            action.addHitstop(hitstop);
        }
        if (target instanceof CombatTarget ct && hitstop > 0) {
            ct.applyHitstop(hitstop);
        }
        if (opt.feedback() && !blocked && defended != PlayerDefense.Result.Kind.GUARD_BROKEN) {
            Vec3 at = target.getBoundingBox().getCenter();
            boolean heavy = power.motionValue() >= 1.4f || action.chargeRatio() > 0f;
            Net.sendAround(target, new CombatPayloads.HitFeedback(attacker.getId(), target.getId(), (float) at.x, (float) at.y, (float) at.z,
                    hitstop, heavy, critical, (riposte ? CombatPayloads.HitKind.RIPOSTE : opt.attackerHitstop() ? CombatPayloads.HitKind.HIT
                            : CombatPayloads.HitKind.RANGED).ordinal()), true);
        }
        return new Outcome(true, damage, critical, defended);
    }
}
