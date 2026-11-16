package dev.vestige.player;

/** Wick Flask numbers (GDD §8.4). Pure, unit-tested. */
public final class FlaskRules {
    public static final int BASE_CHARGES = 3;
    public static final int MAX_CHARGES = 10;
    public static final int MAX_KINDLING = 5;
    /** Ticks over which a sip heals (the drink action's active frames). */
    public static final int HEAL_TICKS = 20;
    public static final float BASE_HEAL = 0.40f;
    public static final float KINDLED_HEAL = 0.60f;

    private FlaskRules() {}

    /** Fraction of max health one sip restores: 40%, rising to 60% at Kindling +5. */
    public static float healFraction(int kindling) {
        int k = Math.max(0, Math.min(MAX_KINDLING, kindling));
        return BASE_HEAL + (KINDLED_HEAL - BASE_HEAL) * k / MAX_KINDLING;
    }

    /** Health restored on each of the {@link #HEAL_TICKS} healing ticks. */
    public static float healPerTick(float maxHealth, int kindling) {
        return maxHealth * healFraction(kindling) / HEAL_TICKS;
    }

    /** A Flask Wick raises the maximum by one, up to {@link #MAX_CHARGES}. */
    public static int afterWick(int max) {
        return Math.min(MAX_CHARGES, max + 1);
    }
}
