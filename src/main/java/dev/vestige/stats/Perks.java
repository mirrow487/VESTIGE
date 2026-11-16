package dev.vestige.stats;

/**
 * Ids of rule-changing effects carried by equipment (rings, talismans, relics, set bonuses). The
 * owning system checks {@code Stats.of(player).has(id)}; the equipment module only switches them on.
 */
public final class Perks {
    // Rings (GDD §12.1)
    public static final String WAXEN_RING = "waxen_ring";             // successful parry heals 5% HP
    public static final String HOLLOW_RING = "hollow_ring";           // roll becomes a blink-step
    public static final String UNSUNG_RING = "unsung_ring";           // Resonance never decays
    public static final String HARTS_RING = "harts_ring";             // +12% damage above 80% HP
    public static final String EMBER_SIGNET = "ember_signet";         // Scorch buildup on every hit
    public static final String TITHE_RING = "tithe_ring";             // kills grant Resonance
    // Talismans (GDD §12.2)
    public static final String GROVE = "grove";                       // out-of-combat regeneration ×3
    public static final String LEDGER_OF_DEBTS = "ledger_of_debts";   // +25% damage, 1% Glim lost per hit taken
    public static final String SECOND_BREATH = "second_breath";       // a revive at 20% HP every 10 minutes
    public static final String BELL_OF_WARDING = "bell_of_warding";   // Hushed buildup −60%
    // Relics (GDD §12.3)
    public static final String WINDSAIL_CAPE = "windsail_cape";       // glide while holding jump
    public static final String BELLBRONZE_SPURS = "bellbronze_spurs"; // sprint +15%, sprint stamina −50%
    public static final String DIVERS_BELL = "divers_bell";           // water breathing, faster swimming
    public static final String CLIMBERS_HOOKS = "climbers_hooks";     // cling to walls and climb
    public static final String PALE_HOOK = "pale_hook";               // active: grapple 24 blocks
    // Set bonuses (GDD §11)
    public static final String WANDERER_SURE_FOOT = "wanderer_sure_foot";   // fall damage −30%
    public static final String BELLBRONZE_TOLL = "bellbronze_toll";         // every 4th hit: Resonance shockwave
    public static final String WICKIRON_SLOW_BURN = "wickiron_slow_burn";   // below 30% HP: +20% absorption, +15 poise

    private Perks() {}
}
