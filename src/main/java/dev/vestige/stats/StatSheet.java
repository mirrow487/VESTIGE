package dev.vestige.stats;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything a player's attributes and equipment add up to (GDD §9.2). Computed by the equipment
 * module ({@code StatCalculator}) on both sides from synced data; read by every system through
 * {@link Stats#of}. Fields are filled once by the calculator and treated as read-only afterwards.
 */
public final class StatSheet {
    public static final StatSheet BASE = new StatSheet();

    public final EnumMap<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
    public int level = 1;
    /** Bonus max health over the 100 base (VESTIGE units). */
    public float healthBonus;
    public float maxStamina = AttributeRules.BASE_STAMINA;
    public float staminaRegen = 1f;
    public float equipLoad;
    public float equipCapacity = AttributeRules.BASE_CAPACITY;
    public float burdenThresholdBonus;
    public Burden burden = Burden.LIGHT;
    public float poise;
    /** Fraction of each damage type absorbed (armor, rings; multiplicative stacking). */
    public DamageSpread absorption = DamageSpread.EMPTY;
    /** Flat defence per type (level, Endurance). */
    public DamageSpread defense = DamageSpread.uniform(AttributeRules.levelDefense(1));
    /** Extra outgoing damage fraction per type (e.g. Ember Signet: Cinder +0.1). */
    public DamageSpread typeBonus = DamageSpread.EMPTY;
    /** Extra outgoing damage fraction for everything (Hart's Ring is conditional and applied in combat). */
    public float damageBonus;
    public float artBonus;
    public float resonanceGain = 1f;
    public float flaskHealBonus;
    public float statusResist;
    public float glimGain = 1f;
    public float sprintSpeedBonus;
    public float sprintStaminaFactor = 1f;
    public final Set<String> perks = new HashSet<>();
    /** Armor set id → pieces worn. */
    public final Map<String, Integer> sets = new HashMap<>();

    public StatSheet() {
        for (Attribute a : Attribute.values()) {
            attributes.put(a, AttributeRules.BASE);
        }
    }

    public int attr(Attribute a) {
        return attributes.getOrDefault(a, AttributeRules.BASE);
    }

    public boolean has(String perk) {
        return perks.contains(perk);
    }

    public float maxHealth() {
        return AttributeRules.BASE_HEALTH + healthBonus;
    }
}
