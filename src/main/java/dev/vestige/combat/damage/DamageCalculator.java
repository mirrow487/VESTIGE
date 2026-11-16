package dev.vestige.combat.damage;

/** GDD §7.10 damage formula. Pure; unit-tested. */
public final class DamageCalculator {
    /** Damage never drops below this fraction of the raw value. */
    public static final double FLOOR = 0.10;

    private DamageCalculator() {}

    /** {@code raw² / (raw + 2·defense)}, floored at 10% of raw. Large hits pierce defense. */
    public static double afterDefense(double raw, double defense) {
        if (raw <= 0.0) {
            return 0.0;
        }
        double d = Math.max(0.0, defense);
        double value = raw * raw / (raw + 2.0 * d);
        return Math.max(value, raw * FLOOR);
    }

    /** Raw damage from an attack rating, motion value and percentage bonus. */
    public static double raw(double attackRating, double motionValue, double bonus) {
        return attackRating * motionValue * (1.0 + bonus);
    }

    /** Defence of entities without VESTIGE stats, from vanilla armor ("Common baseline", GDD §9.1). */
    public static double vanillaDefense(double armor, double toughness) {
        return armor * 4.0 + toughness * 2.0;
    }
}
