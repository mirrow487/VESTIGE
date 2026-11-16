package dev.vestige.combat.machine;

import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.action.MoveSet;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Decides when a player's attacks, combos, charges and dodges start (GDD §7.4, §7.6).
 *
 * <p>Runs on the server (authoritative) and on the owning client (prediction) with identical
 * rules, so the client can animate immediately and almost never needs correcting. It has no
 * world access: callers pass a {@link Context} each call and react to returned {@link Start}s.
 */
public final class PlayerCombatMachine {
    /** How long an early press is remembered (ticks). */
    public static final int BUFFER_TICKS = 10;
    /** A light attack this soon after a dodge becomes the rolling attack. */
    public static final int ROLLING_ATTACK_TICKS = 6;
    /** Idle time after which the light combo restarts from its first hit. */
    public static final int COMBO_RESET_TICKS = 12;

    /** World-facing information the machine needs. */
    public interface Context {
        /** Moveset of the weapon in hand, if any. */
        Optional<MoveSetHandle> weapon();

        /** Shared player moveset (roll, backstep, flinch). */
        Optional<MoveSetHandle> common();

        boolean canSpendStamina();

        boolean sprinting();

        /** Common-moveset action for the guard button: a parry (buckler) or a guard (shield or weapon). */
        default Optional<String> guardAction() {
            return Optional.empty();
        }

        default boolean canSpendResonance(float cost) {
            return false;
        }

        /** True when a light attack should become a riposte or backstab (a valid target is in place). */
        default boolean riposteAvailable() {
            return false;
        }

        /** True when the player can pay for an ammo-consuming projectile (an arrow, or creative). */
        default boolean hasAmmo() {
            return true;
        }

        /** The roll for the player's burden (GDD §7.6): roll_light, roll, roll_heavy, roll_overburdened, or a ring's blink. */
        default String rollAction() {
            return "roll";
        }

        /** True when the Wick Flask has a charge left (otherwise the drink is an empty shake). */
        default boolean hasFlaskCharge() {
            return false;
        }
    }

    public record MoveSetHandle(ResourceLocation id, MoveSet moveSet) {
        Optional<Start> start(String name, int seq, float dirYaw, long now, RunningAction.Kind kind) {
            return moveSet.action(name).map(def -> new Start(new RunningAction(new ActionRef(id, name), def, seq, dirYaw, now, kind)));
        }
    }

    /** An action that just started; the caller spends stamina and broadcasts it. */
    public record Start(RunningAction action) {}

    private RunningAction current;
    private int comboIndex = -1;
    private long lastEndTick = Long.MIN_VALUE / 2;
    private RunningAction.Kind lastKind = RunningAction.Kind.OTHER;
    private long stunnedUntil = Long.MIN_VALUE / 2;

    private CombatInput buffered;
    private int bufferedSeq;
    private float bufferedDir = Float.NaN;
    private long bufferedAt;
    private boolean bufferedHeavyReleased;
    private boolean bufferedGuardReleased;

    public RunningAction current() {
        return current;
    }

    public boolean busy() {
        return current != null;
    }

    public boolean stunned(long now) {
        return now < stunnedUntil;
    }

    /**
     * Handles a press. Returns the action that started, if any. If the press is too early it is
     * buffered and may start from a later {@link #tick}.
     */
    public Optional<Start> input(CombatInput input, int seq, float dirYaw, long now, Context ctx) {
        if (input == CombatInput.HEAVY_RELEASE) {
            if (current != null && current.kind == RunningAction.Kind.HEAVY && current.holding) {
                current.release();
            } else if (buffered == CombatInput.HEAVY_PRESS) {
                bufferedHeavyReleased = true;
            }
            return Optional.empty();
        }
        if (input == CombatInput.GUARD_RELEASE) {
            if (current != null && current.kind == RunningAction.Kind.GUARD && current.holding) {
                current.release();
            } else if (buffered == CombatInput.GUARD_PRESS) {
                bufferedGuardReleased = true;
            }
            return Optional.empty();
        }
        if (stunned(now)) {
            return Optional.empty();
        }
        Optional<Start> started = tryStart(input, seq, dirYaw, now, ctx, false);
        if (started.isEmpty() && current != null) {
            buffered = input;
            bufferedSeq = seq;
            bufferedDir = dirYaw;
            bufferedAt = now;
            bufferedHeavyReleased = false;
            bufferedGuardReleased = false;
        }
        return started;
    }

    /** True if an input would be accepted right now (used to reject without buffering). */
    private boolean windowOpen(CombatInput input) {
        if (current == null) {
            return true;
        }
        int e = current.elapsed();
        // A successful parry is cancellable the moment its impact freeze ends: riposte, art or dodge.
        if (current.parrySucceeded && current.hitstop() == 0 && input != CombatInput.GUARD_PRESS) {
            return true;
        }
        // A raised guard can be dropped into anything at once (attack, dodge, art).
        if (current.kind == RunningAction.Kind.GUARD) {
            return input != CombatInput.GUARD_PRESS;
        }
        if (input == CombatInput.ROLL || input == CombatInput.GUARD_PRESS || input == CombatInput.FLASK) {
            return current.kind == RunningAction.Kind.DODGE ? e >= current.def.comboOpensAt() : e >= current.def.cancelOpensAt();
        }
        return e >= current.def.comboOpensAt();
    }

