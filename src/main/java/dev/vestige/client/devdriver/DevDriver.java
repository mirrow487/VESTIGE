package dev.vestige.client.devdriver;

import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.client.combat.ClientCombat;
import dev.vestige.client.combat.LockOn;
import dev.vestige.client.fx.HitFx;
import dev.vestige.client.fx.TelegraphFx;
import dev.vestige.client.hud.CombatHud;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.entity.EntityModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Developer-only scripted client test (ARCHITECTURE §9.3). Enabled with
 * {@code -Dvestige.dev.driver=combat} (e.g. {@code ./gradlew runClient -Pdriver=combat}); otherwise
 * it registers nothing. Drives real inputs through the combat client, captures screenshots into
 * {@code runs/client/screenshots/} and logs {@code DRIVER CHECK} lines, then quits.
 */
public final class DevDriver {
    public static final String PROPERTY = "vestige.dev.driver";

    private interface Step {
        /** Returns true when finished. */
        boolean run();
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static int index;
    private static int stepTicks;
    private static int worldTicks;
    private static String pendingShot;
    private static int checks;

    private DevDriver() {}

    public static void initIfEnabled(IEventBus gameBus) {
        String scenario = System.getProperty(PROPERTY, "");
        if (scenario.isEmpty()) {
            return;
        }
        VestigeIds.LOG.info("DRIVER enabled: scenario '{}'", scenario);
        if (scenario.equals("anim")) {
            buildAnimScenario();
        } else if (scenario.equals("lock")) {
            buildLockScenario();
        } else if (scenario.equals("calib")) {
            buildCalibrationScenario();
        } else {
            buildCombatScenario();
        }
        gameBus.addListener(DevDriver::onTick);
        gameBus.addListener(DevDriver::onFrame);
    }

    // ------------------------------------------------------------------ step helpers

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void step(Step s) {
        STEPS.add(s);
    }

    private static void run(Runnable r) {
        step(() -> {
            r.run();
            return true;
        });
    }

    private static void wait(int ticks) {
        step(() -> stepTicks >= ticks);
    }

    private static void waitUntil(String name, int timeout, BooleanSupplier condition) {
        step(() -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (stepTicks >= timeout) {
                fail(name, "timed out after " + timeout + " ticks");
                return true;
            }
            return false;
        });
    }

    private static void shot(String name) {
        step(() -> {
            if (stepTicks == 0) {
                pendingShot = name;
            }
            return pendingShot == null;
        });
    }

    private static void command(String cmd) {
        run(() -> {
            var server = mc().getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        });
        wait(2);
    }

    private static void check(String name, BooleanSupplier ok, String detail) {
        check(name, ok, () -> detail);
    }

    /** The detail is evaluated only when the check runs (the script is built before any world exists). */
    private static void check(String name, BooleanSupplier ok, java.util.function.Supplier<String> detail) {
        run(() -> {
            if (ok.getAsBoolean()) {
                checks++;
                VestigeIds.LOG.info("DRIVER CHECK PASS {}", name);
            } else {
                fail(name, detail.get());
            }
        });
    }

    private static void fail(String name, String detail) {
        checks++;
        FAILURES.add(name);
        VestigeIds.LOG.error("DRIVER CHECK FAIL {}: {}", name, detail);
    }

    private static void press(CombatInput input, float dir) {
        run(() -> ClientCombat.press(input, dir));
    }

    private static void camera(CameraType type) {
        run(() -> mc().options.setCameraType(type));
        wait(2);
    }

    private static RunningAction mine() {
        return ClientCombat.machine().current();
    }

    /** Waits until the local action reaches a tick, then screenshots. */
    private static void shotAt(String name, int elapsed) {
        waitUntil("reach tick " + elapsed + " for " + name, 60, () -> mine() != null && mine().elapsed() >= elapsed);
        shot(name);
    }

    /** Selects hotbar slot 0 on the client and tells the server, as a real key press would. */
    private static void selectSlot0() {
        run(() -> {
            mc().player.getInventory().selected = 0;
            mc().getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
        });
        wait(2);
    }

    /** Locks on once the target exists client-side and the player is armed (no fixed delays). */
    private static void lockOnto(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<?>> type) {
        waitUntil("target visible and armed", 200, () -> nearest(type.get()) != null && ClientCombat.armed());
        run(() -> {
            if (!LockOn.locked()) {
                LockOn.toggle();
            }
        });
        waitUntil("lock engaged", 40, LockOn::locked);
    }

