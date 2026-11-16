package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.CombatModule;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.combat.projectile.IncenseCloud;
import dev.vestige.combat.resonance.ResonanceService;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.Hollowed;
import dev.vestige.vitals.StaminaService;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The M4 stage 2 weapon classes on a real server: each class's signature behaviour (GDD §7.4,
 * §7.11) and the new defensive and stamina rules (§7.7, §8.2).
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatArsenalTests {
    private static final String ARENA = "arena";
    private static final Vec3 PLAYER_POS = new Vec3(4.5, 1, 2.5);

    private CombatArsenalTests() {}

    private static ServerPlayer player(GameTestHelper h, Item weapon, Item offhand) {
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

    /** A passive Hollowed at an offset (dx right of the player is -x, dz ahead is +z). */
    private static Hollowed dummy(GameTestHelper h, double dx, double dz) {
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(PLAYER_POS.x + dx, 1, PLAYER_POS.z + dz));
        mob.setNoAi(true);
        mob.setYRot(180f);
        mob.setYBodyRot(180f);
        mob.setYHeadRot(180f);
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    private static void input(ServerPlayer p, CombatInput in) {
        PlayerCombat.handleInput(p, 7, in.ordinal(), Float.NaN);
    }

    private static String action(ServerPlayer p) {
        RunningAction a = PlayerCombat.state(p).machine().current();
        return a == null ? "none" : a.ref.action();
    }

    private static float lost(Hollowed mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static int arrows(ServerPlayer p) {
        return p.getInventory().countItem(Items.ARROW);
    }

    /** The Hunting Bow looses a real arrow that damages a target 6 blocks away and uses up one arrow. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bowShotHitsAtRange(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.HUNTING_BOW.get(), null);
        p.getInventory().add(new ItemStack(Items.ARROW, 5));
        Hollowed mob = dummy(h, 0, 6.0);
        h.runAfterDelay(2, () -> {
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mob.getBoundingBox().getCenter());
            input(p, CombatInput.LIGHT);
        });
        h.runAfterDelay(4, () -> h.assertTrue(action(p).equals("quick_shot"), "expected quick_shot, was " + action(p)));
        h.succeedWhen(() -> {
            h.assertTrue(lost(mob) > 0f, "arrow has not hit yet");
            h.assertTrue(arrows(p) == 4, "one arrow should be used, have " + arrows(p));
            TestPlayers.remove(p);
        });
    }

    /** Without arrows a survival player cannot shoot: the bow action never starts. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bowNeedsArrows(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.HUNTING_BOW.get(), null);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(4, () -> {
            h.assertTrue(action(p).equals("none"), "no arrows, but started " + action(p));
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                    new AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(12)).isEmpty(), "no arrow should exist");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Pinning Shot roots the target: it is forced into a stagger. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void pinningShotRoots(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.HUNTING_BOW.get(), null);
        p.getInventory().add(new ItemStack(Items.ARROW, 5));
        Hollowed mob = dummy(h, 0, 6.0);
        h.runAfterDelay(2, () -> {
            ResonanceService.set(p, 100f);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mob.getBoundingBox().getCenter());
            input(p, CombatInput.ART);
        });
        h.succeedWhen(() -> {
            h.assertTrue(mob.currentAction() != null && mob.currentAction().ref.action().equals("stagger"),
                    "target not pinned yet: " + (mob.currentAction() == null ? "none" : mob.currentAction().ref.action()));
            TestPlayers.remove(p);
        });
    }

    /** A Bell Toll is an expanding ring: it hits enemies beside and behind the player, not just ahead. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bellTollHitsAllAround(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.HANDBELL.get(), null);
        Hollowed left = dummy(h, 3.0, 0.0);
        Hollowed right = dummy(h, -3.0, 0.0);
        Hollowed behind = dummy(h, 0.0, -2.0);
        h.runAfterDelay(2, () -> input(p, CombatInput.HEAVY_PRESS));
        h.runAfterDelay(3, () -> input(p, CombatInput.HEAVY_RELEASE));
        h.runAfterDelay(40, () -> {
            h.assertTrue(lost(left) > 0f && lost(right) > 0f && lost(behind) > 0f,
                    "ring should hit all three: " + lost(left) + ", " + lost(right) + ", " + lost(behind));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /**
     * Smolder Wake leaves an incense cloud (radius 4) wider than its spin (3.2): an enemy standing at
     * 3.7 blocks is untouched by the spin but keeps burning in the smoke.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void censerCloudBurnsOverTime(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.CHANDLERS_CENSER.get(), null);
        Hollowed mob = dummy(h, 0.0, 3.7);
        h.runAfterDelay(2, () -> {
            ResonanceService.set(p, 100f);
            input(p, CombatInput.ART);
        });
        // The spin is active on ticks 12-19; the cloud appears on tick 12 and first pulses about 10 ticks later.
        h.runAfterDelay(21, () -> {
            // A spin hit deals ~100 here; at most one cloud pulse (~5) may already have landed.
            h.assertTrue(lost(mob) < 10f, "the spin should not reach 3.7 blocks, lost " + lost(mob));
            var clouds = h.getLevel().getEntitiesOfClass(IncenseCloud.class, mob.getBoundingBox().inflate(6));
            h.assertTrue(clouds.size() == 1, "expected one incense cloud, found " + clouds.size());
        });
        h.runAfterDelay(72, () -> {
            h.assertTrue(lost(mob) > 15f, "the cloud should keep burning an enemy standing in it, lost " + lost(mob));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Cleaving Wake's shockwave reaches an enemy 5.5 blocks ahead that no normal swing could touch. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void greatbladeWakeReachesFar(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.WICKIRON_GREATBLADE.get(), null);
        Hollowed far = dummy(h, 0.0, 5.5);
        h.runAfterDelay(2, () -> {
            ResonanceService.set(p, 100f);
            input(p, CombatInput.ART);
        });
        h.runAfterDelay(2 + 18 + 8, () -> {
            h.assertTrue(lost(far) > 0f, "the wake should reach 5.5 blocks");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** A greatblade light swing does not reach 5.5 blocks (control for the wake test). */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void greatbladeSwingHasNormalReach(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.WICKIRON_GREATBLADE.get(), null);
        Hollowed far = dummy(h, 0.0, 5.5);
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(2 + 12 + 6, () -> {
            h.assertTrue(lost(far) == 0f, "a light swing should not reach 5.5 blocks");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Quickstep makes the next hit a critical: it deals clearly more than the same cut without it. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void quickstepMakesNextHitCritical(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.TAPER_FANG.get(), null);
        Hollowed mob = dummy(h, 0.0, 1.6);
        float[] plain = {0f};
        h.runAfterDelay(2, () -> input(p, CombatInput.LIGHT));
        h.runAfterDelay(20, () -> {
            plain[0] = lost(mob);
            mob.setHealth(mob.getMaxHealth());
            ResonanceService.set(p, 100f);
            // Quickstep toward the target (direction 0 = forward relative to facing).
            PlayerCombat.handleInput(p, 8, CombatInput.ART.ordinal(), 0f);
        });
        h.runAfterDelay(40, () -> {
            p.teleportTo(h.absoluteVec(PLAYER_POS).x, h.absoluteVec(PLAYER_POS).y, h.absoluteVec(PLAYER_POS).z);
            input(p, CombatInput.LIGHT);
        });
        h.runAfterDelay(55, () -> {
            float keen = lost(mob);
            h.assertTrue(plain[0] > 0f, "plain cut should land");
            h.assertTrue(keen > plain[0] * 1.3f, "keen cut should be a critical: plain " + plain[0] + ", keen " + keen);
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** The Timber Bulwark absorbs the whole swipe and costs less stamina than a kite shield. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void bulwarkBlocksCompletely(GameTestHelper h) {
        ServerPlayer p = player(h, WeaponModule.KEEPSAKE_BLADE.get(), WeaponModule.TIMBER_BULWARK.get());
        Hollowed mob = dummy(h, 0.0, 1.6);
        h.runAfterDelay(2, () -> input(p, CombatInput.GUARD_PRESS));
        h.runAfterDelay(4, () -> {
            h.assertTrue(action(p).equals("guard_bulwark"), "expected guard_bulwark, was " + action(p));
            mob.setTarget(p);
            mob.startAction("swipe");
        });
        h.runAfterDelay(4 + 14 + 4, () -> {
            h.assertTrue(p.getHealth() == p.getMaxHealth(), "bulwark should absorb all, took " + (p.getMaxHealth() - p.getHealth()));
            float cost = 100f - StaminaService.get(p).current();
            // 22 x (1 - 0.78) x 0.9 + 3 = 7.4; the kite shield (stability 60) would cost 10.9.
            h.assertTrue(cost > 0f && cost < 9f, "bulwark block cost " + cost);
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Sprinting while armed costs stamina (6/s); sprinting unarmed does not (GDD §8.2). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void sprintCostsStaminaWhenArmed(GameTestHelper h) {
        ServerPlayer armed = player(h, WeaponModule.KEEPSAKE_BLADE.get(), null);
        ServerPlayer unarmed = player(h, Items.BREAD, null);
        h.onEachTick(() -> {
            armed.setSprinting(true);
            unarmed.setSprinting(true);
        });
        h.runAfterDelay(21, () -> {
            float spent = 100f - StaminaService.get(armed).current();
            h.assertTrue(spent > 4f && spent < 8f, "armed sprint should cost ~6 per second, cost " + spent);
            h.assertTrue(StaminaService.get(unarmed).current() == 100f, "unarmed sprint should be free");
            TestPlayers.remove(armed);
            TestPlayers.remove(unarmed);
            h.succeed();
        });
    }

    /** The incense cloud entity type exists and is not saved with the world (combat state only). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void incenseCloudIsTransient(GameTestHelper h) {
        IncenseCloud cloud = CombatModule.INCENSE_CLOUD.get().create(h.getLevel());
        h.assertTrue(cloud != null && !cloud.shouldBeSaved(), "cloud should be transient");
        h.succeed();
    }
}
