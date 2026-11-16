package dev.vestige.equipment;

import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import dev.vestige.stats.Burden;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.StatSheet;
import java.util.List;
import java.util.Map;

/** Attributes + equipment → {@link StatSheet} (GDD §8.6, §9). Pure; unit-tested. */
public final class StatCalculator {
    private StatCalculator() {}

    /**
     * @param attributes attribute levels
     * @param sources    stat modifiers of everything equipped (armor, accessories, active set bonuses)
     * @param equipLoad  total equip weight (weapons, shields, armor, accessories)
     * @param sets       set id → pieces worn (for display)
     */
    public static StatSheet compute(Map<Attribute, Integer> attributes, List<StatMods> sources, float equipLoad, Map<String, Integer> sets) {
        StatSheet s = new StatSheet();
        s.attributes.putAll(attributes);
        int vit = s.attr(Attribute.VITALITY), end = s.attr(Attribute.ENDURANCE);
        int cad = s.attr(Attribute.CADENCE), lmp = s.attr(Attribute.LAMPLIGHT);
        s.level = AttributeRules.level(s.attributes);
        s.healthBonus = AttributeRules.vitalityHealth(vit);
        s.maxStamina = AttributeRules.BASE_STAMINA + AttributeRules.enduranceStamina(end);
        s.equipCapacity = AttributeRules.BASE_CAPACITY + AttributeRules.enduranceCapacity(end);
        s.poise = AttributeRules.endurancePoise(end);
        s.defense = DamageSpread.uniform(AttributeRules.levelDefense(s.level));
        s.resonanceGain = AttributeRules.cadenceResonanceGain(cad);
        s.artBonus = AttributeRules.cadenceArtBonus(cad);
        s.flaskHealBonus = AttributeRules.lamplightFlaskBonus(lmp);
        s.statusResist = AttributeRules.lamplightStatusResist(lmp);
        DamageSpread absorption = DamageSpread.EMPTY;
        DamageSpread typeBonus = DamageSpread.EMPTY;
        float sprintFactor = 1f;
        for (StatMods m : sources) {
            s.healthBonus += m.health();
            s.maxStamina += m.stamina();
            s.staminaRegen += m.staminaRegen();
            s.poise += m.poise();
            s.equipCapacity += m.capacity();
            s.burdenThresholdBonus += m.burdenThreshold();
            absorption = absorption.stackAbsorption(m.absorption());
            typeBonus = typeBonus.plus(m.typeBonus());
            s.damageBonus += m.damageBonus();
            s.resonanceGain += m.resonanceGain();
            s.flaskHealBonus += m.flaskHeal();
            s.statusResist = 1f - (1f - s.statusResist) * (1f - m.statusResist());
            s.glimGain += m.glimGain();
            s.sprintSpeedBonus += m.sprintSpeed();
            if (m.sprintStaminaFactor() != 0f) {
                sprintFactor *= 1f - m.sprintStaminaFactor();
            }
            m.perk().ifPresent(s.perks::add);
        }
        s.absorption = cap(absorption);
        s.typeBonus = typeBonus;
        s.sprintStaminaFactor = Math.max(0f, sprintFactor);
        s.equipLoad = equipLoad;
        s.burden = Burden.of(equipLoad, s.equipCapacity, s.burdenThresholdBonus);
        s.sets.putAll(sets);
        return s;
    }

    /** No type is ever fully absorbed: capped at 90%. */
    private static DamageSpread cap(DamageSpread abs) {
        java.util.EnumMap<dev.vestige.stats.DamageType, Float> m = new java.util.EnumMap<>(dev.vestige.stats.DamageType.class);
        abs.forEach((t, v) -> m.put(t, Math.min(0.9f, v)));
        return DamageSpread.of(m);
    }
}