    private static Entity nearest(net.minecraft.world.entity.EntityType<?> type) {
        var p = mc().player;
        return p.level().getEntities(p, p.getBoundingBox().inflate(20), e -> e.getType() == type && e.isAlive()).stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceTo(p))).orElse(null);
    }

    private static float health(Entity e) {
        return e instanceof LivingEntity le ? le.getHealth() : -1f;
    }

    // ------------------------------------------------------------------ scenario

    private static float[] remembered = new float[4];

    /** Short scenario for iterating on animations: one attack of each kind from the front. */
    private static void setup() {
        waitUntil("world ready", 1200, () -> mc().player != null && mc().level != null && worldTicks > 80);
        command("time set day");
        command("weather clear");
        command("gamerule doMobSpawning false");
        command("gamerule doDaylightCycle false");
        command("kill @e[type=!player]");
        command("gamemode survival @p");
        command("execute at @p run fill ~-10 ~-1 ~-10 ~10 ~-1 ~10 minecraft:polished_andesite");
        command("execute at @p run fill ~-10 ~ ~-10 ~10 ~5 ~10 minecraft:air");
        command("execute at @p run tp @p ~ ~ ~ 0 15");
        command("clear @p");
        run(() -> arenaCenter = mc().player.position());
    }

    private static net.minecraft.world.phys.Vec3 arenaCenter;
    private static boolean attackSeen;

    /** Returns the player to the arena centre facing +Z, so spawns land inside the cleared area. */
    private static void recenter() {
        run(() -> {
            var server = mc().getSingleplayerServer();
            var c = arenaCenter;
            String cmd = String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 15", c.x, c.y, c.z);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        });
        wait(3);
    }

    /**
     * Holds single-axis test poses so rotation sign conventions can be read off screenshots
     * (front camera: the player's right hand is on the viewer's left).
     */
    private static void buildCalibrationScenario() {
        setup();
        command("difficulty peaceful");
        command("give @p vestige:keepsake_blade");
        selectSlot0();
        wait(10);
        camera(CameraType.THIRD_PERSON_FRONT);
        Object[][] poses = {
                {"d1_arm_fwd_baseline", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.RIGHT_ARM_PITCH}, new float[] {-90}},
                {"d2_arm_fwd_item_pitch_plus80", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.RIGHT_ARM_PITCH, dev.vestige.combat.anim.Channel.ITEM_PITCH}, new float[] {-90, 80}},
                {"d3_arm_fwd_item_pitch_minus80", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.RIGHT_ARM_PITCH, dev.vestige.combat.anim.Channel.ITEM_PITCH}, new float[] {-90, -80}},
                {"d4_arm_down_baseline", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.RIGHT_ARM_PITCH}, new float[] {0}},
                {"d5_torso_roll_plus30", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.TORSO_ROLL}, new float[] {30}},
                {"d6_body_roll_plus45", new dev.vestige.combat.anim.Channel[] {dev.vestige.combat.anim.Channel.BODY_ROLL}, new float[] {45}},
        };
        for (Object[] pose : poses) {
            String name = (String) pose[0];
            var channels = (dev.vestige.combat.anim.Channel[]) pose[1];
            float[] values = (float[]) pose[2];
            run(() -> {
                var a = dev.vestige.combat.anim.PoseKey.key(dev.vestige.combat.anim.Anchor.ACTIVE, 0f, dev.vestige.combat.anim.Ease.LINEAR);
                var b = dev.vestige.combat.anim.PoseKey.key(dev.vestige.combat.anim.Anchor.RECOVERY, 0f, dev.vestige.combat.anim.Ease.LINEAR);
                for (int i = 0; i < channels.length; i++) {
                    a.set(channels[i], values[i]);
                    b.set(channels[i], values[i]);
                }
                dev.vestige.client.anim.PlayerAnimations.playDebug(mc().player, java.util.List.of(a.build(), b.build()), 4, 60, 4);
            });
            wait(20);
            shot(name);
        }
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40; // screenshots are written asynchronously
        });
    }

    /** Lock-on reticle check against a stationary target. */
    private static void buildLockScenario() {
        setup();
        command("difficulty normal");
        command("give @p vestige:keepsake_blade");
        selectSlot0();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^5 {NoAI:1b}");
        camera(CameraType.FIRST_PERSON);
        lockOnto(EntityModule.HOLLOWED);
        wait(5);
        check("locked", LockOn::locked, () -> "not locked; last release: " + LockOn.lastReleaseReason);
        shot("l1_lock_reticle");
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40; // screenshots are written asynchronously
        });
    }

    private static void buildAnimScenario() {
        setup();
        command("give @p vestige:keepsake_blade");
        selectSlot0();
        wait(10);
        camera(CameraType.THIRD_PERSON_FRONT);
        press(CombatInput.LIGHT, Float.NaN);
        wait(1);
        run(() -> VestigeIds.LOG.info("DRIVER ANIM {}", dev.vestige.client.anim.PlayerAnimations.debugState(mc().player)));
        check("player animation active during attack",
                () -> dev.vestige.client.anim.PlayerAnimations.debugState(mc().player).contains("active=true"),
                () -> dev.vestige.client.anim.PlayerAnimations.debugState(mc().player));
        shotAt("a1_light1_peak", 5);
        shotAt("a2_light1_mid", 6);
        waitUntil("strike", 40, () -> mine() != null && mine().phase() == ActionPhase.RECOVERY);
        shot("a3_light1_strike");
        wait(20);
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40; // screenshots are written asynchronously
        });
    }

    private static void buildCombatScenario() {
        setup();
        command("difficulty peaceful");
        // Slow the game so screenshots land on the intended animation tick even at low software-rendering FPS.
        command("tick rate 6");
        command("give @p vestige:keepsake_blade");
        selectSlot0();
        waitUntil("holding the keepsake blade", 200, () -> mc().player.getMainHandItem().is(dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE.get()));

        // HUD and idle, first person.
        camera(CameraType.FIRST_PERSON);
        check("hud health scaled to 100", () -> Math.abs(mc().player.getMaxHealth() - 100f) < 0.01f, () -> "max health " + mc().player.getMaxHealth());
        check("armed with keepsake blade", ClientCombat::armed, () -> "held " + mc().player.getMainHandItem()
                + ", client weapon profiles " + dev.vestige.weapon.WeaponProfiles.TABLE.side(true).size());
        shot("01_fp_idle_hud");
        check("hud rendered", () -> CombatHud.rendered, "combat HUD layer did not draw");

        // First-person light attack frames.
        press(CombatInput.LIGHT, Float.NaN);
        check("light attack predicted", () -> mine() != null && mine().ref.action().equals("light_1"), "no predicted action");
        wait(2);
        run(() -> remembered[0] = mc().player.getData(dev.vestige.vitals.VitalsModule.STAMINA).current());
        check("stamina spent by the server and synced", () -> Math.abs(remembered[0] - 88f) < 0.5f, () -> "stamina " + remembered[0]);
        shotAt("02_fp_light1_windup", 4);
        shotAt("03_fp_light1_strike", 7);
        wait(20);

        // Third person, behind: the full blade combo.
        camera(CameraType.THIRD_PERSON_BACK);
        for (int i = 1; i <= 4; i++) {
            final int hit = i;
            press(CombatInput.LIGHT, Float.NaN);
            shotAt("1" + i + "_tp_back_light" + hit + "_peak", 5);
            waitUntil("light " + hit + " strike", 40, () -> mine() != null && mine().phase() == ActionPhase.RECOVERY);
            shot("1" + i + "_tp_back_light" + hit + "_strike");
            waitUntil("combo window " + hit, 40, () -> mine() == null || mine().elapsed() >= mine().def.comboOpensAt());
        }
        wait(25);

        // Third person, front: heavy charge and release.
        camera(CameraType.THIRD_PERSON_FRONT);
        press(CombatInput.HEAVY_PRESS, Float.NaN);
        waitUntil("heavy charging", 40, () -> mine() != null && mine().chargeTicks() > 6);
        shot("20_tp_front_heavy_charging");
        check("heavy holds at windup peak", () -> mine() != null && mine().elapsed() == mine().def.timing().windup(), "not paused");
        press(CombatInput.HEAVY_RELEASE, Float.NaN);
        waitUntil("heavy strike", 40, () -> mine() != null && mine().phase() == ActionPhase.RECOVERY);
        shot("21_tp_front_heavy_strike");
        wait(25);

        // Rolls.
        camera(CameraType.THIRD_PERSON_BACK);
        run(() -> remembered[1] = (float) mc().player.getZ());
        press(CombatInput.ROLL, 0f);
        shotAt("30_tp_roll_forward_a", 3);
        shotAt("31_tp_roll_forward_b", 6);
        wait(20);
        check("forward roll moved the player", () -> Math.abs(mc().player.getZ() - remembered[1]) > 2.0,
                () -> "moved " + Math.abs(mc().player.getZ() - remembered[1]));
        press(CombatInput.ROLL, 90f);
        shotAt("32_tp_roll_right", 5);
        wait(20);
        press(CombatInput.ROLL, Float.NaN);
        shotAt("33_tp_backstep", 3);
        wait(20);
        camera(CameraType.FIRST_PERSON);
        press(CombatInput.ROLL, -90f);
        shotAt("34_fp_roll_left_tilt", 5);
        wait(20);

        // Pike and maul.
        camera(CameraType.THIRD_PERSON_FRONT);
        command("clear @p");
        command("give @p vestige:ash_pike");
        wait(5);
        press(CombatInput.LIGHT, Float.NaN);
        shotAt("40_tp_pike_thrust_windup", 5);
        waitUntil("pike strike", 30, () -> mine() != null && mine().phase() == ActionPhase.RECOVERY);
        shot("41_tp_pike_thrust_strike");
        wait(20);
        command("clear @p");
        command("give @p vestige:hand_maul");
        wait(5);
        press(CombatInput.LIGHT, Float.NaN);
        shotAt("42_tp_maul_overhead_windup", 13);
        waitUntil("maul strike", 40, () -> mine() != null && mine().phase() == ActionPhase.RECOVERY);
        shot("43_tp_maul_overhead_strike");
        wait(30);

        // Enemy: Hollowed. Lock on, read its telegraph, dodge, fight.
        command("difficulty normal");
        command("kill @e[type=!player]");
        command("clear @p");
        command("give @p vestige:keepsake_blade");
        command("effect give @p minecraft:regeneration 600 1 true");
        recenter();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^5");
        wait(10);
        camera(CameraType.FIRST_PERSON);
        check("hollowed spawned with profile health 90", () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            return h != null && Math.abs(((LivingEntity) h).getMaxHealth() - 90f) < 0.01f;
        }, "hollowed missing or wrong health");
        lockOnto(EntityModule.HOLLOWED);
        check("lock-on acquired the hollowed", () -> LockOn.target().map(t -> t.getType() == EntityModule.HOLLOWED.get()).orElse(false),
                () -> "no lock target; last release: " + LockOn.lastReleaseReason);
        shot("50_tp_lock_on");
        waitUntil("hollowed starts an attack", 400, () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            RunningAction a = h == null ? null : ClientActions.of(h);
            boolean ready = a != null && a.def.telegraph().isPresent() && a.phase() == ActionPhase.WINDUP
                    && a.elapsed() >= a.def.timing().windup() - a.def.telegraph().get().lead();
            if (ready) {
                attackSeen = true;
            }
            return ready;
        });
        wait(1);
        shot("51_tp_hollowed_glint");
        check("telegraph glint shown", () -> TelegraphFx.glintsShown > 0, "no glint");
        // Dodge it: roll backward just before its active frames.
        run(() -> remembered[2] = mc().player.getHealth());
        waitUntil("hollowed about to strike", 60, () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            RunningAction a = h == null ? null : ClientActions.of(h);
            return a == null || a.elapsed() >= a.def.timing().windup() - 2;
        });
        press(CombatInput.ROLL, 180f);
        wait(25);
        check("rolled through a real attack without damage", () -> attackSeen && mc().player.getHealth() >= remembered[2] - 0.01f,
                () -> "attackSeen=" + attackSeen + " health " + remembered[2] + " -> " + mc().player.getHealth());
        // Fight back.
        run(() -> remembered[3] = health(nearest(EntityModule.HOLLOWED.get())));
        for (int i = 0; i < 6; i++) {
            press(CombatInput.LIGHT, Float.NaN);
            wait(14);
        }
        shot("52_tp_hit_feedback");
        check("hits registered on the hollowed", () -> HitFx.hitsSeen > 0, "no hit feedback received");
        check("hollowed took damage", () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            return h == null || health(h) < remembered[3];
        }, "health unchanged");
        command("kill @e[type=vestige:hollowed]");
        run(LockOn::release);

        // Brute: ground slam decal, seen from behind and to the side.
        recenter();
        command("execute at @p rotated 0 0 run summon vestige:hollowed_brute ^-1.5 ^ ^3.5");
        wait(10);
        camera(CameraType.THIRD_PERSON_BACK);
        waitUntil("brute slam windup", 600, () -> {
            Entity b = nearest(EntityModule.HOLLOWED_BRUTE.get());
            RunningAction a = b == null ? null : ClientActions.of(b);
            return a != null && a.ref.action().equals("overhead_slam") && a.elapsed() >= 14;
        });
        shot("60_tp_brute_slam_decal");
        command("kill @e[type=vestige:hollowed_brute]");
        check("kill command removed the brute", () -> nearest(EntityModule.HOLLOWED_BRUTE.get()) == null, "brute still alive after /kill");
        // A Hollowed off to the side, so its windup pose and glint are visible past the player.
        recenter();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^-2 ^ ^3");
        waitUntil("side hollowed windup", 600, () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            RunningAction a = h == null ? null : ClientActions.of(h);
            return a != null && a.def.telegraph().isPresent() && a.phase() == ActionPhase.WINDUP && a.elapsed() >= a.def.timing().windup() - 3;
        });
        shot("61_tp_hollowed_windup_side");
        waitUntil("side hollowed strike", 60, () -> {
            Entity h = nearest(EntityModule.HOLLOWED.get());
            RunningAction a = h == null ? null : ClientActions.of(h);
            return a == null || a.phase() == ActionPhase.RECOVERY;
        });
        shot("62_tp_hollowed_strike_side");
        command("kill @e[type=vestige:hollowed]");
        command("tick rate 20");

        // Config screen (automated UI check requested in Milestone 2 review).
        run(() -> mc().setScreen(new ConfigurationScreen(ModList.get().getModContainerById(VestigeIds.MOD_ID).orElseThrow(), null)));
        wait(10);
        shot("70_config_screen");
        // In singleplayer NeoForge opens the only config section ("World Settings") directly.
        check("config screen opened on VESTIGE world settings", () -> mc().screen != null
                && mc().screen.getTitle().getString().contains("World Settings"), () -> "screen " + (mc().screen == null ? "none" : mc().screen.getTitle().getString()));
        run(() -> {
            Button edit = findButton(mc().screen, "Edit");
            if (edit == null) {
                fail("config edit button", "no 'Edit...' button for The Pale section");
            } else {
                edit.onPress();
            }
        });
        wait(10);
        shot("71_config_pale_section");
        check("pale section lists border options", () -> mc().screen != null && mc().screen.getTitle().getString().contains("Pale"),
                () -> "screen " + (mc().screen == null ? "none" : mc().screen.getTitle().getString()));
        run(() -> mc().setScreen(null));

        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40; // screenshots are written asynchronously
        });
    }

    /** Depth-first search for the first button whose label contains {@code text}. */
    private static Button findButton(net.minecraft.client.gui.components.events.ContainerEventHandler parent, String text) {
        for (var child : parent.children()) {
            if (child instanceof Button b && b.getMessage().getString().contains(text)) {
                return b;
            }
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested) {
                Button found = findButton(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ event plumbing

    private static int quitSoon = -1;

    private static void onTick(ClientTickEvent.Post event) {
        if (mc().player != null) {
            worldTicks++;
        }
        if (quitSoon > 0 && --quitSoon == 0) {
            mc().stop();
            return;
        }
        if (index >= STEPS.size() || pendingShot != null) {
            return;
        }
        if (STEPS.get(index).run()) {
            index++;
            stepTicks = 0;
            // Run consecutive instant steps in the same tick.
            while (index < STEPS.size() && pendingShot == null && STEPS.get(index).run()) {
                index++;
            }
        } else {
            stepTicks++;
        }
    }

    private static void onFrame(RenderFrameEvent.Post event) {
        if (pendingShot != null) {
            String name = pendingShot;
            Screenshot.grab(mc().gameDirectory, name + ".png", mc().getMainRenderTarget(), msg -> {});
            VestigeIds.LOG.info("DRIVER SHOT {}", name);
            pendingShot = null;
        }
    }
}
