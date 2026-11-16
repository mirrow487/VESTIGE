package dev.vestige.combat.damage;

import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.player.PlayerDefense;
import dev.vestige.combat.status.StatusService;
import dev.vestige.core.Net;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.StatSheet;
import dev.vestige.stats.Stats;
import dev.vestige.stats.StatusType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Applies one action hit (GDD §7.10): parry/guard, the per-type damage formula with each type's
 * identity, poise, status buildup, hitstop and client feedback.
 */
public final class CombatDamage {
    /** Critical multiplier on staggered targets hit by ordinary attacks (ripostes use their own motion value). */
    public static final float STAGGER_CRIT = 1.5f;
    public static final float PIERCE_COUNTER = 1.25f;
    public static final float STRIKE_POISE = 0.5f;
    public static final float RESONANCE_POISE = 0.8f;
    public static final float RADIANCE_SMITE = 1.3f;
    public static final float RADIANCE_LIFE = 0.04f;
    /** Intrinsic status buildup per point of damage of the matching type. */
    public static final float REND_PER_SLASH = 0.2f, SCORCH_PER_CINDER = 0.6f, HUSHED_PER_HUSH = 0.6f, VOIDBITE_PER_VOID = 0.5f;

    private CombatDamage() {}

    /** Result of a hit, for callers and tests. {@code dealt} is the damage per type before guarding. */
    public record Outcome(boolean landed, float damage, boolean critical, PlayerDefense.Result.Kind defense, float poise,
                          DamageSpread dealt) {}

    /**
     * How a hit is delivered.
     *
     * @param attackerHitstop the attacker's action pauses on impact (melee only)
     * @param motionOverride  replaces the action's motion value when not NaN (clouds, keen criticals)
     * @param forceCritical   counts as a critical even on a target that is not reeling (Quickstep)
     * @param poise           deals poise damage and stagger
     * @param feedback        sends hit sparks/sound to clients
     * @param status          extra status buildup per hit (weapon, rings)
     */
    public record Options(boolean attackerHitstop, float motionOverride, boolean forceCritical, boolean poise, boolean feedback,
                          Map<StatusType, Float> status) {
        public static final Options MELEE = new Options(true, Float.NaN, false, true, true, Map.of());
        public static final Options RANGED = new Options(false, Float.NaN, false, true, true, Map.of());

        public static Options cloud(float motionValue) {
            return new Options(false, motionValue, false, false, false, Map.of());
        }

        public Options keen(float criticalMotion) {
            return new Options(attackerHitstop, criticalMotion, true, poise, feedback, status);
        }

        public Options withStatus(Map<StatusType, Float> extra) {
            return new Options(attackerHitstop, motionOverride, forceCritical, poise, feedback, extra);
        }
    }

