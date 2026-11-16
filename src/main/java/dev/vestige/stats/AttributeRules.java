package dev.vestige.stats;

import java.util.Map;

/** Attribute numbers (GDD §8.6). Pure, unit-tested. */
public final class AttributeRules {
    public static final int BASE = 10;
    public static final int MAX = 60;
    public static final float BASE_HEALTH = 100f;
    public static final float BASE_STAMINA = 100f;
    public static final float BASE_CAPACITY = 60f;
    /** Under-requirement penalty on attack rating. */
    public static final float REQUIREMENT_PENALTY = 0.6f;

    private AttributeRules() {}

    /** Character level: 1 with every attribute at its base. */
    public static int level(Map<Attribute, Integer> attrs) {
        int sum = 0;
        for (Attribute a : Attribute.values()) {
            sum += attrs.getOrDefault(a, BASE) - BASE;
        }
        return 1 + sum;
    }

    /** Glim to go from {@code level} to {@code level + 1}: gentle early, steep later (a Souls-like curve). */
    public static long levelCost(int level) {
        int l = Math.max(1, level);
        return Math.round(120 + 18.0 * l + 0.9 * l * l + 0.004 * l * l * l);
    }

    /**
     * Normalised soft-capped curve for scaling: 0 at 10, 0.35 at 20, 0.80 at 40, 1.0 at 60.
     * Early points matter most; past the soft caps they taper.
     */
    public static float curve(int value) {
        int v = Math.max(BASE, Math.min(MAX, value));
        if (v <= 20) {
            return 0.35f * (v - 10) / 10f;
        }
        if (v <= 40) {
            return 0.35f + 0.45f * (v - 20) / 20f;
        }
        return 0.80f + 0.20f * (v - 40) / 20f;
    }

    /** Bonus max health (VESTIGE units): +4/pt to 20, +3/pt to 40, +1.5/pt to 60. */
    public static float vitalityHealth(int vit) {
        int v = Math.max(BASE, Math.min(MAX, vit));
        float h = 4f * (Math.min(v, 20) - 10);
        if (v > 20) {
            h += 3f * (Math.min(v, 40) - 20);
        }
        if (v > 40) {
            h += 1.5f * (v - 40);
        }
        return h;
    }

    /** Bonus max stamina: +2/pt to 30, +1/pt to 60. */
    public static float enduranceStamina(int end) {
        int v = Math.max(BASE, Math.min(MAX, end));
        return 2f * (Math.min(v, 30) - 10) + (v > 30 ? (v - 30) : 0);
    }

    /** Bonus equip capacity: +1.2 per point. */
    public static float enduranceCapacity(int end) {
        return 1.2f * (Math.max(BASE, Math.min(MAX, end)) - BASE);
    }

    /** Bonus poise: +0.4 per point. */
    public static float endurancePoise(int end) {
        return 0.4f * (Math.max(BASE, Math.min(MAX, end)) - BASE);
    }

    /**
     * Flat defence against every type from the character's level: 0 at level 1 (Milestone 3-4 balance
     * is unchanged for a fresh character), +0.5 per level after.
     */
    public static float levelDefense(int level) {
        return 0.5f * Math.max(0, level - 1);
    }

    /** Wick Flask heal bonus from Lamplight: up to +15% of max HP at 60. */
    public static float lamplightFlaskBonus(int lamplight) {
        return 0.15f * curve(lamplight);
    }

    /** Status resistance from Lamplight (buildup taken ×(1 - this)): up to 40%. */
    public static float lamplightStatusResist(int lamplight) {
        return 0.40f * curve(lamplight);
    }

    /** Resonance gain multiplier from Cadence: up to +50%. */
    public static float cadenceResonanceGain(int cadence) {
        return 1f + 0.5f * curve(cadence);
    }

    /** Weapon art damage bonus from Cadence: up to +40%. */
    public static float cadenceArtBonus(int cadence) {
        return 0.4f * curve(cadence);
    }

    /** Which attributes scale each damage type of a weapon. */
    public static Attribute[] scalersOf(DamageType type) {
        return switch (type) {
            case SLASH, STRIKE, PIERCE -> new Attribute[] {Attribute.STRENGTH, Attribute.DEXTERITY};
            case RESONANCE, HUSH, VOID -> new Attribute[] {Attribute.CADENCE};
            case CINDER, RADIANCE -> new Attribute[] {Attribute.LAMPLIGHT};
        };
    }

    /**
     * A weapon's attack rating per type for these attributes: base × (1 + Σ grade × curve(attr)) over
     * the attributes that scale that type, ×0.6 everywhere if any requirement is unmet.
     */
    public static DamageSpread attackRating(DamageSpread base, Map<Attribute, Grade> scaling, Map<Attribute, Integer> requirements,
                                            Map<Attribute, Integer> attrs) {
        boolean met = requirementsMet(requirements, attrs);
        java.util.EnumMap<DamageType, Float> out = new java.util.EnumMap<>(DamageType.class);
        base.forEach((type, value) -> {
            float bonus = 0f;
            for (Attribute a : scalersOf(type)) {
                Grade g = scaling.get(a);
                if (g != null) {
                    bonus += g.coefficient * curve(attrs.getOrDefault(a, BASE));
                }
            }
            out.put(type, value * (1f + bonus) * (met ? 1f : REQUIREMENT_PENALTY));
        });
        return DamageSpread.of(out);
    }

    public static boolean requirementsMet(Map<Attribute, Integer> requirements, Map<Attribute, Integer> attrs) {
        for (var e : requirements.entrySet()) {
            if (attrs.getOrDefault(e.getKey(), BASE) < e.getValue()) {
                return false;
            }
        }
        return true;
    }
}
