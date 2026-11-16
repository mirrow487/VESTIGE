package dev.vestige.stats;

/** Equip load classes (GDD §7.6): the roll a player gets. */
public enum Burden {
    LIGHT("roll_light"), MEDIUM("roll"), HEAVY("roll_heavy"), OVERBURDENED("roll_overburdened");

    /** Player-moveset action used to roll at this burden. */
    public final String rollAction;

    Burden(String rollAction) {
        this.rollAction = rollAction;
    }

    /** Ratio thresholds: light ≤ 30%, medium ≤ 70%, heavy ≤ 100% (raised by the Lightfoot Feather). */
    public static Burden of(float load, float capacity, float thresholdBonus) {
        float r = capacity <= 0 ? 2f : load / capacity;
        float k = 1f + thresholdBonus;
        if (r <= 0.30f * k) {
            return LIGHT;
        }
        if (r <= 0.70f * k) {
            return MEDIUM;
        }
        return r <= 1.00f ? HEAVY : OVERBURDENED;
    }
}
