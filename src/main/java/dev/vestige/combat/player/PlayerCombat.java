package dev.vestige.combat.player;

import dev.vestige.core.Net;
import dev.vestige.combat.CombatData;
import dev.vestige.combat.CombatModule;
import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.resonance.ResonanceRules;
import dev.vestige.combat.resonance.ResonanceService;
import dev.vestige.weapon.ShieldProfile;
import dev.vestige.weapon.ShieldProfiles;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.damage.CombatDamage;
import dev.vestige.combat.damage.CombatDamageTypes;
import dev.vestige.combat.hit.HitResolver;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.PlayerCombatMachine;
import dev.vestige.combat.machine.PlayerCombatMachine.MoveSetHandle;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.config.ServerConfig;
import dev.vestige.vitals.StaminaService;
import dev.vestige.vitals.VitalsModule;
import dev.vestige.weapon.WeaponProfile;
import dev.vestige.weapon.WeaponProfiles;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative player combat: inputs, action timeline, hits, i-frames (GDD §7). */
public final class PlayerCombat {
    /** Ticks a player stays "in combat" after attacking or being attacked. */
    public static final int IN_COMBAT_TICKS = 100;
    /** Default stun when hit out of hyperarmor. */
    public static final int DEFAULT_HIT_STUN = 8;

    private PlayerCombat() {}

    public static PlayerCombatState state(Player player) {
        return player.getData(CombatModule.PLAYER_STATE);
    }

    public static boolean busy(Player player) {
        return state(player).machine.busy();
    }

    public static boolean inCombat(Player player) {
        return player.level().getGameTime() - state(player).lastCombatTick < IN_COMBAT_TICKS;
    }

    /** Weapon profile and moveset of the item in the main hand, if it is a combat weapon. */
    public static Optional<WeaponProfile> weapon(Player player) {
        return WeaponProfiles.of(player.getMainHandItem(), player.level().isClientSide());
    }

    private static PlayerCombatMachine.Context context(ServerPlayer player) {
        return new PlayerCombatMachine.Context() {
            @Override
            public Optional<MoveSetHandle> weapon() {
                return PlayerCombat.weapon(player).flatMap(w -> CombatData.handle(false, w.moveset()));
            }

            @Override
            public Optional<MoveSetHandle> common() {
                return CombatData.handle(false, CombatData.PLAYER_MOVESET);
            }

            @Override
            public boolean canSpendStamina() {
                return StaminaService.canAct(player);
            }

            @Override
            public boolean sprinting() {
                return player.isSprinting();
            }

            @Override
            public Optional<String> guardAction() {
                return Optional.of(guardActionFor(player));
            }

            @Override
            public boolean canSpendResonance(float cost) {
                return player.isCreative() || ResonanceService.get(player) + 1.0e-4f >= cost;
            }

            @Override
            public boolean riposteAvailable() {
                return RiposteTargeting.find(player).isPresent();
            }
        };
    }

    /** Guard button: a buckler parries; a shield or the bare weapon guards (GDD §7.2, §7.7). Shared with the client. */
    public static String guardActionFor(Player player) {
        return ShieldProfiles.of(player.getOffhandItem(), player.level().isClientSide())
                .map(s -> s.kind() == ShieldProfile.Kind.BUCKLER ? "parry_buckler" : "guard_shield")
                .orElse("guard_weapon");
    }

