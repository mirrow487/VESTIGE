package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.Hollowed;
import dev.vestige.vitals.StaminaService;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side combat behaviour on a real level (GDD §7). Players are survival mock players whose
 * combat tick is driven explicitly (mock players are not ticked by a connection).
 * Yaw 0 faces +Z; enemies are placed at +Z from the player.
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatTests {
    private static final String ARENA = "arena";
    private static final Vec3 PLAYER_POS = new Vec3(4.5, 1, 2.5);

    private CombatTests() {}

    private static ServerPlayer armedPlayer(GameTestHelper h, net.minecraft.world.item.Item weapon) {
        ServerPlayer p = TestPlayers.survival(h, PLAYER_POS, 0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        h.onEachTick(() -> {
            if (p.isAlive()) {
                PlayerCombat.serverTick(p);
            }
        });
        return p;
    }

    private static Hollowed dummy(GameTestHelper h, EntityType<Hollowed> type, double distance) {
        Hollowed mob = h.spawn(type, new Vec3(PLAYER_POS.x, 1, PLAYER_POS.z + distance));
        mob.setNoAi(true);
        mob.setYRot(180f); // facing the player (-Z)
        mob.setYBodyRot(180f);
        mob.setYHeadRot(180f);
        return mob;
    }

    private static void input(ServerPlayer p, CombatInput in) {
        PlayerCombat.handleInput(p, 1, in.ordinal(), Float.NaN);
    }

    /** A light attack deals damage only once its active frames start (blade light: windup 5). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void lightAttackHitsOnlyInActiveFrames(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), 2.0);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(2 + 4, () -> h.assertTrue(mob.getHealth() == mob.getMaxHealth(), "damage landed during windup"));
        h.runAfterDelay(2 + 9, () -> {
            h.assertTrue(mob.getHealth() < mob.getMaxHealth(), "light attack did not hit a hollowed 2 blocks ahead");
            // 60 AR, MV 1.0 vs defence 10: 60²/(60+20) = 45.
            h.assertTrue(Math.abs((mob.getMaxHealth() - mob.getHealth()) - 45f) < 0.5f, "expected 45 damage, got " + (mob.getMaxHealth() - mob.getHealth()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void attackMissesTargetBehind(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), -2.0);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(25, () -> {
            h.assertTrue(mob.getHealth() == mob.getMaxHealth(), "hit a target behind the player");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void staminaSpentThenRegenerates(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(4, () -> h.assertTrue(Math.abs(StaminaService.get(p).current() - 88f) < 0.01f,
                "light attack should cost 12 stamina, have " + StaminaService.get(p).current()));
        h.runAfterDelay(70, () -> {
            h.assertTrue(StaminaService.get(p).current() >= 99.9f, "stamina did not regenerate: " + StaminaService.get(p).current());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void noStaminaRejectsAttack(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        h.runAfterDelay(2, () -> {
            StaminaService.set(p, 0f);
            input(p, CombatInput.LIGHT);
            h.assertTrue(!PlayerCombat.busy(p), "attack started with zero stamina");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Without dodging, the hollowed's swipe lands; rolling into it with good timing avoids all damage. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void swipeHitsWithoutDodge(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            h.assertTrue(mob.startAction("swipe"), "swipe missing from hollowed moveset");
        });
        h.runAfterDelay(2 + 20, () -> {
            h.assertTrue(p.getHealth() < p.getMaxHealth(), "swipe did not hit the player");
            // 22 AR vs player defence 0.
            h.assertTrue(Math.abs((p.getMaxHealth() - p.getHealth()) - 22f) < 0.5f, "expected 22 damage, got " + (p.getMaxHealth() - p.getHealth()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void rollIframesAvoidSwipe(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        // Swipe active frames: ticks 14-16 after start. Roll at tick 11 => i-frames 11+3 .. 11+10.
        h.runAfterDelay(2 + 11, () -> PlayerCombat.handleInput(p, 2, CombatInput.ROLL.ordinal(), 180f));
        h.runAfterDelay(2 + 22, () -> {
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "rolled but still took " + (p.getMaxHealth() - p.getHealth()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Being hit outside hyperarmor interrupts the player's attack. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void hitInterruptsPlayerAttack(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), 1.6);
        h.runAfterDelay(2, () -> {
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(2 + 14, () -> input(p, CombatInput.LIGHT)); // swiped during our windup
        h.runAfterDelay(2 + 18, () -> {
            RunningAction a = PlayerCombat.state(p).machine().current();
            h.assertTrue(a != null && a.ref.action().equals("flinch"), "expected a flinch, got " + (a == null ? "nothing" : a.ref));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Poise damage makes the hollowed flinch on each hit and stagger when its poise breaks. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void poiseBreakStaggers(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.HAND_MAUL.get());
        Hollowed mob = dummy(h, EntityModule.HOLLOWED.get(), 2.0);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT)); // maul light: 25 poise vs hollowed poise 20
        h.succeedWhen(() -> {
            h.assertTrue(mob.isStaggered(), "hollowed not staggered yet");
            TestPlayers.remove(p);
        });
    }

    /** A fully charged heavy hits harder than a tapped one (GDD §7.5). */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void chargedHeavyHitsHarder(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed tapped = dummy(h, EntityModule.HOLLOWED.get(), 2.0);
        float[] tapDamage = new float[1];
        h.runAfterDelay(2, () -> {
            input(p, CombatInput.HEAVY_PRESS);
            input(p, CombatInput.HEAVY_RELEASE);
        });
        h.runAfterDelay(30, () -> {
            tapDamage[0] = tapped.getMaxHealth() - tapped.getHealth();
            h.assertTrue(tapDamage[0] > 0f, "tapped heavy missed");
            tapped.discard();
        });
        Hollowed[] charged = new Hollowed[1];
        h.runAfterDelay(40, () -> charged[0] = dummy(h, EntityModule.HOLLOWED.get(), 2.0));
        h.runAfterDelay(45, () -> input(p, CombatInput.HEAVY_PRESS));
        h.runAfterDelay(45 + 30, () -> input(p, CombatInput.HEAVY_RELEASE));
        h.runAfterDelay(45 + 45, () -> {
            float chargedDamage = charged[0].getMaxHealth() - charged[0].getHealth();
            h.assertTrue(chargedDamage > tapDamage[0] * 1.2f, "charged " + chargedDamage + " vs tapped " + tapDamage[0]);
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void swappingWeaponCancelsAttack(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(4, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT)));
        h.runAfterDelay(7, () -> {
            h.assertTrue(!PlayerCombat.busy(p), "attack continued after the weapon was swapped");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /**
     * GDD §8.1: vanilla creatures get ×5 health and vanilla damage is scaled to match. Uses a pig
     * (no daylight burning, which made a zombie-based version of this test flaky).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void healthScaledForVanillaMobsAndDamage(GameTestHelper h) {
        var pig = h.spawn(EntityType.PIG, new Vec3(2.5, 1, 6.5));
        pig.setNoAi(true);
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(pig.getMaxHealth() - 50f) < 0.01f, "pig max health " + pig.getMaxHealth());
            h.assertTrue(Math.abs(pig.getHealth() - 50f) < 0.01f, "pig not filled to scaled max: " + pig.getHealth());
            pig.hurt(h.getLevel().damageSources().generic(), 4f);
            h.assertTrue(Math.abs(pig.getHealth() - 30f) < 0.01f, "vanilla 4 damage should become 20, health " + pig.getHealth());
            h.succeed();
        });
    }

    /** VESTIGE enemies use profile health in VESTIGE units and are not rescaled. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void enemyProfileHealth(GameTestHelper h) {
        Hollowed brute = h.spawn(EntityModule.HOLLOWED_BRUTE.get(), new Vec3(4.5, 1, 6.5));
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(brute.getMaxHealth() - 220f) < 0.01f, "brute max health " + brute.getMaxHealth());
            h.assertTrue(Math.abs(brute.combatDefense() - 25f) < 0.01f, "brute defence " + brute.combatDefense());
            h.succeed();
        });
    }

    /** The AI picks telegraphed attacks from its table and respects range. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hollowedAiAttacksTargetInRange(GameTestHelper h) {
        ServerPlayer p = armedPlayer(h, WeaponModule.KEEPSAKE_BLADE.get());
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(PLAYER_POS.x, 1, PLAYER_POS.z + 2.2));
        mob.setTarget(p);
        h.succeedWhen(() -> {
            RunningAction a = mob.currentAction();
            h.assertTrue(a != null && a.def.telegraph().isPresent(), "hollowed has not started a telegraphed attack");
            h.assertTrue(a.def.timing().windup() >= 9, "attack windup too short to read: " + a.def.timing().windup());
            TestPlayers.remove(p);
        });
    }

    /** /kill deals Float.MAX_VALUE; scaling must not overflow it into NaN health (regression). */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void killStillKillsScaledEntities(GameTestHelper h) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(2.5, 1, 6.5));
        Hollowed hollowed = h.spawn(EntityModule.HOLLOWED_BRUTE.get(), new Vec3(6.5, 1, 6.5));
        h.runAfterDelay(2, () -> {
            zombie.kill();
            hollowed.kill();
            h.assertTrue(!zombie.isAlive() && !Float.isNaN(zombie.getHealth()), "zombie survived /kill, health " + zombie.getHealth());
            h.assertTrue(!hollowed.isAlive() && !Float.isNaN(hollowed.getHealth()), "brute survived /kill, health " + hollowed.getHealth());
            h.succeed();
        });
    }
}
