package dev.vestige.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.equipment.StatCalculator;
import dev.vestige.equipment.StatMods;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatsRulesTest {
    private static Map<Attribute, Integer> attrs(Object... kv) {
        EnumMap<Attribute, Integer> m = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            m.put(a, AttributeRules.BASE);
        }
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Attribute) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void levelAndCostCurve() {
        assertEquals(1, AttributeRules.level(attrs()));
        assertEquals(11, AttributeRules.level(attrs(Attribute.VITALITY, 15, Attribute.STRENGTH, 15)));
        long previous = 0;
        for (int l = 1; l < 100; l++) {
            long c = AttributeRules.levelCost(l);
            assertTrue(c > previous, "cost must rise every level");
            previous = c;
        }
        assertTrue(AttributeRules.levelCost(1) < 200, "the first levels are cheap: " + AttributeRules.levelCost(1));
    }

    @Test
    void softCaps() {
        assertEquals(0f, AttributeRules.curve(10), 1e-6);
        assertEquals(0.35f, AttributeRules.curve(20), 1e-6);
        assertEquals(0.80f, AttributeRules.curve(40), 1e-6);
        assertEquals(1.00f, AttributeRules.curve(60), 1e-6);
        // Each point is worth less past each soft cap.
        assertTrue(AttributeRules.curve(15) - AttributeRules.curve(14) > AttributeRules.curve(45) - AttributeRules.curve(44));
        assertEquals(40f, AttributeRules.vitalityHealth(20), 1e-6);
        assertEquals(130f, AttributeRules.vitalityHealth(60), 1e-6);
        assertEquals(70f, AttributeRules.enduranceStamina(60), 1e-6);
    }

    @Test
    void weaponScalingAndRequirements() {
        DamageSpread base = DamageSpread.of(Map.of(DamageType.STRIKE, 72f));
        Map<Attribute, Grade> scaling = Map.of(Attribute.STRENGTH, Grade.B);
        Map<Attribute, Integer> req = Map.of(Attribute.STRENGTH, 14);
        // Below the requirement: x0.6.
        assertEquals(72f * 0.6f, AttributeRules.attackRating(base, scaling, req, attrs()).get(DamageType.STRIKE), 1e-3);
        // STR 20 (curve 0.35, grade B 0.8): 72 x 1.28.
        assertEquals(72f * 1.28f, AttributeRules.attackRating(base, scaling, req, attrs(Attribute.STRENGTH, 20)).get(DamageType.STRIKE), 1e-3);
        // Elemental damage ignores Strength: Cinder scales with Lamplight only.
        DamageSpread fire = DamageSpread.of(Map.of(DamageType.CINDER, 30f));
        assertEquals(30f, AttributeRules.attackRating(fire, Map.of(Attribute.STRENGTH, Grade.S), Map.of(), attrs(Attribute.STRENGTH, 60))
                .get(DamageType.CINDER), 1e-3);
    }

    @Test
    void burdenClasses() {
        assertEquals(Burden.LIGHT, Burden.of(18, 60, 0));
        assertEquals(Burden.MEDIUM, Burden.of(19, 60, 0));
        assertEquals(Burden.LIGHT, Burden.of(20, 60, 0.15f), "Lightfoot Feather raises the thresholds");
        assertEquals(Burden.HEAVY, Burden.of(60, 60, 0));
        assertEquals(Burden.OVERBURDENED, Burden.of(61, 60, 0));
    }

    @Test
    void absorptionStacksMultiplicatively() {
        DamageSpread a = DamageSpread.single(DamageType.SLASH, 0.5f);
        assertEquals(0.75f, a.stackAbsorption(a).get(DamageType.SLASH), 1e-6);
    }

    @Test
    void calculatorCombinesAttributesGearAndPerks() {
        StatMods ring = new StatMods(0, 0, 0.15f, 10, 0, 0, DamageSpread.EMPTY, DamageSpread.EMPTY, 0, 0, 0, 0, 0, 0, 0, Optional.of("x"));
        StatMods chest = new StatMods(0, 0, 0, 14, 0, 0, DamageSpread.single(DamageType.SLASH, 0.17f), DamageSpread.EMPTY, 0, 0, 0, 0, 0,
                0, 0, Optional.empty());
        StatSheet s = StatCalculator.compute(attrs(Attribute.ENDURANCE, 20, Attribute.VITALITY, 20), List.of(ring, chest), 30f, Map.of());
        assertEquals(140f, s.maxHealth(), 1e-3);
        assertEquals(120f, s.maxStamina, 1e-3);
        assertEquals(1.15f, s.staminaRegen, 1e-3);
        assertEquals(24f + 4f, s.poise, 1e-3);
        assertEquals(72f, s.equipCapacity, 1e-3);
        assertEquals(Burden.MEDIUM, s.burden);
        assertEquals(0.17f, s.absorption.get(DamageType.SLASH), 1e-6);
        assertTrue(s.has("x"));
    }
}
