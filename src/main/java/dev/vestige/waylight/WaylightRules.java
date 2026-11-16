package dev.vestige.waylight;

/** When resting and travelling are allowed (GDD §6.6). Pure, unit-tested. */
public final class WaylightRules {
    /** No fast travel within this many blocks of a hostile mob. */
    public static final double TRAVEL_HOSTILE_RADIUS = 24;
    /** No resting with a hostile this close. */
    public static final double REST_HOSTILE_RADIUS = 12;
    /** How far from a Waylight its menu can be used. */
    public static final double USE_DISTANCE = 6;

    public enum Refusal { NONE, IN_COMBAT, HOSTILES_NEAR, TOO_FAR, NOT_LIT, UNKNOWN_DESTINATION, SAME_PLACE }

    private WaylightRules() {}

    public static Refusal rest(boolean inCombat, double nearestHostile, double distance) {
        if (distance > USE_DISTANCE) {
            return Refusal.TOO_FAR;
        }
        if (inCombat) {
            return Refusal.IN_COMBAT;
        }
        return nearestHostile <= REST_HOSTILE_RADIUS ? Refusal.HOSTILES_NEAR : Refusal.NONE;
    }

    public static Refusal travel(boolean inCombat, double nearestHostile, double distance, boolean sourceLit, boolean targetKnown,
                                 boolean samePlace) {
        if (distance > USE_DISTANCE) {
            return Refusal.TOO_FAR;
        }
        if (!sourceLit) {
            return Refusal.NOT_LIT;
        }
        if (!targetKnown) {
            return Refusal.UNKNOWN_DESTINATION;
        }
        if (samePlace) {
            return Refusal.SAME_PLACE;
        }
        if (inCombat) {
            return Refusal.IN_COMBAT;
        }
        return nearestHostile <= TRAVEL_HOSTILE_RADIUS ? Refusal.HOSTILES_NEAR : Refusal.NONE;
    }
}