    /** Compatibility: a single attack rating of the default physical type (Slash). */
    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, double attackRating,
                                float poiseScale, float damageBonus, ResourceKey<DamageType> type) {
        return apply(attacker, target, action, DamageSpread.single(dev.vestige.stats.DamageType.SLASH, (float) attackRating), poiseScale,
                damageBonus, type, Options.MELEE);
    }

    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, DamageSpread attack,
                                float poiseScale, float damageBonus, ResourceKey<DamageType> type) {
        return apply(attacker, target, action, attack, poiseScale, damageBonus, type, Options.MELEE);
    }

    /** Defence per type of any living target: VESTIGE stats, player sheet, or vanilla armor (physical only). */
    public static DamageSpread defenses(LivingEntity target) {
        if (target instanceof CombatTarget ct) {
            return ct.combatDefenses();
        }
        float vanilla = (float) DamageCalculator.vanillaDefense(target.getAttributeValue(Attributes.ARMOR),
                target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (target instanceof Player p) {
            StatSheet s = Stats.of(p);
            return s.defense.plus(DamageSpread.of(Map.of(dev.vestige.stats.DamageType.SLASH, vanilla,
                    dev.vestige.stats.DamageType.STRIKE, vanilla, dev.vestige.stats.DamageType.PIERCE, vanilla)));
        }
        return DamageSpread.of(Map.of(dev.vestige.stats.DamageType.SLASH, vanilla, dev.vestige.stats.DamageType.STRIKE, vanilla,
                dev.vestige.stats.DamageType.PIERCE, vanilla));
    }

    public static DamageSpread absorption(LivingEntity target) {
        if (target instanceof CombatTarget ct) {
            return ct.combatAbsorption();
        }
        if (target instanceof Player p) {
            StatSheet s = Stats.of(p);
            DamageSpread abs = s.absorption;
            if (s.has(dev.vestige.stats.Perks.WICKIRON_SLOW_BURN) && p.getHealth() < p.getMaxHealth() * 0.3f) {
                abs = abs.stackAbsorption(DamageSpread.uniform(0.2f));
            }
            return abs;
        }
        return DamageSpread.EMPTY;
    }

    private static boolean attacking(LivingEntity target) {
        if (target instanceof CombatTarget ct) {
            return ct.isAttacking();
        }
        if (target instanceof ServerPlayer p) {
            RunningAction a = PlayerCombat.state(p).machine().current();
            return a != null && a.def.hit().isPresent() && a.phase() != dev.vestige.combat.action.ActionPhase.RECOVERY;
        }
        return false;
    }

    /**
     * Damage per type against a target, before guarding: the GDD §7.10 formula with each type's
     * identity. Pure apart from reading the target's stats; used by {@link #apply} and tests.
     */
    public static DamageSpread perType(LivingEntity target, DamageSpread attack, double motion, double bonus, DamageSpread typeBonus) {
        DamageSpread def = defenses(target);
        DamageSpread abs = absorption(target);
        String family = target instanceof CombatTarget ct ? ct.family() : "";
        boolean counter = attacking(target);
        boolean hushed = !(target instanceof Player) && StatusService.active(target, StatusType.HUSHED);
        EnumMap<dev.vestige.stats.DamageType, Float> out = new EnumMap<>(dev.vestige.stats.DamageType.class);
        attack.forEach((t, ar) -> {
            double raw = DamageCalculator.raw(ar, motion, bonus + typeBonus.get(t));
            double d = def.get(t) * (t == dev.vestige.stats.DamageType.HUSH ? 0.5 : 1.0);   // Hush slips past half the defence
            double v = DamageCalculator.afterDefense(raw, d);
            if (t != dev.vestige.stats.DamageType.VOID) {                                      // Void ignores absorption
                v *= 1.0 - abs.get(t);
            }
            if (t == dev.vestige.stats.DamageType.PIERCE && counter) {
                v *= PIERCE_COUNTER;
            }
            if (t == dev.vestige.stats.DamageType.RADIANCE && (family.equals("pale") || family.equals("hush") || family.equals("void"))) {
                v *= RADIANCE_SMITE;
            }
            if (target instanceof CombatTarget ct) {
                v *= ct.weakness(t);
            }
            if (hushed) {
                v *= 1.1;
            }
            out.put(t, (float) v);
        });
        return DamageSpread.of(out);
    }

    public static Outcome apply(LivingEntity attacker, LivingEntity target, RunningAction action, DamageSpread attack,
                                float poiseScale, float damageBonus, ResourceKey<DamageType> type, Options opt) {
        var power = action.def.power();
        boolean reeling = target instanceof CombatTarget rt && rt.isReeling();
        // A keen strike on a reeling target lands with the weapon's riposte motion value instead of x1.5.
        boolean keenRiposte = opt.forceCritical() && reeling && !Float.isNaN(opt.motionOverride());
        double motion = !Float.isNaN(opt.motionOverride()) && (!opt.forceCritical() || keenRiposte) ? opt.motionOverride()
                : power.motionValue() + action.def.charge().map(c -> c.motionBonus() * action.chargeRatio()).orElse(0f);
        boolean riposte = action.kind == RunningAction.Kind.RIPOSTE || keenRiposte;
        boolean critical = riposte || reeling || opt.forceCritical();
        DamageSpread typeBonus = attacker instanceof Player p ? Stats.of(p).typeBonus : DamageSpread.EMPTY;
        DamageSpread dealt = perType(target, attack, motion, damageBonus, typeBonus);
        if (critical && !riposte) {
            dealt = dealt.scaled(STAGGER_CRIT);
        }
        float damage = dealt.total();

        PlayerDefense.Result.Kind defended = PlayerDefense.Result.Kind.NONE;
        if (target instanceof ServerPlayer player) {
            // Strike leans on a guard harder; guards weigh it 1.4x for stamina.
            float guardWeight = damage + 0.4f * dealt.get(dev.vestige.stats.DamageType.STRIKE);
            PlayerDefense.Result r = PlayerDefense.resolve(player, attacker, action, guardWeight);
            defended = r.kind();
            if (r.kind() == PlayerDefense.Result.Kind.PARRIED) {
                return new Outcome(false, 0f, false, defended, 0f, dealt);
            }
            if (r.kind() != PlayerDefense.Result.Kind.NONE) {
                // Shields absorb only half of a Resonance blow: sound passes through.
                float absorbed = 1f - r.damageFactor();
                float res = dealt.get(dev.vestige.stats.DamageType.RESONANCE);
                damage = (damage - res) * r.damageFactor() + res * (1f - absorbed * 0.5f);
            }
        }

        boolean landed = damage > 0f && target.hurt(CombatDamageTypes.source(attacker, type), damage);
        if (!landed) {
            return new Outcome(false, 0f, false, defended, 0f, dealt);
        }
        boolean blocked = defended == PlayerDefense.Result.Kind.BLOCKED;
        float strikeShare = dealt.total() > 0f ? dealt.get(dev.vestige.stats.DamageType.STRIKE) / dealt.total() : 0f;
        float basePoise = power.poiseDamage() > 0f || attacker instanceof Player ? power.poiseDamage() : power.damage() * 0.6f;
        float poise = (basePoise + action.def.charge().map(c -> c.poiseBonus() * action.chargeRatio()).orElse(0f)) * poiseScale
                * (1f + STRIKE_POISE * strikeShare) + RESONANCE_POISE * dealt.get(dev.vestige.stats.DamageType.RESONANCE);
        if (!opt.poise()) {
            poise = 0f;
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
        if (!blocked) {
            buildStatuses(attacker, target, dealt, opt.status());
        }
        float radiance = dealt.get(dev.vestige.stats.DamageType.RADIANCE);
        if (radiance > 0f && attacker.isAlive()) {
            // Lamplight sustains its bearer (set directly: already in VESTIGE units).
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + radiance * RADIANCE_LIFE));
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
        return new Outcome(true, damage, critical, defended, poise, dealt);
    }

    /** Intrinsic buildup of each damage type plus the attack's own status buildup. */
    private static void buildStatuses(LivingEntity attacker, LivingEntity target, DamageSpread dealt, Map<StatusType, Float> extra) {
        EnumMap<StatusType, Float> b = new EnumMap<>(StatusType.class);
        b.merge(StatusType.REND, dealt.get(dev.vestige.stats.DamageType.SLASH) * REND_PER_SLASH, Float::sum);
        b.merge(StatusType.SCORCH, dealt.get(dev.vestige.stats.DamageType.CINDER) * SCORCH_PER_CINDER, Float::sum);
        b.merge(StatusType.HUSHED, dealt.get(dev.vestige.stats.DamageType.HUSH) * HUSHED_PER_HUSH, Float::sum);
        b.merge(StatusType.VOIDBITE, dealt.get(dev.vestige.stats.DamageType.VOID) * VOIDBITE_PER_VOID, Float::sum);
        extra.forEach((k, v) -> b.merge(k, v, Float::sum));
        b.forEach((status, amount) -> StatusService.build(target, status, amount, attacker));
    }
}