    /** Entry point for a client input (also used directly by GameTests). */
    public static void handleInput(ServerPlayer player, int seq, int inputId, float dirYaw) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        CombatInput input = CombatInput.byId(inputId);
        PlayerCombatState st = state(player);
        long now = player.level().getGameTime();
        boolean wasHolding = st.machine.current() != null && st.machine.current().holding();
        Optional<PlayerCombatMachine.Start> started = st.machine.input(input, seq, dirYaw, now, context(player));
        if (started.isPresent()) {
            begin(player, st, started.get().action());
        } else if (input == CombatInput.HEAVY_RELEASE || input == CombatInput.GUARD_RELEASE) {
            if (wasHolding) {
                Net.sendAround(player, new CombatPayloads.ActionHold(player.getId(), false), true);
            }
        } else if (!st.machine.hasBuffered()) {
            Net.send(player, new CombatPayloads.ActionEnd(player.getId(), CombatPayloads.EndReason.REJECTED.ordinal(), seq));
        }
    }

    private static void begin(ServerPlayer player, PlayerCombatState st, RunningAction action) {
        StaminaService.spend(player, action.def.power().stamina());
        st.actionItem = player.getMainHandItem().getItem();
        if (action.kind != RunningAction.Kind.DODGE && action.kind != RunningAction.Kind.GUARD) {
            st.lastCombatTick = player.level().getGameTime();
        }
        action.def.art().ifPresent(art -> {
            // Crescendo: a full Resonance bar empowers the art (GDD §8.3).
            if (ResonanceRules.crescendo(ResonanceService.get(player))) {
                action.damageBonus = ResonanceRules.CRESCENDO_ART_BONUS;
            }
            if (!player.isCreative()) {
                ResonanceService.spend(player, art.cost());
            }
        });
        if (action.kind == RunningAction.Kind.RIPOSTE) {
            RiposteTargeting.find(player).ifPresent(target -> ((CombatTarget) target).beginCritical(player, action.def.total() + 6));
        }
        Net.sendAround(player, new CombatPayloads.ActionStart(
                player.getId(), action.ref.toString(), action.seq, action.dirYaw, action.holding()), true);
    }

    /** Server tick for one player; called from PlayerTickEvent.Post and by GameTests. */
    public static void serverTick(ServerPlayer player) {
        PlayerCombatState st = state(player);
        long now = player.level().getGameTime();
        RunningAction current = st.machine.current();
        // Swapping items mid-action cancels it (except dodges, which do not use the weapon).
        if (current != null && current.kind != RunningAction.Kind.DODGE && player.getMainHandItem().getItem() != st.actionItem) {
            st.machine.interrupt(now, 0);
            Net.sendAround(player, new CombatPayloads.ActionEnd(
                    player.getId(), CombatPayloads.EndReason.CANCELLED.ordinal(), current.seq), true);
        }
        Optional<PlayerCombatMachine.Start> buffered = st.machine.tick(now, context(player));
        buffered.ifPresent(s -> begin(player, st, s.action()));
        current = st.machine.current();
        if (current != null && current.phase() == ActionPhase.ACTIVE && current.def.hit().isPresent()) {
            sweep(player, st, current);
        }
        ResonanceService.tick(player, now - st.lastCombatTick);
        VitalsModule.tickPlayer(player);
    }

    /** Starts a server-originated action (a counter, a reaction) and tells clients. */
    public static void adoptServerAction(ServerPlayer player, RunningAction action) {
        PlayerCombatState st = state(player);
        st.machine.adopt(action);
        st.actionItem = player.getMainHandItem().getItem();
        Net.sendAround(player, new CombatPayloads.ActionStart(player.getId(), action.ref.toString(), -1, Float.NaN, false), true);
    }

    /** Stamina ran out while blocking: the guard breaks and the player staggers. */
    static void breakGuard(ServerPlayer player, RunningAction guard) {
        PlayerCombatState st = state(player);
        long now = player.level().getGameTime();
        st.machine.interrupt(now, PlayerDefense.GUARD_BREAK_STUN);
        Net.sendAround(player, new CombatPayloads.ActionEnd(player.getId(), CombatPayloads.EndReason.INTERRUPTED.ordinal(), guard.seq), true);
        startReaction(player, st, "guard_break", now);
    }

    private static void startReaction(ServerPlayer player, PlayerCombatState st, String name, long now) {
        CombatData.handle(false, CombatData.PLAYER_MOVESET).flatMap(h -> h.moveSet().action(name).map(def ->
                new RunningAction(new ActionRef(h.id(), name), def, -1, Float.NaN, now, RunningAction.Kind.OTHER)))
                .ifPresent(reaction -> {
                    st.machine.adopt(reaction);
                    Net.sendAround(player, new CombatPayloads.ActionStart(player.getId(), reaction.ref.toString(), -1, Float.NaN, false), true);
                });
    }

    private static void sweep(ServerPlayer player, PlayerCombatState st, RunningAction action) {
        Optional<WeaponProfile> weapon = weapon(player);
        if (weapon.isEmpty()) {
            return;
        }
        boolean resonant = ResonanceRules.resonant(ResonanceService.get(player));
        float poiseScale = weapon.get().poiseDamage() * (resonant ? 1f + ResonanceRules.RESONANT_POISE_BONUS : 1f);
        for (Entity e : HitResolver.sweep(player, action, player.getYRot(), PlayerCombat::canHit)) {
            if (e instanceof LivingEntity living) {
                CombatDamage.Outcome out = CombatDamage.apply(player, living, action, weapon.get().attackRating(), poiseScale,
                        action.damageBonus, CombatDamageTypes.WEAPON);
                if (out.landed()) {
                    ResonanceService.add(player, ResonanceRules.hitGain(weapon.get().weight())
                            + (action.kind == RunningAction.Kind.RIPOSTE ? ResonanceRules.RIPOSTE : 0f));
                }
            } else if (e.isAttackable()) {
                player.attack(e); // boats, minecarts, item frames, end crystals keep vanilla behaviour
            }
            st.lastCombatTick = player.level().getGameTime();
        }
    }

    private static boolean canHit(Entity e) {
        if (e instanceof ArmorStand) {
            return true;
        }
        if (e instanceof LivingEntity) {
            return true;
        }
        return e.isAttackable() && !e.isInvulnerable();
    }

    /**
     * Dodge invincibility. Returns true if this damage must be cancelled. Only attacks with an
     * attacker, projectiles and explosions can be dodged; lava or drowning cannot.
     */
    public static boolean dodges(ServerPlayer player, DamageSource source) {
        RunningAction current = state(player).machine.current();
        if (current == null || (current.def.dodge().isEmpty() && !current.def.defense().invulnerable())) {
            return false;
        }
        boolean dodgeable = source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
        int tolerance = ServerConfig.IFRAME_LATENCY_TOLERANCE.get();
        if (!dodgeable || !current.invulnerable(tolerance)) {
            return false;
        }
        if (current.def.defense().invulnerable()) {
            return dodgeable;
        }
        if (!current.perfectDodged && current.perfectDodgeWindow(tolerance)) {
            current.perfectDodged = true;
            ResonanceService.add(player, ResonanceRules.PERFECT_DODGE);
            Net.send(player, new CombatPayloads.Event(CombatPayloads.EventKind.PERFECT_DODGE.ordinal(), player.getId()));
        }
        return true;
    }

    /**
     * A VESTIGE attack landed on the player: interrupts the current action unless it has
     * hyperarmor during its windup (GDD §7.8), then plays a flinch or stagger.
     */
    public static void onHitByAction(ServerPlayer player, RunningAction attack) {
        PlayerCombatState st = state(player);
        long now = player.level().getGameTime();
        st.lastCombatTick = now;
        ResonanceService.add(player, ResonanceRules.HIT_TAKEN);
        RunningAction current = st.machine.current();
        boolean armored = current != null && current.def.hyperarmor() && current.phase() == ActionPhase.WINDUP;
        if (armored && attack.def.inflictStagger() == 0) {
            return;
        }
        int stun = Math.max(DEFAULT_HIT_STUN, attack.def.inflictStagger());
        st.machine.interrupt(now, stun);
        if (current != null) {
            Net.sendAround(player, new CombatPayloads.ActionEnd(
                    player.getId(), CombatPayloads.EndReason.INTERRUPTED.ordinal(), current.seq), true);
        }
        startReaction(player, st, attack.def.inflictStagger() > DEFAULT_HIT_STUN ? "stagger" : "flinch", now);
    }

    /** For tests: the item a stack must be to count as armed. */
    public static boolean armed(Player player, ItemStack stack) {
        return WeaponProfiles.of(stack, player.level().isClientSide()).isPresent();
    }
}
