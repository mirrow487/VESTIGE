package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.resonance.ResonanceService;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.Hollowed;
import dev.vestige.vitals.StaminaService;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Guard, parry, riposte, weapon arts and Resonance on a real server (GDD §7.7, §7.11, §8.3).
 * Hollowed swipe: windup 14, active 14–16, white glint (parriable). Lunge grab: red/grab (unparriable).
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatDepthTests {
    private static final String ARENA = "arena";
    private static final Vec3 PLAYER_POS = new Vec3(4.5, 1, 2.5);
    private static final int SWIPE_ACTIVE = 14;

    private CombatDepthTests() {}

    private static ServerPlayer player(GameTestHelper h, net.minecraft.world.item.Item weapon, net.minecraft.world.item.Item offhand) {
        ServerPlayer p = TestPlayers.survival(h, PLAYER_POS, 0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        if (offhand != null) {
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(offhand));
        }
        h.onEachTick(() -> {
            if (p.isAlive()) {
                PlayerCombat.serverTick(p);
            }
        });
        return p;
    }

    private static Hollowed dummy(GameTestHelper h, double distance) {
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(PLAYER_POS.x, 1, PLAYER_POS.z + distance));
        mob.setNoAi(true);
        mob.setYRot(180f);
        mob.setYBodyRot(180f);
        mob.setYHeadRot(180f);
        return mob;
    }

    private static void input(ServerPlayer p, CombatInput in) {
        PlayerCombat.handleInput(p, 7, in.ordinal(), Float.NaN);
    }

    private static String action(ServerPlayer p) {
        RunningAction a = PlayerCombat.state(p).machine().current();
        return a == null ? "none" : a.ref.action();
    }

    private static String action(Hollowed mob) {
        return mob.currentAction() == null ? "none" : mob.currentAction().ref.action();
    }

    /** A raised weapon guard absorbs most of the swipe and costs stamina instead (GDD §7.7). */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void weaponGuardBlocksSwipe(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(4, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(4 + SWIPE_ACTIVE + 4, () -> {
            float taken = p.getMaxHealth() - p.getHealth();
            // 22 damage x (1 - 0.55 weapon absorption) = 9.9
            h.assertTrue(Math.abs(taken - 9.9f) < 0.5f, "blocked damage should be ~9.9, was " + taken);
            h.assertTrue(StaminaService.get(p).current() < 100f, "blocking should cost stamina");
            h.assertTrue(action(p).equals("guard_weapon"), "guard should hold through a block, was " + action(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Out of stamina, the guard breaks: the player staggers and takes most of the hit. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void guardBreaksWithoutStamina(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(4, () -> {
            StaminaService.set(p, 2f);
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(4 + SWIPE_ACTIVE + 2, () -> {
            h.assertTrue(action(p).equals("guard_break"), "expected guard_break, was " + action(p));
            h.assertTrue(p.getMaxHealth() - p.getHealth() > 9.9f + 1f, "a broken guard should let more damage through");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** A buckler parry timed into the swipe deflects it completely and makes the Hollowed reel. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void bucklerParryDeflectsSwipe(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), WeaponModule.PLANK_BUCKLER.get());
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        // Parry window opens 2 ticks after the press; press 3 ticks before the swipe's active frames.
        h.runAfterDelay(2 + SWIPE_ACTIVE - 3, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(2 + SWIPE_ACTIVE + 3, () -> {
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "parried but took " + (p.getMaxHealth() - p.getHealth()));
            h.assertTrue(action(mob).equals("parried"), "hollowed should reel, was " + action(mob));
            h.assertTrue(ResonanceService.get(p) >= 20f, "parry should grant Resonance, have " + ResonanceService.get(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Parrying too early does nothing: the window is precise. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void earlyParryFails(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), WeaponModule.PLANK_BUCKLER.get());
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(2 + SWIPE_ACTIVE - 9, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(2 + SWIPE_ACTIVE + 3, () -> {
            h.assertTrue(p.getHealth() < p.getMaxHealth(), "a parry 9 ticks early must not succeed");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Grabs (red glint + chime) cannot be parried. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void grabCannotBeParried(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), WeaponModule.PLANK_BUCKLER.get());
        Hollowed mob = dummy(h, 2.4);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("lunge_grab");
        });
        // lunge_grab: windup 18, active 18-22.
        h.runAfterDelay(2 + 18 - 3, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(2 + 26, () -> {
            h.assertTrue(p.getHealth() < p.getMaxHealth(), "the grab should land through a parry");
            h.assertTrue(!action(mob).equals("parried"), "a grab must not be parried");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** After a parry, a light attack becomes a riposte: a critical that kills a Hollowed outright. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void riposteAfterParry(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), WeaponModule.PLANK_BUCKLER.get());
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(2 + SWIPE_ACTIVE - 3, () -> input(p, CombatInput.GUARD_PRESS));
        // The parry action must finish its recovery before attacking (parry: 2+4+12; combo window at 50%).
        h.runAfterDelay(2 + SWIPE_ACTIVE + 10, () -> {
            h.assertTrue(action(mob).equals("parried"), "precondition: hollowed reeling, was " + action(mob));
            input(p, CombatInput.LIGHT);
            h.assertTrue(action(p).equals("riposte"), "light attack should become a riposte, was " + action(p));
            h.assertTrue(action(mob).equals("riposted"), "victim should play its riposted reaction, was " + action(mob));
        });
        h.runAfterDelay(2 + SWIPE_ACTIVE + 10 + 14, () -> {
            h.assertTrue(!mob.isAlive() || mob.getHealth() <= 0f, "riposte (60 AR x 3.0) should kill a 90 HP hollowed, health " + mob.getHealth());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** From behind an enemy facing away, a light attack is a backstab (same critical). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void backstabFromBehind(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(PLAYER_POS.x, 1, PLAYER_POS.z + 1.4));
        mob.setNoAi(true);
        mob.setYRot(0f); // facing +Z: away from the player
        mob.setYBodyRot(0f);
        mob.setYHeadRot(0f);
        h.runAfterDelay(3, () -> {
            input(p, CombatInput.LIGHT);
            h.assertTrue(action(p).equals("riposte"), "expected a backstab (riposte action), was " + action(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Arts cost Resonance; without enough, the art does not start. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void artSpendsResonance(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        h.runAfterDelay(2, () -> {
            input(p, CombatInput.ART);
            h.assertTrue(action(p).equals("none"), "art started with no Resonance");
            ResonanceService.set(p, 30f);
            input(p, CombatInput.ART);
            h.assertTrue(action(p).equals("riposte_stance"), "art should start, was " + action(p));
            h.assertTrue(Math.abs(ResonanceService.get(p) - 10f) < 0.01f, "Riposte Stance costs 20, left " + ResonanceService.get(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Blade art: a parry inside Riposte Stance answers with an automatic counter-slash. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void riposteStanceCounters(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        Hollowed mob = dummy(h, 1.6);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        h.runAfterDelay(2, () -> {
            ResonanceService.set(p, 40f);
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(2 + SWIPE_ACTIVE - 6, () -> input(p, CombatInput.ART));
        h.runAfterDelay(2 + SWIPE_ACTIVE + 3, () -> {
            h.assertTrue(action(mob).equals("parried"), "stance should have parried, hollowed is " + action(mob));
            h.assertTrue(action(p).equals("riposte_counter"), "parry should chain into the counter, was " + action(p));
        });
        h.runAfterDelay(2 + SWIPE_ACTIVE + 10, () -> {
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "stance should have parried the swipe");
            h.assertTrue(mob.getHealth() < 1000f, "counter-slash should hit");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /**
     * Crescendo (full Resonance) empowers the art: Tremor hits harder at 100 than at 40.
     * (Dummy health stays below vanilla's 1024 max-health attribute cap.)
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void crescendoEmpowersArt(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.HAND_MAUL.get(), null);
        Hollowed[] target = new Hollowed[1];
        float[] normal = new float[1];
        h.runAfterDelay(2, () -> {
            target[0] = dummy(h, 2.0);
            target[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            target[0].setHealth(1000);
            ResonanceService.set(p, 40f);
            input(p, CombatInput.ART);
        });
        h.runAfterDelay(50, () -> {
            normal[0] = 1000f - target[0].getHealth();
            h.assertTrue(normal[0] > 0f, "tremor missed");
            target[0].discard();
            target[0] = dummy(h, 2.0);
            target[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            target[0].setHealth(1000);
            StaminaService.set(p, 100f);
            ResonanceService.set(p, 100f);
            input(p, CombatInput.ART);
        });
        h.runAfterDelay(100, () -> {
            float empowered = 1000f - target[0].getHealth();
            h.assertTrue(empowered > normal[0] * 1.2f, "crescendo " + empowered + " vs normal " + normal[0]);
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Hits build Resonance; taking an unblocked hit breaks the rhythm. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void resonanceRisesOnHitsAndFallsWhenHit(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        Hollowed mob = dummy(h, 1.6);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        float[] after = new float[1];
        h.runAfterDelay(14, () -> {
            after[0] = ResonanceService.get(p);
            h.assertTrue(after[0] > 0f, "hit should build Resonance");
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(14 + SWIPE_ACTIVE + 3, () -> {
            h.assertTrue(ResonanceService.get(p) < after[0], "getting hit should drain Resonance");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** With a kite shield (vanilla shield) the guard absorbs nearly everything. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void shieldGuardOutblocksWeaponGuard(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), Items.SHIELD);
        Hollowed mob = dummy(h, 1.6);
        h.runAfterDelay(2, () -> {
            input(p, CombatInput.GUARD_PRESS);
            h.assertTrue(action(p).equals("guard_shield"), "shield should use the shield guard, was " + action(p));
        });
        h.runAfterDelay(4, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(4 + SWIPE_ACTIVE + 4, () -> {
            float taken = p.getMaxHealth() - p.getHealth();
            h.assertTrue(taken < 3f, "kite shield (0.9 absorption) should take ~2.2, took " + taken);
            TestPlayers.remove(p);
            h.succeed();
        });
    }
}
