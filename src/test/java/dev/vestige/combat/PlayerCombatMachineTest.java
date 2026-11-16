package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.MoveSet;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.PlayerCombatMachine;
import dev.vestige.combat.machine.PlayerCombatMachine.MoveSetHandle;
import dev.vestige.combat.machine.RunningAction;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerCombatMachineTest {
    private static ActionDef action(int w, int a, int r) {
        return new ActionDef(new ActionDef.Timing(w, a, r, 0.4f, 0.4f, 0), ActionDef.Power.NONE, false, Optional.empty(),
                ActionDef.Motion.NONE, Optional.empty(), "", Optional.empty(), Optional.empty(), 30, 0);
    }

    private static final ActionDef LIGHT = action(5, 3, 10);
    private static final ActionDef HEAVY = new ActionDef(new ActionDef.Timing(10, 3, 12, 0.4f, 0.4f, 0), ActionDef.Power.NONE, true,
            Optional.empty(), ActionDef.Motion.NONE, Optional.empty(), "", Optional.of(new ActionDef.Charge(14, 0.45f, 0f)),
            Optional.empty(), 30, 0);
    private static final ActionDef ROLL = new ActionDef(new ActionDef.Timing(0, 10, 6, 0.4f, 0.4f, 0), ActionDef.Power.NONE, false,
            Optional.empty(), ActionDef.Motion.NONE, Optional.empty(), "", Optional.empty(),
            Optional.of(new ActionDef.Dodge(3, 9, 3.5f, 10)), 30, 0);

    private static final MoveSetHandle WEAPON = new MoveSetHandle(ResourceLocation.parse("test:blade"), new MoveSet(
            Map.of("l1", LIGHT, "l2", LIGHT, "l3", LIGHT, "heavy", HEAVY, "run", LIGHT, "rollatk", LIGHT),
            List.of("l1", "l2", "l3"), Optional.of("heavy"), Optional.of("run"), Optional.of("rollatk")));
    private static final MoveSetHandle COMMON = new MoveSetHandle(ResourceLocation.parse("test:player"), new MoveSet(
            Map.of("roll", ROLL, "backstep", action(0, 6, 4)), List.of(), Optional.empty(), Optional.empty(), Optional.empty()));

    private PlayerCombatMachine m;
    private boolean stamina;
    private boolean sprinting;
    private long now;

    private final PlayerCombatMachine.Context ctx = new PlayerCombatMachine.Context() {
        public Optional<MoveSetHandle> weapon() { return Optional.of(WEAPON); }
        public Optional<MoveSetHandle> common() { return Optional.of(COMMON); }
        public boolean canSpendStamina() { return stamina; }
        public boolean sprinting() { return sprinting; }
    };

    @BeforeEach
    void setUp() {
        m = new PlayerCombatMachine();
        stamina = true;
        sprinting = false;
        now = 0;
    }

    private Optional<PlayerCombatMachine.Start> press(CombatInput input) {
        return m.input(input, 1, input == CombatInput.ROLL ? 0f : Float.NaN, now, ctx);
    }

    private Optional<PlayerCombatMachine.Start> tick() {
        now++;
        return m.tick(now, ctx);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) tick();
    }

    @Test
    void lightStartsWhenIdle() {
        var s = press(CombatInput.LIGHT);
        assertTrue(s.isPresent());
        assertEquals("l1", s.get().action().ref.action());
    }

    @Test
    void noStaminaRejectsWithoutBuffering() {
        stamina = false;
        assertTrue(press(CombatInput.LIGHT).isEmpty());
        assertFalse(m.hasBuffered());
        assertFalse(m.busy());
    }

    @Test
    void comboChainsOnlyInsideWindow() {
        press(CombatInput.LIGHT);
        ticks(3); // still in windup
        assertTrue(press(CombatInput.LIGHT).isEmpty(), "too early: buffered, not started");
        assertTrue(m.hasBuffered());
        // Window opens at 5 + 3 + floor(10 * 0.4) = 12 ticks; the buffered press fires then.
        Optional<PlayerCombatMachine.Start> fired = Optional.empty();
        for (int i = 0; i < 9 && fired.isEmpty(); i++) fired = tick();
        assertTrue(fired.isPresent(), "buffered press should start the second hit");
        assertEquals("l2", fired.get().action().ref.action());
        assertEquals(12, now);
    }

    @Test
    void bufferExpires() {
        press(CombatInput.LIGHT);
        press(CombatInput.LIGHT); // tick 0, window at 12 > buffer 10
        ticks(14);
        assertEquals("l1", m.current() == null ? "l1" : m.current().ref.action());
        assertFalse(m.hasBuffered());
    }

    @Test
    void comboWrapsAndResetsAfterIdle() {
        for (String expected : List.of("l1", "l2", "l3", "l1")) {
            var s = press(CombatInput.LIGHT);
            assertEquals(expected, s.orElseThrow().action().ref.action());
            ticks(12);
        }
        ticks(40);
        assertEquals("l1", press(CombatInput.LIGHT).orElseThrow().action().ref.action());
    }

    @Test
    void rollCannotCancelWindupOrActive() {
        press(CombatInput.LIGHT);
        ticks(6); // active
        assertTrue(press(CombatInput.ROLL).isEmpty());
        assertEquals(RunningAction.Kind.LIGHT, m.current().kind);
    }

    @Test
    void rollGivesIframes() {
        var s = press(CombatInput.ROLL).orElseThrow();
        RunningAction roll = s.action();
        assertFalse(roll.invulnerable(0));
        ticks(3);
        assertTrue(roll.invulnerable(0));
        assertTrue(roll.perfectDodgeWindow(0));
        ticks(6);
        assertFalse(roll.invulnerable(0));
    }

    @Test
    void latencyToleranceBackdatesIframes() {
        RunningAction roll = press(CombatInput.ROLL).orElseThrow().action();
        ticks(1);
        assertFalse(roll.invulnerable(0));
        assertTrue(roll.invulnerable(2));
    }

    @Test
    void noDirectionMeansBackstep() {
        var s = m.input(CombatInput.ROLL, 1, Float.NaN, now, ctx);
        assertEquals("backstep", s.orElseThrow().action().ref.action());
    }

    @Test
    void lightRightAfterRollIsRollingAttack() {
        press(CombatInput.ROLL);
        ticks(16);
        assertNull(m.current());
        assertEquals("rollatk", press(CombatInput.LIGHT).orElseThrow().action().ref.action());
    }

    @Test
    void sprintingLightIsRunningAttack() {
        sprinting = true;
        assertEquals("run", press(CombatInput.LIGHT).orElseThrow().action().ref.action());
    }

    @Test
    void heavyHoldsChargeUntilReleased() {
        RunningAction heavy = press(CombatInput.HEAVY_PRESS).orElseThrow().action();
        ticks(10);
        assertEquals(10, heavy.elapsed());
        ticks(5);
        assertEquals(10, heavy.elapsed(), "paused at windup peak while held");
        assertEquals(5, heavy.chargeTicks());
        m.input(CombatInput.HEAVY_RELEASE, 2, Float.NaN, now, ctx);
        ticks(2);
        assertEquals(12, heavy.elapsed());
        assertEquals(5f / 14f, heavy.chargeRatio(), 1e-6);
    }

    @Test
    void tappedHeavyDoesNotCharge() {
        RunningAction heavy = press(CombatInput.HEAVY_PRESS).orElseThrow().action();
        m.input(CombatInput.HEAVY_RELEASE, 2, Float.NaN, now, ctx);
        ticks(12);
        assertEquals(0, heavy.chargeTicks());
        assertEquals(12, heavy.elapsed());
    }

    @Test
    void chargeCapsAtMax() {
        RunningAction heavy = press(CombatInput.HEAVY_PRESS).orElseThrow().action();
        ticks(40);
        assertEquals(14, heavy.chargeTicks());
        assertTrue(heavy.elapsed() > 10);
    }

    @Test
    void hitstopPausesTimeline() {
        RunningAction light = press(CombatInput.LIGHT).orElseThrow().action();
        ticks(6);
        light.addHitstop(3);
        ticks(3);
        assertEquals(6, light.elapsed());
        tick();
        assertEquals(7, light.elapsed());
    }

    @Test
    void interruptStunsAndClearsBuffer() {
        press(CombatInput.LIGHT);
        press(CombatInput.LIGHT);
        m.interrupt(now, 8);
        assertNull(m.current());
        assertFalse(m.hasBuffered());
        ticks(4);
        assertTrue(press(CombatInput.LIGHT).isEmpty());
        ticks(5);
        assertTrue(press(CombatInput.LIGHT).isPresent());
    }
}
