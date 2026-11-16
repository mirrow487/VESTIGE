package dev.vestige.combat.machine;

/** Player combat inputs (GDD §7.2). */
public enum CombatInput {
    LIGHT, HEAVY_PRESS, HEAVY_RELEASE, ROLL, GUARD_PRESS, GUARD_RELEASE, ART;

    private static final CombatInput[] VALUES = values();

    public static CombatInput byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : LIGHT;
    }
}
