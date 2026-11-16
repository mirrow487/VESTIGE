package dev.vestige.vitals;

/** Pure stamina rules (GDD §8.2), unit-tested. */
public final class StaminaRules {
    /** Food level below which stamina regenerates at half speed. */
    public static final int LOW_FOOD = 6;

    private StaminaRules() {}

    /**
     * Stamina regenerated this tick.
     *
     * @param ticksSinceSpend ticks since stamina was last spent
     * @param delayTicks      configured regeneration delay
     * @param perSecond       configured regeneration per second
     * @param busy            true while an action (attack, roll) is in progress
     * @param foodLevel       vanilla food level (0–20)
     */
    public static float regenThisTick(long ticksSinceSpend, int delayTicks, double perSecond, boolean busy, int foodLevel) {
        if (busy || ticksSinceSpend < delayTicks) {
            return 0f;
        }
        double perTick = perSecond / 20.0;
        if (foodLevel < LOW_FOOD) {
            perTick *= 0.5;
        }
        return (float) perTick;
    }

    /** An action may start whenever stamina is above zero; it may then drain stamina to zero (GDD §7.4). */
    public static boolean canStart(float current) {
        return current > 0f;
    }

    public static float afterSpend(float current, float cost) {
        return Math.max(0f, current - cost);
    }
}
