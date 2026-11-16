package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.combat.player.PlayerDefense;
import dev.vestige.combat.player.RiposteTargeting;
import dev.vestige.combat.resonance.ResonanceRules;
import org.junit.jupiter.api.Test;

/** Pure rules behind guard, parry, ripostes and Resonance. */
class CombatDepthRulesTest {
    @Test
    void heavierWeaponsBuildMoreResonance() {
        assertEquals(3f, ResonanceRules.hitGain(0f), 1e-6);
        assertTrue(ResonanceRules.hitGain(7f) > ResonanceRules.hitGain(3f));
        assertEquals(8f, ResonanceRules.hitGain(100f), 1e-6, "capped");
    }

    @Test
    void precisionIsWorthMoreThanHitting() {
        assertTrue(ResonanceRules.PARRY > ResonanceRules.PERFECT_DODGE);
        assertTrue(ResonanceRules.PERFECT_DODGE > ResonanceRules.hitGain(100f));
        assertTrue(ResonanceRules.HIT_TAKEN < 0f);
    }

    @Test
    void resonanceThresholdsAndDecay() {
        assertFalse(ResonanceRules.resonant(49.9f));
        assertTrue(ResonanceRules.resonant(50f));
        assertTrue(ResonanceRules.crescendo(100f));
        assertEquals(0f, ResonanceRules.decayThisTick(ResonanceRules.DECAY_DELAY - 1), 1e-6);
        assertEquals(0.2f, ResonanceRules.decayThisTick(ResonanceRules.DECAY_DELAY), 1e-6);
        assertEquals(100f, ResonanceRules.clamp(140f), 1e-6);
        assertEquals(0f, ResonanceRules.clamp(-3f), 1e-6);
    }

    @Test
    void stabilityReducesBlockCost() {
        float weapon = PlayerDefense.blockStaminaCost(22f, PlayerDefense.WEAPON_GUARD_STABILITY);
        float kite = PlayerDefense.blockStaminaCost(22f, 55f);
        assertTrue(kite < weapon);
        assertEquals(3f, PlayerDefense.blockStaminaCost(0f, 50f), 1e-6, "every block costs at least the base");
    }

    @Test
    void angleOffUsesMinecraftYaw() {
        // Facing +Z (yaw 0): a point at +Z is straight ahead, at -Z directly behind.
        assertEquals(0.0, RiposteTargeting.angleOff(0f, 0, 0, 0, 3), 1e-9);
        assertEquals(180.0, RiposteTargeting.angleOff(0f, 0, 0, 0, -3), 1e-9);
        assertEquals(90.0, RiposteTargeting.angleOff(0f, 0, 0, 3, 0), 1e-9);
    }

    @Test
    void frontRiposteNeedsRangeAndAim() {
        assertTrue(RiposteTargeting.frontRiposte(2.0, 20.0));
        assertFalse(RiposteTargeting.frontRiposte(3.5, 0.0));
        assertFalse(RiposteTargeting.frontRiposte(2.0, 80.0));
    }

    @Test
    void facingAwayAngleIsZeroWhenLookingAtPlayer() {
        // Target at origin facing +Z (yaw 0); a player at +Z is in front of it, at -Z behind it.
        assertEquals(0.0, RiposteTargeting.angleOff(0f, 0, 0, 0, 2), 1e-9);
        assertEquals(180.0, RiposteTargeting.angleOff(0f, 0, 0, 0, -2), 1e-9);
    }

    @Test
    void backstabNeedsTargetFacingAway() {
        assertTrue(RiposteTargeting.backstab(1.5, 10.0, 160.0));
        assertFalse(RiposteTargeting.backstab(1.5, 10.0, 60.0), "target is facing the player");
        assertFalse(RiposteTargeting.backstab(3.0, 10.0, 170.0), "too far");
    }
}
