package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.combat.hit.HitGeometry;
import dev.vestige.combat.hit.HitGeometry.Target;
import org.junit.jupiter.api.Test;

class HitGeometryTest {
    private static Target at(double forward, double right) {
        return new Target(forward, right, 0.3, 0.0, 1.95);
    }

    @Test
    void arcHitsTargetInFront() {
        assertTrue(HitGeometry.arc(at(2.0, 0.0), 2.5, -60, 60, 0, 2));
    }

    @Test
    void arcMissesBehindAndBeyondReach() {
        assertFalse(HitGeometry.arc(at(-2.0, 0.0), 2.5, -60, 60, 0, 2));
        assertFalse(HitGeometry.arc(at(3.5, 0.0), 2.5, -60, 60, 0, 2));
    }

    @Test
    void arcSliceOnlyCoversItsPortion() {
        double[] first = HitGeometry.arcSlice(70, -70, 0, 2); // 70 -> 0
        assertArrayEquals(new double[] {70, 0}, first, 1e-9);
        // A target 50° to the left is only swept in the second half of a right-to-left slash.
        Target left = at(1.5 * Math.cos(Math.toRadians(-50)), 1.5 * Math.sin(Math.toRadians(-50)));
        assertFalse(HitGeometry.arc(left, 2.5, first[0], first[1], 0, 2));
        double[] second = HitGeometry.arcSlice(70, -70, 1, 2);
        assertTrue(HitGeometry.arc(left, 2.5, second[0], second[1], 0, 2));
    }

    @Test
    void arcRespectsHeightBand() {
        assertFalse(HitGeometry.arc(new Target(1.5, 0, 0.3, 3.0, 4.0), 2.5, -60, 60, 0, 2.2));
    }

    @Test
    void wideTargetHitAtEdgeOfArc() {
        // Centre is just outside the arc, but its half-width reaches in.
        Target wide = new Target(1.5 * Math.cos(Math.toRadians(70)), 1.5 * Math.sin(Math.toRadians(70)), 0.7, 0, 2);
        assertTrue(HitGeometry.arc(wide, 2.5, -60, 60, 0, 2));
    }

    @Test
    void thrustIsNarrow() {
        assertTrue(HitGeometry.thrust(at(3.0, 0.2), 3.5, 0.4, 0, 2));
        assertFalse(HitGeometry.thrust(at(3.0, 1.5), 3.5, 0.4, 0, 2));
        assertFalse(HitGeometry.thrust(at(4.5, 0.0), 3.5, 0.4, 0, 2));
    }

    @Test
    void circleCentredAhead() {
        assertTrue(HitGeometry.circle(at(2.0, 1.0), 1.8, 1.6, 0, 2));
        assertFalse(HitGeometry.circle(at(-1.0, 0.0), 1.8, 1.6, 0, 2));
    }

    @Test
    void toLocalMatchesMinecraftYaw() {
        // yaw 0 faces +Z: a point at +Z is straight ahead; Minecraft's right is -X.
        double[] ahead = HitGeometry.toLocal(0, 2, 0);
        assertEquals(2.0, ahead[0], 1e-9);
        assertEquals(0.0, ahead[1], 1e-9);
        double[] right = HitGeometry.toLocal(-1, 0, 0);
        assertEquals(1.0, right[1], 1e-9);
        // yaw 90 faces -X.
        double[] ahead90 = HitGeometry.toLocal(-3, 0, 90);
        assertEquals(3.0, ahead90[0], 1e-9);
    }

    @Test
    void ringExpandsOverActiveTicksAndHitsAllAround() {
        // reach 5 over 10 ticks, thickness 1: a target 3 blocks away (any direction) is hit around tick 5 only.
        for (double[] dir : new double[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            Target t = new Target(dir[0], dir[1], 0.3, 0.0, 1.95);
            assertFalse(HitGeometry.ring(t, 5, 1, 0, 2, 10, -0.5, 0.6), "ring has not reached it yet");
            assertTrue(HitGeometry.ring(t, 5, 1, 0, 5, 10, -0.5, 0.6), "ring front passes it");
            assertFalse(HitGeometry.ring(t, 5, 1, 0, 9, 10, -0.5, 0.6), "ring has passed it");
        }
    }

    @Test
    void ringCoversEveryDistanceAndCanBeJumped() {
        // Every distance from 0 to reach is inside the band on some tick (no gaps between ticks).
        for (double d = 0.0; d <= 5.0; d += 0.1) {
            boolean hit = false;
            for (int i = 0; i < 10; i++) {
                hit |= HitGeometry.ring(new Target(d, 0, 0.0, 0.0, 1.95), 5, 1, 0, i, 10, -0.5, 0.6);
            }
            assertTrue(hit, "gap at distance " + d);
        }
        // Feet 1.2 blocks up clear a ring that is 0.6 high.
        Target jumping = new Target(3, 0, 0.3, 1.2, 3.0);
        for (int i = 0; i < 10; i++) {
            assertFalse(HitGeometry.ring(jumping, 5, 1, 0, i, 10, -0.5, 0.6));
        }
    }
}
