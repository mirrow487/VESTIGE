package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.Hollowed;
import dev.vestige.player.EmberGhost;
import dev.vestige.player.FlaskService;
import dev.vestige.player.GlimService;
import dev.vestige.player.PlayerModule;
import dev.vestige.waylight.WaylightBlock;
import dev.vestige.waylight.WaylightModule;
import dev.vestige.waylight.WaylightService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Core RPG systems on a real server (M4 stage 3): the Wick Flask (GDD §8.4), Glim and the
 * Ember-Ghost (§8.5, §23.1) and Waylights (§6.6). The arena floor surface is y = 0. Waylight tests
 * run in their own batches: hostiles from neighbouring tests would otherwise be within the 24-block
 * no-travel radius.
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RpgSystemsTests {
    private static final String ARENA = "arena";
    private static final int DRINK_WINDUP = 8;

    private RpgSystemsTests() {}

    private static ServerPlayer player(GameTestHelper h, Vec3 pos) {
        ServerPlayer p = TestPlayers.survival(h, pos, 0f);
        h.onEachTick(() -> {
            if (p.isAlive()) {
                PlayerCombat.serverTick(p);
                PlayerModule.tickFlask(p);
            }
        });
        return p;
    }

    private static String action(ServerPlayer p) {
        RunningAction a = PlayerCombat.state(p).machine().current();
        return a == null ? "none" : a.ref.action();
    }

    private static void input(ServerPlayer p, CombatInput in) {
        PlayerCombat.handleInput(p, 7, in.ordinal(), Float.NaN);
    }

    // ------------------------------------------------------------------ Wick Flask

    /** A sip heals 40% of max health over the drink's 20 active ticks and uses one of three charges. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void flaskHealsFortyPercent(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 4.5));
        h.runAfterDelay(1, () -> {
            p.setHealth(20f);
            input(p, CombatInput.FLASK);
        });
        h.runAfterDelay(3, () -> h.assertTrue(action(p).equals("flask_drink"), "expected flask_drink, was " + action(p)));
        h.runAfterDelay(1 + DRINK_WINDUP + 24, () -> {
            float healed = p.getHealth() - 20f;
            h.assertTrue(Math.abs(healed - 0.4f * p.getMaxHealth()) < 1f, "should heal 40% of max, healed " + healed);
            h.assertTrue(FlaskService.get(p).charges() == 2, "one charge used, left " + FlaskService.get(p).charges());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** With no charges the drink is an empty shake: no healing. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void emptyFlaskDoesNotHeal(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 4.5));
        h.runAfterDelay(1, () -> {
            FlaskService.set(p, 0, 3, 0);
            p.setHealth(20f);
            input(p, CombatInput.FLASK);
        });
        h.runAfterDelay(3, () -> h.assertTrue(action(p).equals("flask_empty"), "expected flask_empty, was " + action(p)));
        h.runAfterDelay(30, () -> {
            h.assertTrue(p.getHealth() == 20f, "an empty flask must not heal, health " + p.getHealth());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Being hit mid-drink (the action is interrupted) cuts the heal short. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void hitInterruptsFlask(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 4.5));
        h.runAfterDelay(1, () -> {
            p.setHealth(20f);
            input(p, CombatInput.FLASK);
        });
        h.runAfterDelay(1 + DRINK_WINDUP + 5, () -> PlayerCombat.state(p).machine().interrupt(p.level().getGameTime(), 8));
        h.runAfterDelay(1 + DRINK_WINDUP + 24, () -> {
            float healed = p.getHealth() - 20f;
            h.assertTrue(healed > 0f && healed < 0.4f * p.getMaxHealth() * 0.5f, "interrupted heal should be partial, healed " + healed);
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Flask Wicks add a charge each, up to ten. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void flaskWicksCapAtTen(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 4.5));
        h.assertTrue(FlaskService.addWick(p) && FlaskService.get(p).max() == 4, "a wick should raise the maximum to 4");
        for (int i = 0; i < 10; i++) {
            FlaskService.addWick(p);
        }
        h.assertTrue(FlaskService.get(p).max() == 10 && !FlaskService.addWick(p), "the maximum is 10");
        TestPlayers.remove(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ Glim and the Ember-Ghost

    /** Killing a Hollowed puts its profile's Glim (32) into the killer's purse. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void killingGivesGlim(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 2.5));
        Hollowed mob = h.spawn(EntityModule.HOLLOWED.get(), new Vec3(4.5, 0, 4.5));
        mob.setNoAi(true);
        h.runAfterDelay(2, () -> mob.hurt(p.damageSources().playerAttack(p), 100_000f));
        h.runAfterDelay(4, () -> {
            h.assertTrue(GlimService.get(p) == 32, "expected 32 Glim, have " + GlimService.get(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Dying leaves the carried Glim in an Ember-Ghost where the player fell; the purse is empty. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void deathLeavesEmberGhost(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 4.5));
        GlimService.set(p, 250);
        h.runAfterDelay(2, () -> p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE));
        h.runAfterDelay(4, () -> {
            var ghosts = h.getLevel().getEntitiesOfClass(EmberGhost.class, new AABB(p.blockPosition()).inflate(3));
            h.assertTrue(ghosts.size() == 1, "expected one ghost, found " + ghosts.size());
            h.assertTrue(ghosts.get(0).glim() == 250, "the ghost should hold 250, holds " + ghosts.get(0).glim());
            h.assertTrue(GlimService.get(p) == 0, "the purse should be empty");
            ghosts.forEach(g -> g.discard());
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** The owner touching their ghost reclaims the Glim; the ghost is gone. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void ownerReclaimsGhost(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 0, 1.5));
        EmberGhost ghost = PlayerModule.spawnGhost(p, 120);
        Vec3 far = h.absoluteVec(new Vec3(7.5, 0, 7.5));
        ghost.moveTo(far.x, far.y, far.z);
        h.runAfterDelay(3, () -> h.assertTrue(ghost.isAlive(), "not touched yet"));
        h.runAfterDelay(4, () -> p.teleportTo(far.x, far.y, far.z));
        h.runAfterDelay(8, () -> {
            h.assertTrue(!ghost.isAlive(), "touching should reclaim the ghost");
            h.assertTrue(GlimService.get(p) == 120, "expected 120 Glim back, have " + GlimService.get(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Another player standing on someone's ghost cannot take it. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void othersCannotReclaim(GameTestHelper h) {
        ServerPlayer owner = player(h, new Vec3(1.5, 0, 1.5));
        ServerPlayer other = player(h, new Vec3(7.5, 0, 7.5));
        EmberGhost ghost = PlayerModule.spawnGhost(owner, 80);
        Vec3 at = h.absoluteVec(new Vec3(7.5, 0, 7.5));
        ghost.moveTo(at.x, at.y, at.z);
        h.runAfterDelay(10, () -> {
            h.assertTrue(ghost.isAlive() && GlimService.get(other) == 0, "only the owner may reclaim");
            ghost.discard();
            TestPlayers.remove(owner);
            TestPlayers.remove(other);
            h.succeed();
        });
    }

    /** Dying again before reclaiming: the newer ghost replaces the older one, whose Glim is lost. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void newerGhostReplacesOlder(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 0, 1.5));
        Vec3 away = h.absoluteVec(new Vec3(7.5, 0, 7.5));
        EmberGhost first = PlayerModule.spawnGhost(p, 300);
        first.moveTo(away.x, away.y, away.z);
        EmberGhost second = PlayerModule.spawnGhost(p, 40);
        second.moveTo(away.x, away.y, away.z - 2);
        h.runAfterDelay(3, () -> {
            h.assertTrue(!first.isAlive(), "the older ghost should be gone");
            h.assertTrue(second.isAlive() && second.glim() == 40, "the newer ghost remains");
            second.discard();
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ Waylights

    private static BlockPos waylight(GameTestHelper h, int x, int z) {
        BlockPos rel = new BlockPos(x, 0, z);
        h.setBlock(rel, WaylightModule.WAYLIGHT.get().defaultBlockState());
        return h.absolutePos(rel);
    }

    /** Using an unlit Waylight lights it and records the discovery for that player. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "waylight")
    public static void waylightLightsOnUse(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 2.5));
        BlockPos pos = waylight(h, 4, 4);
        WaylightService.use(p, pos);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WaylightBlock.LIT), "the lantern should be lit");
        h.assertTrue(WaylightService.discovery(p).has(GlobalPos.of(h.getLevel().dimension(), pos)), "discovery recorded");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Resting: respawn point set, flask refilled, health restored. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "waylight")
    public static void restRefillsAndSetsRespawn(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 2.5));
        BlockPos pos = waylight(h, 4, 4);
        WaylightService.light(p, pos);
        FlaskService.set(p, 0, 3, 0);
        p.setHealth(10f);
        h.assertTrue(WaylightService.rest(p, pos), "rest should be allowed");
        h.assertTrue(FlaskService.get(p).charges() == 3, "flask refilled");
        h.assertTrue(p.getHealth() == p.getMaxHealth(), "health restored");
        h.assertTrue(p.getRespawnPosition() != null && p.getRespawnPosition().distSqr(pos) <= 2, "respawn set beside the Waylight: "
                + p.getRespawnPosition());
        TestPlayers.remove(p);
        h.succeed();
    }

    /** No resting with an enemy close by. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "waylight_hostile")
    public static void restRefusedWithEnemyNear(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 2.5));
        BlockPos pos = waylight(h, 4, 4);
        WaylightService.light(p, pos);
        h.spawn(EntityModule.HOLLOWED.get(), new Vec3(7.5, 0, 7.5)).setNoAi(true);
        FlaskService.set(p, 0, 3, 0);
        h.assertTrue(!WaylightService.rest(p, pos), "rest must be refused with an enemy near");
        h.assertTrue(FlaskService.get(p).charges() == 0, "nothing refilled");
        TestPlayers.remove(p);
        h.succeed();
    }

    /** Travel between two lit Waylights puts the player beside the destination. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "waylight")
    public static void travelBetweenLitWaylights(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 0, 2.5));
        BlockPos a = waylight(h, 1, 1);
        BlockPos b = waylight(h, 7, 7);
        WaylightService.light(p, a);
        WaylightService.light(p, b);
        h.assertTrue(WaylightService.travel(p, a, GlobalPos.of(h.getLevel().dimension(), b)), "travel should be allowed");
        h.assertTrue(p.blockPosition().distSqr(b) <= 3, "should arrive beside B, at " + p.blockPosition() + " vs " + b);
        TestPlayers.remove(p);
        h.succeed();
    }

    /** A Waylight the player never lit is not a destination. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "waylight")
    public static void travelRefusedToUnlitWaylight(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 0, 2.5));
        BlockPos a = waylight(h, 1, 1);
        BlockPos b = waylight(h, 7, 7);
        WaylightService.light(p, a);
        h.assertTrue(!WaylightService.travel(p, a, GlobalPos.of(h.getLevel().dimension(), b)), "unlit destination must be refused");
        h.assertTrue(p.blockPosition().distSqr(b) > 9, "the player should not have moved");
        TestPlayers.remove(p);
        h.succeed();
    }
}
