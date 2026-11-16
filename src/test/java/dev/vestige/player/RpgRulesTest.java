package dev.vestige.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vestige.waylight.WaylightRules;
import dev.vestige.waylight.WaylightRules.Refusal;
import org.junit.jupiter.api.Test;

class RpgRulesTest {
    @Test
    void flaskHealsFortyPercentOverTwentyTicks() {
        float total = 0;
        for (int i = 0; i < FlaskRules.HEAL_TICKS; i++) {
            total += FlaskRules.healPerTick(100f, 0);
        }
        assertEquals(40f, total, 1e-3);
        assertEquals(0.60f, FlaskRules.healFraction(5), 1e-6);
        assertEquals(0.60f, FlaskRules.healFraction(99), 1e-6, "kindling is capped");
        assertEquals(0.44f, FlaskRules.healFraction(1), 1e-6);
    }

    @Test
    void flaskWicksCapAtTen() {
        assertEquals(4, FlaskRules.afterWick(3));
        assertEquals(10, FlaskRules.afterWick(10));
    }

    @Test
    void glimValues() {
        assertEquals(5, GlimRules.vanillaMob(100f), "a zombie (100 HP after scaling) is worth 5");
        assertEquals(1, GlimRules.vanillaMob(4f));
        assertEquals(32, GlimRules.profile(32, 90f));
        assertEquals(30, GlimRules.profile(0, 90f), "no profile value: a third of its health");
    }

    @Test
    void restRules() {
        assertEquals(Refusal.NONE, WaylightRules.rest(false, 50, 2));
        assertEquals(Refusal.HOSTILES_NEAR, WaylightRules.rest(false, 10, 2));
        assertEquals(Refusal.IN_COMBAT, WaylightRules.rest(true, 50, 2));
        assertEquals(Refusal.TOO_FAR, WaylightRules.rest(false, 50, 9));
    }

    @Test
    void travelRules() {
        assertEquals(Refusal.NONE, WaylightRules.travel(false, 100, 2, true, true, false));
        assertEquals(Refusal.HOSTILES_NEAR, WaylightRules.travel(false, 20, 2, true, true, false), "24-block hostile radius");
        assertEquals(Refusal.NONE, WaylightRules.travel(false, 30, 2, true, true, false));
        assertEquals(Refusal.IN_COMBAT, WaylightRules.travel(true, 100, 2, true, true, false));
        assertEquals(Refusal.UNKNOWN_DESTINATION, WaylightRules.travel(false, 100, 2, true, false, false));
        assertEquals(Refusal.NOT_LIT, WaylightRules.travel(false, 100, 2, false, true, false));
        assertEquals(Refusal.SAME_PLACE, WaylightRules.travel(false, 100, 2, true, true, true));
    }
}
