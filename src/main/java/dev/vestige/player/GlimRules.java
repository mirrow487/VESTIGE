package dev.vestige.player;

/** How much Glim things are worth (GDD §23.1). Pure, unit-tested. */
public final class GlimRules {
    private GlimRules() {}

    /**
     * A vanilla hostile mob's Glim: a twentieth of its (VESTIGE-scaled) max health, at least 1, so a
     * zombie (100 HP) is worth 5 and a VESTIGE enemy's profile value dominates.
     */
    public static long vanillaMob(float maxHealth) {
        return Math.max(1, Math.round(maxHealth / 20f));
    }

    /** VESTIGE enemies: the profile's value, or a third of the profile's health if it gives none. */
    public static long profile(int glim, float health) {
        return glim > 0 ? glim : Math.max(1, Math.round(health / 3f));
    }
}