    private Optional<Start> tryStart(CombatInput input, int seq, float dirYaw, long now, Context ctx, boolean fromBuffer) {
        // The flask needs no stamina (GDD §8.4); everything else needs some left.
        if (!windowOpen(input) || (input != CombatInput.FLASK && !ctx.canSpendStamina())) {
            return Optional.empty();
        }
        int comboBefore = comboIndex;
        Optional<Start> start = switch (input) {
            case ROLL -> ctx.common().flatMap(c -> c.start(Float.isNaN(dirYaw) ? "backstep" : ctx.rollAction(), seq, dirYaw, now,
                    RunningAction.Kind.DODGE));
            case LIGHT -> ctx.weapon().flatMap(w -> startLight(w, seq, now, ctx));
            case HEAVY_PRESS -> ctx.weapon().flatMap(w -> w.moveSet().heavy()
                    .flatMap(name -> w.start(name, seq, Float.NaN, now, RunningAction.Kind.HEAVY)));
            case GUARD_PRESS -> ctx.guardAction().flatMap(name -> ctx.common().flatMap(c -> c.moveSet().action(name).flatMap(def ->
                    c.start(name, seq, Float.NaN, now, def.defense().guard() ? RunningAction.Kind.GUARD : RunningAction.Kind.PARRY))));
            case ART -> ctx.weapon().flatMap(w -> w.moveSet().art().flatMap(name -> w.moveSet().action(name)
                    .filter(def -> ctx.canSpendResonance(def.art().map(ActionDef.Art::cost).orElse(0f)))
                    .flatMap(def -> w.start(name, seq, def.dodge().isPresent() ? dirYaw : Float.NaN, now, RunningAction.Kind.ART))));
            case FLASK -> ctx.common().flatMap(c -> c.start(ctx.hasFlaskCharge() ? "flask_drink" : "flask_empty", seq, Float.NaN, now,
                    RunningAction.Kind.FLASK));
            default -> Optional.empty();
        };
        if (start.isPresent() && !ammoOk(start.get().action().def, ctx)) {
            comboIndex = comboBefore;
            return Optional.empty();
        }
        start.ifPresent(s -> {
            if (s.action().kind == RunningAction.Kind.HEAVY) {
                s.action().holding = !(fromBuffer && bufferedHeavyReleased);
            }
            if (s.action().kind == RunningAction.Kind.GUARD) {
                s.action().holding = !(fromBuffer && bufferedGuardReleased);
            }
            begin(s.action());
        });
        return start;
    }

    private static boolean ammoOk(ActionDef def, Context ctx) {
        return def.effects().projectile().map(p -> !p.ammo() || ctx.hasAmmo()).orElse(true);
    }

    private Optional<Start> startLight(MoveSetHandle w, int seq, long now, Context ctx) {
        MoveSet set = w.moveSet();
        if (set.riposte().isPresent() && ctx.riposteAvailable()) {
            return w.start(set.riposte().get(), seq, Float.NaN, now, RunningAction.Kind.RIPOSTE);
        }
        boolean afterDodge = (current != null && current.kind == RunningAction.Kind.DODGE)
                || (current == null && lastKind == RunningAction.Kind.DODGE && now - lastEndTick <= ROLLING_ATTACK_TICKS);
        if (afterDodge && set.rolling().isPresent()) {
            return w.start(set.rolling().get(), seq, Float.NaN, now, RunningAction.Kind.ROLLING);
        }
        if (current == null && ctx.sprinting() && set.running().isPresent()) {
            return w.start(set.running().get(), seq, Float.NaN, now, RunningAction.Kind.RUNNING);
        }
        if (set.light().isEmpty()) {
            return Optional.empty();
        }
        boolean chaining = current != null && current.kind == RunningAction.Kind.LIGHT;
        boolean recent = current == null && lastKind == RunningAction.Kind.LIGHT && now - lastEndTick <= COMBO_RESET_TICKS;
        int next = (chaining || recent) ? (comboIndex + 1) % set.light().size() : 0;
        Optional<Start> s = w.start(set.light().get(next), seq, Float.NaN, now, RunningAction.Kind.LIGHT);
        s.ifPresent(x -> comboIndex = next);
        return s;
    }

    private void begin(RunningAction action) {
        current = action;
        buffered = null;
    }

    /**
     * Advances one tick. Returns an action started from the input buffer, if any.
     */
    public Optional<Start> tick(long now, Context ctx) {
        if (current != null && current.tick()) {
            lastEndTick = now;
            lastKind = current.kind;
            current = null;
        }
        if (buffered != null) {
            if (now - bufferedAt > BUFFER_TICKS || stunned(now)) {
                buffered = null;
            } else {
                CombatInput input = buffered;
                Optional<Start> s = tryStart(input, bufferedSeq, bufferedDir, now, ctx, true);
                if (s.isPresent()) {
                    buffered = null;
                    return s;
                }
            }
        }
        return Optional.empty();
    }

    /** Cancels the current action (hit, weapon swapped...) and optionally stuns for some ticks. */
    public RunningAction interrupt(long now, int stunTicks) {
        RunningAction was = current;
        if (current != null) {
            lastEndTick = now;
            lastKind = RunningAction.Kind.OTHER;
        }
        current = null;
        buffered = null;
        comboIndex = -1;
        stunnedUntil = Math.max(stunnedUntil, now + stunTicks);
        return was;
    }

    /** Client-side: align with an authoritative start the client did not predict. */
    public void adopt(RunningAction action) {
        current = action;
        buffered = null;
    }

    public boolean hasBuffered() {
        return buffered != null;
    }
}
