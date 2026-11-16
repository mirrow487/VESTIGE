package dev.vestige.gametest.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.entity.EntityModule;
import dev.vestige.entity.mob.TollingRam;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Tolling Ram's lessons (GDD §18.1): the Butt Charge runs straight once its windup ends, so a
 * sidestep beats it; the Toll Ring is low, so a jump clears it.
 * Butt Charge: windup 18, active 18-31 at 0.6 blocks/tick. Toll Ring: windup 16, active 16-25, reach 5.
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TollingRamTests {
    private static final String ARENA = "arena";
    private static final int CHARGE_WINDUP = 18;
    private static final int TOLL_WINDUP = 16;

    private TollingRamTests() {}

    private static ServerPlayer player(GameTestHelper h, Vec3 pos) {
        ServerPlayer p = TestPlayers.survival(h, pos, 180f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()));
        h.onEachTick(() -> {
            if (p.isAlive()) {
                PlayerCombat.serverTick(p);
            }
        });
        return p;
    }

    private static TollingRam ram(GameTestHelper h, Vec3 pos, ServerPlayer target) {
        TollingRam ram = h.spawn(EntityModule.TOLLING_RAM.get(), pos);
        ram.pacify();
        ram.setYRot(0f);
        ram.setYBodyRot(0f);
        ram.setYHeadRot(0f);
        ram.setTarget(target);
        return ram;
    }

    private static float taken(ServerPlayer p) {
        return p.getMaxHealth() - p.getHealth();
    }

    /** Standing in the line, the charge lands and knocks the player into a stagger. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void chargeHitsInItsLine(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 7.0));
        TollingRam ram = ram(h, new Vec3(4.5, 1, 1.5), p);
        h.runAfterDelay(2, () -> h.assertTrue(ram.startAction("butt_charge"), "charge should start"));
        h.runAfterDelay(2 + CHARGE_WINDUP + 14, () -> {
            h.assertTrue(taken(p) > 0f, "the charge should hit a player standing in its line");
            var a = PlayerCombat.state(p).machine().current();
            h.assertTrue(a != null && a.ref.action().equals("stagger"), "a charge hit should stagger, was " + (a == null ? "none" : a.ref.action()));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** Stepping 2.5 blocks aside once the gallop has begun: it no longer turns, and misses. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sidestepBeatsCharge(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 7.0));
        TollingRam ram = ram(h, new Vec3(4.5, 1, 1.5), p);
        h.runAfterDelay(2, () -> ram.startAction("butt_charge"));
        h.runAfterDelay(2 + CHARGE_WINDUP + 1, () -> {
            Vec3 aside = h.absoluteVec(new Vec3(7.2, 1, 7.0));
            p.teleportTo(aside.x, aside.y, aside.z);
        });
        h.runAfterDelay(2 + CHARGE_WINDUP + 16, () -> {
            h.assertTrue(taken(p) == 0f, "a sidestep after the windup should beat the charge, took " + taken(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** The Toll Ring hits a player on the ground 3 blocks away (the arena floor surface is y = 0). */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void tollRingHitsGrounded(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 6.5));
        TollingRam ram = ram(h, new Vec3(4.5, 0, 3.5), p);
        h.runAfterDelay(2, () -> h.assertTrue(ram.startAction("toll_ring"), "toll should start"));
        h.runAfterDelay(2 + TOLL_WINDUP + 11, () -> {
            h.assertTrue(taken(p) > 0f, "the ring should hit a grounded player");
            TestPlayers.remove(p);
            h.succeed();
        });
    }

    /** ... but passes under a player who jumps it (feet 1.2 blocks up while the ring goes by). */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void tollRingCanBeJumped(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 0, 6.5));
        TollingRam ram = ram(h, new Vec3(4.5, 0, 3.5), p);
        h.runAfterDelay(2, () -> ram.startAction("toll_ring"));
        h.runAfterDelay(2 + TOLL_WINDUP, () -> {
            Vec3 up = h.absoluteVec(new Vec3(4.5, 1.2, 6.5));
            p.teleportTo(up.x, up.y, up.z);
        });
        h.runAfterDelay(2 + TOLL_WINDUP + 11, () -> {
            h.assertTrue(taken(p) == 0f, "jumping should clear the ring, took " + taken(p));
            TestPlayers.remove(p);
            h.succeed();
        });
    }
}
