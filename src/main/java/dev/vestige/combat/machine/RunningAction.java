package dev.vestige.combat.machine;

import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.ActionRef;

/**
 * An action in progress. {@link #elapsed} only advances when not in hitstop and not holding a
 * charge, so it is the authoritative position on the action's timeline.
 */
public final class RunningAction {
    public final ActionRef ref;
    public final ActionDef def;
    public final int seq;
    /** Dodge direction relative to the body's yaw (degrees), or NaN for none. */
    public final float dirYaw;
    public final long startTick;
    public final Kind kind;
    /** Entity ids already hit by this action (one hit per target per action). */
    public final java.util.Set<Integer> hitEntities = new java.util.HashSet<>();
    /** Set once a perfect dodge has been rewarded for this dodge. */
    public boolean perfectDodged;
    /** Server: the action's effects (projectile, cloud) have been released. */
    public boolean effectsFired;
    int elapsed;
    int hitstop;
    boolean holding;
    int chargeTicks;

    public enum Kind { LIGHT, HEAVY, RUNNING, ROLLING, DODGE, GUARD, PARRY, ART, RIPOSTE, OTHER }

    public RunningAction(ActionRef ref, ActionDef def, int seq, float dirYaw, long startTick, Kind kind) {
        this.ref = ref;
        this.def = def;
        this.seq = seq;
        this.dirYaw = dirYaw;
        this.startTick = startTick;
        this.kind = kind;
    }

    public int elapsed() {
        return elapsed;
    }

    public ActionPhase phase() {
        return def.phaseAt(elapsed);
    }

    public boolean holding() {
        return holding;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public int hitstop() {
        return hitstop;
    }

    public void addHitstop(int ticks) {
        hitstop = Math.max(hitstop, ticks);
    }

    /** 0..1 how much of the maximum charge was built up. */
    public float chargeRatio() {
        return def.charge().map(c -> Math.min(1f, chargeTicks / (float) c.maxTicks())).orElse(0f);
    }

    /**
     * Advances one tick. Returns true when the action has finished. A held chargeable action
     * pauses at the end of its windup until released or fully charged.
     */
    public boolean tick() {
        if (hitstop > 0) {
            hitstop--;
            return false;
        }
        if (holding && def.charge().isPresent() && elapsed == def.timing().windup()) {
            if (chargeTicks < def.charge().get().maxTicks()) {
                chargeTicks++;
                return false;
            }
            holding = false;
        }
        elapsed++;
        return elapsed >= def.total();
    }

    /** Sets the charging state (client mirror of the server's hold). */
    public void setHolding(boolean holding) {
        this.holding = holding;
    }

    /** Client-side catch-up when adopting a server action already in progress. */
    public void setElapsed(int elapsed) {
        this.elapsed = elapsed;
    }

    public void release() {
        holding = false;
    }

    /** True while invincible (dodge i-frames), with optional backdating at the start. */
    public boolean invulnerable(int toleranceTicks) {
        return def.dodge().map(d -> elapsed >= d.iframeStart() - toleranceTicks && elapsed < d.iframeEnd()).orElse(false);
    }

    /** True while a guard action is raised and held (GDD §7.7). */
    public boolean guarding() {
        return def.defense().guard() && elapsed >= def.timing().windup() && phase() != dev.vestige.combat.action.ActionPhase.RECOVERY
                && phase() != dev.vestige.combat.action.ActionPhase.DONE;
    }

    /** True inside this action's parry window. */
    public boolean inParryWindow(int toleranceTicks) {
        return def.defense().parry().map(w -> elapsed >= w.start() - toleranceTicks && elapsed < w.end()).orElse(false);
    }

    /** Additive damage bonus fixed when the action started (e.g. a Crescendo art). */
    public float damageBonus;

    /** Set once this action has parried something (counters fire once). */
    public boolean parrySucceeded;

    /** True during the first ticks of the i-frame window, which count as a perfect dodge. */
    public boolean perfectDodgeWindow(int toleranceTicks) {
        return def.dodge().map(d -> elapsed >= d.iframeStart() - toleranceTicks && elapsed < d.iframeStart() + 3).orElse(false);
    }
}
