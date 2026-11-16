package dev.vestige.combat.player;

import dev.vestige.combat.CombatData;
import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.combat.resonance.ResonanceRules;
import dev.vestige.combat.resonance.ResonanceService;
import dev.vestige.config.ServerConfig;
import dev.vestige.core.Net;
import dev.vestige.vitals.StaminaService;
import dev.vestige.weapon.ShieldProfile;
import dev.vestige.weapon.ShieldProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Resolves an incoming VESTIGE attack against a player's parry or guard (GDD §7.7), before damage.
 * Parry beats guard: a parriable (white-glint) attack landing in an open parry window is deflected
 * and staggers the attacker. Otherwise a raised guard blocks frontal, blockable attacks for stamina.
 */
public final class PlayerDefense {
    public static final double PARRY_CONE_DEG = 100.0;
    public static final double GUARD_CONE_DEG = 120.0;
    /** Weapon guard (no shield): weaker than any shield. */
    public static final float WEAPON_GUARD_ABSORPTION = 0.55f;
    public static final float WEAPON_GUARD_STABILITY = 30f;
    public static final int PARRY_HITSTOP = 7;
    public static final int GUARD_BREAK_STUN = 24;

    private PlayerDefense() {}

    /** Outcome; {@code damageFactor} multiplies the damage that still goes through. */
    public record Result(Kind kind, float damageFactor) {
        public enum Kind { NONE, PARRIED, BLOCKED, GUARD_BROKEN }

        static final Result NONE = new Result(Kind.NONE, 1f);
    }

    /** Pure: stamina lost blocking a hit of {@code damage} with a given stability. */
    public static float blockStaminaCost(float damage, float stability) {
        return damage * (1f - stability / 100f) * 0.9f + 3f;
    }

    public static Result resolve(ServerPlayer player, LivingEntity attacker, RunningAction attack, float incomingDamage) {
        PlayerCombatState st = PlayerCombat.state(player);
        RunningAction current = st.machine().current();
        if (current == null) {
            return Result.NONE;
        }
        double off = RiposteTargeting.angleOff(player.getYRot(), player.getX(), player.getZ(), attacker.getX(), attacker.getZ());
        int tolerance = ServerConfig.IFRAME_LATENCY_TOLERANCE.get();

        if (attack.def.parriable() && current.inParryWindow(tolerance) && off <= PARRY_CONE_DEG / 2) {
            parry(player, attacker, attack, current);
            return new Result(Result.Kind.PARRIED, 0f);
        }
        if (attack.def.blockable() && current.guarding() && off <= GUARD_CONE_DEG / 2) {
            return block(player, attacker, incomingDamage, current);
        }
        return Result.NONE;
    }

    private static void parry(ServerPlayer player, LivingEntity attacker, RunningAction attack, RunningAction current) {
        current.parrySucceeded = true;
        current.addHitstop(PARRY_HITSTOP);
        if (attacker instanceof CombatTarget ct) {
            ct.receiveParry(player);
        }
        ResonanceService.add(player, ResonanceRules.PARRY);
        PlayerCombat.state(player).lastCombatTick = player.level().getGameTime();
        var at = attacker.getBoundingBox().getCenter().add(player.getBoundingBox().getCenter()).scale(0.5);
        Net.sendAround(player, new CombatPayloads.HitFeedback(attacker.getId(), player.getId(), (float) at.x, (float) at.y + 0.3f,
                (float) at.z, PARRY_HITSTOP, true, false, CombatPayloads.HitKind.PARRY.ordinal()), true);
        // Arts with an on_parry counter strike back immediately.
        current.def.art().flatMap(a -> a.onParry()).ifPresent(counter ->
                CombatData.resolve(false, new ActionRef(current.ref.moveset(), counter)).ifPresent(def -> {
                    RunningAction c = new RunningAction(new ActionRef(current.ref.moveset(), counter), def, -1, Float.NaN,
                            player.level().getGameTime(), RunningAction.Kind.ART);
                    PlayerCombat.adoptServerAction(player, c);
                }));
    }

    private static Result block(ServerPlayer player, LivingEntity attacker, float damage, RunningAction current) {
        ShieldProfile shield = ShieldProfiles.of(player.getOffhandItem(), false).filter(s -> s.kind() != ShieldProfile.Kind.BUCKLER).orElse(null);
        float absorption = shield == null ? WEAPON_GUARD_ABSORPTION : shield.absorption();
        float stability = shield == null ? WEAPON_GUARD_STABILITY : shield.stability();
        float cost = blockStaminaCost(damage, stability);
        float stamina = StaminaService.get(player).current();
        PlayerCombat.state(player).lastCombatTick = player.level().getGameTime();
        var at = player.getBoundingBox().getCenter();
        if (!player.isCreative() && stamina < cost) {
            StaminaService.spend(player, stamina);
            PlayerCombat.breakGuard(player, current);
            Net.sendAround(player, new CombatPayloads.HitFeedback(attacker.getId(), player.getId(), (float) at.x, (float) at.y, (float) at.z,
                    4, true, false, CombatPayloads.HitKind.GUARD_BREAK.ordinal()), true);
            return new Result(Result.Kind.GUARD_BROKEN, 1f - absorption * 0.5f);
        }
        StaminaService.spend(player, cost);
        ResonanceService.add(player, ResonanceRules.BLOCK);
        current.addHitstop(2);
        Net.sendAround(player, new CombatPayloads.HitFeedback(attacker.getId(), player.getId(), (float) at.x, (float) at.y + 0.2f, (float) at.z,
                2, false, false, CombatPayloads.HitKind.BLOCK.ordinal()), true);
        return new Result(Result.Kind.BLOCKED, 1f - absorption);
    }
}
