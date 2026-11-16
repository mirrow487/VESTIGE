package dev.vestige.combat.resonance;

/**
 * Resonance (GDD §8.3): the rhythm of precise play. Earned by landing hits and, above all, by
 * perfect dodges, parries and ripostes; broken by taking hits; spent on weapon arts.
 * Pure rules, unit-tested.
 */
public final class ResonanceRules {
    public static final float MAX = 100f;
    /** At or above this, attacks deal extra poise damage. */
    public static final float RESONANT = 50f;
    /** At full Resonance, the next weapon art is empowered. */
    public static final float CRESCENDO = 100f;

    public static final float PERFECT_DODGE = 15f;
    public static final float PARRY = 20f;
    public static final float RIPOSTE = 15f;
    public static final float BLOCK = 2f;
    /** Taking an unblocked hit breaks the rhythm. */
    public static final float HIT_TAKEN = -10f;

    public static final float RESONANT_POISE_BONUS = 0.20f;
    public static final float CRESCENDO_ART_BONUS = 0.30f;

    /** Ticks without combat before Resonance starts to fade, and the fade rate per second. */
    public static final int DECAY_DELAY = 120;
    public static final float DECAY_PER_SECOND = 4f;

    private ResonanceRules() {}

    /** Gain for landing a weapon hit: heavier weapons ring louder (3 to 8). */
    public static float hitGain(float weaponWeight) {
        return Math.max(3f, Math.min(8f, 3f + weaponWeight * 0.7f));
    }

    public static float clamp(float v) {
        return Math.max(0f, Math.min(MAX, v));
    }

    public static float decayThisTick(long ticksSinceCombat) {
        return ticksSinceCombat >= DECAY_DELAY ? DECAY_PER_SECOND / 20f : 0f;
    }

    public static boolean resonant(float v) {
        return v >= RESONANT;
    }

    public static boolean crescendo(float v) {
        return v >= CRESCENDO;
    }
}
