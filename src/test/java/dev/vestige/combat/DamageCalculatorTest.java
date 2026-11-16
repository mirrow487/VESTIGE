package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.combat.damage.DamageCalculator;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {
    @Test
    void zeroDefenseIsRaw() {
        assertEquals(60.0, DamageCalculator.afterDefense(60, 0), 1e-9);
    }

    @Test
    void defenseReducesSmoothly() {
        // 60² / (60 + 20) = 45
        assertEquals(45.0, DamageCalculator.afterDefense(60, 10), 1e-9);
    }

    @Test
    void largeHitsPierceDefenseMoreThanSmallOnes() {
        double small = DamageCalculator.afterDefense(20, 20) / 20;
        double large = DamageCalculator.afterDefense(200, 20) / 200;
        assertTrue(large > small);
    }

    @Test
    void floorIsTenPercent() {
        assertEquals(1.0, DamageCalculator.afterDefense(10, 10_000), 1e-9);
    }

    @Test
    void rawCombinesRatingMotionAndBonus() {
        assertEquals(60 * 1.45 * 1.1, DamageCalculator.raw(60, 1.45, 0.1), 1e-9);
    }
}
