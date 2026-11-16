package dev.vestige.pale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BorderScalingTest {
    @Test
    void dividesBySizeDivisor() {
        assertEquals(500.0, BorderScaling.scaledSize(2000, 4, 32));
    }

    @Test
    void divisorOneKeepsSize() {
        assertEquals(2500.0, BorderScaling.scaledSize(2500, 1, 32));
    }

    @Test
    void clampsToMinimum() {
        assertEquals(32.0, BorderScaling.scaledSize(8, 4, 32));
    }

    @Test
    void gddDefaultScheduleWithNetherDivisorFour() {
        // GDD §6.1: Overworld radii 750..3000 -> Nether radii 188 (rounded), 250, 312.5, 437.5, 625, 750.
        double[] overworldRadius = {750, 1000, 1250, 1750, 2500, 3000};
        double[] netherRadius = {187.5, 250, 312.5, 437.5, 625, 750};
        for (int i = 0; i < overworldRadius.length; i++) {
            double netherDiameter = BorderScaling.scaledSize(overworldRadius[i] * 2, 4, 32);
            assertEquals(netherRadius[i] * 2, netherDiameter, 1e-9);
        }
    }

    @Test
    void rejectsDivisorBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> BorderScaling.scaledSize(100, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () -> BorderScaling.scaledSize(100, Double.NaN, 1));
    }

    @Test
    void rejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> BorderScaling.scaledSize(-1, 2, 1));
    }
}
