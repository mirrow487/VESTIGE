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
        } else if (scenario.equals("depth")) {
            buildDepthScenario();
        } else if (scenario.equals("mp_a")) {
            buildMultiplayerA();
        } else if (scenario.equals("mp_b")) {
            buildMultiplayerB();
        } else if (scenario.equals("equipment")) {
            buildEquipmentScenario();
        } else if (scenario.equals("rpg")) {
            buildRpgScenario();
        } else if (scenario.equals("arsenal")) {
            buildArsenalScenario();
        } else if (scenario.equals("ram")) {
            buildRamScenario();
        } else if (scenario.equals("items")) {
            buildItemsScenario();
        } else if (scenario.equals("models")) {
            buildModelsScenario();
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
                var p = mc().player;
                fail(name, "timed out after " + timeout + " ticks [main=" + (p == null ? "?" : p.getMainHandItem())
                        + " slot0=" + (p == null ? "?" : p.getInventory().getItem(0)) + " selected=" + (p == null ? "?" : p.getInventory().selected)
                        + " gamemode=" + (mc().gameMode == null ? "?" : mc().gameMode.getPlayerMode()) + "]");
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

    /** Runs code against the integrated server's copy of the local player (server thread). */
    private static void onServerPlayer(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        run(() -> {
            var server = mc().getSingleplayerServer();
            var uuid = mc().player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) {
                    action.accept(sp);
                }
            });
        });
        wait(2);
    }

    private static volatile boolean serverGuardAfterRelease;

    private static RunningAction actionOf(net.minecraft.world.entity.EntityType<?> type) {
        Entity e = nearest(type);
        return e == null ? null : ClientActions.of(e);
    }

    /**
     * Combat depth in the real client (M4 stage 1): guards, a buckler parry timed off a live
     * Hollowed's glint, the riposte that follows, a backstab, the three weapon arts and the
     * Resonance HUD.
     */
    private static void buildDepthScenario() {
        setup();
        command("difficulty peaceful");
        command("tick rate 6");
        command("give @p vestige:keepsake_blade");
        selectSlot0();
        waitUntil("holding the blade", 200, () -> mc().player.getMainHandItem().is(dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE.get()));

        // Guards (third person, front).
        camera(CameraType.THIRD_PERSON_FRONT);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        waitUntil("weapon guard raised", 40, () -> mine() != null && mine().guarding());
        shot("d01_guard_weapon");
        press(CombatInput.GUARD_RELEASE, Float.NaN);
        wait(12);
        onServerPlayer(sp -> {
            var a = dev.vestige.combat.player.PlayerCombat.state(sp).machine().current();
            serverGuardAfterRelease = a != null && a.guarding();
        });
        check("server lowered the guard after release", () -> !serverGuardAfterRelease, "server still guarding");
        command("item replace entity @p weapon.offhand with minecraft:shield");
        wait(4);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        waitUntil("shield guard raised", 40, () -> mine() != null && mine().guarding() && mine().ref.action().equals("guard_shield"));
        shot("d02_guard_shield");
        press(CombatInput.GUARD_RELEASE, Float.NaN);
        wait(12);
        command("item replace entity @p weapon.offhand with vestige:plank_buckler");
        wait(4);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        check("buckler press is a parry", () -> mine() != null && mine().ref.action().equals("parry_buckler"),
                () -> "action " + (mine() == null ? "none" : mine().ref.action()));
        shotAt("d03_parry_snap", 4);
        wait(20);

        // Live parry and riposte against an AI Hollowed.
        command("difficulty normal");
        recenter();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^3");
        camera(CameraType.THIRD_PERSON_BACK);
        lockOnto(EntityModule.HOLLOWED);
        waitUntil("hollowed winds up a parriable attack", 600, () -> {
            RunningAction a = actionOf(EntityModule.HOLLOWED.get());
            return a != null && a.def.parriable() && a.def.hit().isPresent() && a.phase() == ActionPhase.WINDUP
                    && a.elapsed() >= a.def.timing().windup() - 3;
        });
        press(CombatInput.GUARD_PRESS, Float.NaN);
        waitUntil("parry lands", 30, () -> {
            RunningAction a = actionOf(EntityModule.HOLLOWED.get());
            return a != null && a.ref.action().equals("parried");
        });
        shot("d10_parry_impact");
        check("live parry made the hollowed reel", () -> {
            RunningAction a = actionOf(EntityModule.HOLLOWED.get());
            return a != null && a.ref.action().equals("parried");
        }, "hollowed not parried");
        check("parry earned resonance", () -> mc().player.getData(dev.vestige.combat.CombatModule.RESONANCE).value() >= 20f,
                () -> "resonance " + mc().player.getData(dev.vestige.combat.CombatModule.RESONANCE).value());
        waitUntil("riposte prompt available", 30, () -> ClientCombat.riposteTarget(mc().player).isPresent());
        shot("d11_riposte_prompt");
        press(CombatInput.LIGHT, Float.NaN);
        // The riposte starts as soon as the parry's impact freeze ends (pressed early, it is buffered).
        waitUntil("light became a riposte", 12, () -> mine() != null && mine().ref.action().equals("riposte"));
        shotAt("d12_riposte_thrust", 9);
        shotAt("d13_riposte_twist", 15);
        wait(30);
        check("riposte killed the hollowed", () -> nearest(EntityModule.HOLLOWED.get()) == null, "hollowed survived the riposte");
        run(LockOn::release);

        // Backstab: a Hollowed (no AI) facing away, the player just behind it.
        recenter();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^1.5 {NoAI:1b,Rotation:[0f,0f]}");
        waitUntil("backstab target visible", 100, () -> nearest(EntityModule.HOLLOWED.get()) != null);
        wait(5);
        press(CombatInput.LIGHT, Float.NaN);
        check("backstab from behind", () -> mine() != null && mine().ref.action().equals("riposte"),
                () -> "action " + (mine() == null ? "none" : mine().ref.action()));
        shotAt("d20_backstab", 9);
        wait(30);
        command("kill @e[type=vestige:hollowed]");

        // Weapon arts at full Resonance (Crescendo), third person front.
        command("difficulty peaceful");
        camera(CameraType.THIRD_PERSON_FRONT);
        onServerPlayer(sp -> dev.vestige.combat.resonance.ResonanceService.set(sp, 100f));
        wait(4);
        shot("d30_hud_crescendo");
        press(CombatInput.ART, Float.NaN);
        shotAt("d31_blade_riposte_stance", 6);
        wait(30);
        command("clear @p");
        command("give @p vestige:ash_pike");
        selectSlot0();
        onServerPlayer(sp -> dev.vestige.combat.resonance.ResonanceService.set(sp, 100f));
        wait(4);
        press(CombatInput.ART, Float.NaN);
        shotAt("d32_pike_charge_windup", 6);
        shotAt("d33_pike_charge_lunge", 9);
        wait(30);
        command("clear @p");
        command("give @p vestige:hand_maul");
        selectSlot0();
        onServerPlayer(sp -> dev.vestige.combat.resonance.ResonanceService.set(sp, 100f));
        wait(4);
        press(CombatInput.ART, Float.NaN);
        shotAt("d34_maul_tremor_windup", 15);
        shotAt("d35_maul_tremor_impact", 19);
        check("art spent resonance", () -> mc().player.getData(dev.vestige.combat.CombatModule.RESONANCE).value() < 100f,
                () -> "resonance " + mc().player.getData(dev.vestige.combat.CombatModule.RESONANCE).value());
        wait(30);
        command("tick rate 20");
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    /** Views the scene from a marker armor stand placed beside the player (the player is then rendered). */
    private static void sideCamera(double dx, double dz, float yaw) {
        camAt(dx, 1.15, dz, yaw, 8f);
    }

    /** A camera at an offset from the player (marker armor stand), looking along yaw/pitch. */
    private static void camAt(double dx, double dy, double dz, float yaw, float pitch) {
        command("kill @e[type=armor_stand,tag=cam]");
        command(String.format(java.util.Locale.ROOT,
                "execute at @p run summon armor_stand ~%.2f ~%.2f ~%.2f {Marker:1b,Invisible:1b,NoGravity:1b,Tags:[\"cam\"],Rotation:[%.1ff,%.1ff]}",
                dx, dy, dz, yaw, pitch));
        waitUntil("camera stand visible", 60, () -> nearest(net.minecraft.world.entity.EntityType.ARMOR_STAND) != null);
        run(() -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().setCameraEntity(nearest(net.minecraft.world.entity.EntityType.ARMOR_STAND));
        });
        wait(3);
    }

    private static void playerCamera() {
        run(() -> mc().setCameraEntity(mc().player));
        command("kill @e[type=armor_stand,tag=cam]");
    }

    private static boolean hasModel(net.minecraft.world.item.Item item) {
        var model = mc().getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), mc().level, mc().player, 0);
        return model != mc().getModelManager().getMissingModel();
    }

    /**
     * 3D weapon item models in the real client: each weapon in hand from the front, the side and first
     * person, mid-swing, the buckler raised, items on the ground and the flat inventory icons.
     */
    private static void buildItemsScenario() {
        setup();
        command("difficulty peaceful");
        for (var item : java.util.List.<java.util.function.Supplier<net.minecraft.world.item.Item>>of(
                dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE::get, dev.vestige.weapon.WeaponModule.ASH_PIKE::get,
                dev.vestige.weapon.WeaponModule.HAND_MAUL::get, dev.vestige.weapon.WeaponModule.PLANK_BUCKLER::get)) {
            check("item model present", () -> hasModel(item.get()), () -> "missing model for " + item.get());
        }
        command("tick rate 6");
        command("item replace entity @p weapon.offhand with vestige:plank_buckler");
        String[][] weapons = {{"keepsake_blade", "blade"}, {"ash_pike", "pike"}, {"hand_maul", "maul"}};
        for (String[] w : weapons) {
            command("item replace entity @p weapon.mainhand with vestige:" + w[0]);
            waitUntil("armed with " + w[0], 60, ClientCombat::armed);
            camera(CameraType.THIRD_PERSON_FRONT);
            shot("i_" + w[1] + "_front");
            sideCamera(-3.6, 0.3, -90f);
            shot("i_" + w[1] + "_side");
            press(CombatInput.LIGHT, Float.NaN);
            waitUntil("swing started", 20, () -> mine() != null);
            run(() -> {
                var a = mine();
                remembered[0] = a == null ? 0 : a.def.timing().windup();
            });
            step(() -> mine() == null || mine().elapsed() >= remembered[0] - 1);
            shot("i_" + w[1] + "_side_windup");
            step(() -> mine() == null || mine().elapsed() >= remembered[0] + 1);
            shot("i_" + w[1] + "_side_strike");
            wait(30);
            playerCamera();
            camera(CameraType.FIRST_PERSON);
            shot("i_" + w[1] + "_firstperson");
        }
        // Weapon arts from the side (Resonance filled first).
        String[][] arts = {{"keepsake_blade", "blade", "6", "12"}, {"ash_pike", "pike", "10", "14"}, {"hand_maul", "maul", "15", "19"}};
        for (String[] a : arts) {
            command("item replace entity @p weapon.mainhand with vestige:" + a[0]);
            waitUntil("armed with " + a[0], 60, ClientCombat::armed);
            onServerPlayer(sp -> dev.vestige.combat.resonance.ResonanceService.set(sp, 100f));
            wait(4);
            sideCamera(-3.6, 0.3, -90f);
            press(CombatInput.ART, Float.NaN);
            shotAt("i_art_" + a[1] + "_side_1", Integer.parseInt(a[2]));
            shotAt("i_art_" + a[1] + "_side_2", Integer.parseInt(a[3]));
            press(CombatInput.GUARD_RELEASE, Float.NaN);
            wait(40);
            playerCamera();
        }
        // Buckler: the parry snap from the side and front, and in first person.
        command("item replace entity @p weapon.mainhand with vestige:keepsake_blade");
        wait(4);
        sideCamera(3.6, 0.3, 90f);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        shotAt("i_buckler_parry_side_1tuck", 2);
        shotAt("i_buckler_parry_side_2snap", 4);
        shotAt("i_buckler_parry_side_3swat", 8);
        press(CombatInput.GUARD_RELEASE, Float.NaN);
        wait(30);
        playerCamera();
        camera(CameraType.THIRD_PERSON_FRONT);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        shotAt("i_buckler_parry_front_1tuck", 2);
        shotAt("i_buckler_parry_front_2snap", 4);
        shotAt("i_buckler_parry_front_3swat", 8);
        press(CombatInput.GUARD_RELEASE, Float.NaN);
        wait(30);
        camera(CameraType.FIRST_PERSON);
        press(CombatInput.GUARD_PRESS, Float.NaN);
        shotAt("i_buckler_parry_firstperson_2snap", 4);
        shotAt("i_buckler_parry_firstperson_3swat", 8);
        press(CombatInput.GUARD_RELEASE, Float.NaN);
        wait(30);
        // On the ground.
        command("tick rate 20");
        command("execute at @p run tp @p ~ ~ ~ 0 50");
        int i = 0;
        for (String id : new String[] {"keepsake_blade", "ash_pike", "hand_maul", "plank_buckler"}) {
            command(String.format(java.util.Locale.ROOT,
                    "execute at @p run summon item ~%.1f ~ ~2.2 {Item:{id:\"vestige:%s\",count:1},PickupDelay:32767s,Age:-32768s}", -1.5 + i++, id));
        }
        wait(30);
        shot("i_ground");
        command("kill @e[type=item]");
        // Inventory icons stay flat sprites.
        command("give @p vestige:ash_pike");
        command("give @p vestige:hand_maul");
        command("give @p vestige:plank_buckler");
        run(() -> mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)));
        wait(10);
        shot("i_inventory");
        run(() -> mc().setScreen(null));
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    private static int count(net.minecraft.world.entity.EntityType<?> type) {
        var p = mc().player;
        return p.level().getEntities(p, p.getBoundingBox().inflate(24), e -> e.getType() == type).size();
    }

    private static int ringsBefore;
    private static int wakesBefore;
    private static float dummyHealthBefore;

    /**
     * M4 stage 2 weapon classes in the real client: every class from the side at its windup peak and
     * strike, its art, first person, the drawn bow, the Bell's rings, the Censer's cloud, the
     * Greatblade's wake, the Fang's Quickstep, and the kite and tower shields.
     */
    private static void buildArsenalScenario() {
        setup();
        command("difficulty normal");
        command("tick rate 6");
        command("item replace entity @p inventory.0 with minecraft:arrow 64");
        // {item, class, light windup, art windup}
        String[][] weapons = {{"taper_fang", "fang", "3", "0"}, {"wickiron_greatblade", "greatblade", "12", "18"},
                {"chandlers_censer", "censer", "8", "10"}, {"hunting_bow", "bow", "8", "16"}, {"handbell", "bell", "7", "12"}};
        for (String[] w : weapons) {
            int lw = Integer.parseInt(w[2]);
            int aw = Integer.parseInt(w[3]);
            command("item replace entity @p weapon.mainhand with vestige:" + w[0]);
            waitUntil("armed with " + w[0], 60, ClientCombat::armed);
            sideCamera(-3.6, 0.3, -90f);
            shot("a_" + w[1] + "_0_idle_side");
            press(CombatInput.LIGHT, Float.NaN);
            shotAt("a_" + w[1] + "_1_light_peak", Math.max(0, lw - 1));
            shotAt("a_" + w[1] + "_2_light_strike", lw + 1);
            wait(40);
            onServerPlayer(sp -> dev.vestige.combat.resonance.ResonanceService.set(sp, 100f));
            wait(4);
            run(() -> {
                ringsBefore = dev.vestige.client.fx.ActionFx.ringsShown;
                wakesBefore = dev.vestige.client.fx.ActionFx.wakesShown;
            });
            if (w[1].equals("bell") || w[1].equals("censer") || w[1].equals("greatblade")) {
                camAt(-3.4, 3.4, -2.6, -52f, 40f);  // high and behind-right, to see the ground effects
            }
            press(CombatInput.ART, w[1].equals("fang") ? 90f : Float.NaN);
            shotAt("a_" + w[1] + "_3_art_peak", Math.max(1, aw - 1));
            shotAt("a_" + w[1] + "_4_art_active", aw + 3);
            if (w[1].equals("censer")) {
                waitUntil("incense cloud exists", 60, () -> count(dev.vestige.combat.CombatModule.INCENSE_CLOUD.get()) > 0);
                wait(20);
                shot("a_censer_5_incense_cloud");
                check("incense cloud spawned", () -> count(dev.vestige.combat.CombatModule.INCENSE_CLOUD.get()) > 0, "no cloud");
            }
            if (w[1].equals("bell")) {
                check("toll ring drawn", () -> dev.vestige.client.fx.ActionFx.ringsShown > ringsBefore, "no ring shown");
            }
            if (w[1].equals("greatblade")) {
                check("cleaving wake drawn", () -> dev.vestige.client.fx.ActionFx.wakesShown > wakesBefore, "no wake shown");
            }
            wait(60);
            playerCamera();
            camera(CameraType.FIRST_PERSON);
            press(CombatInput.LIGHT, Float.NaN);
            shotAt("a_" + w[1] + "_6_firstperson", lw + 1);
            wait(40);
            recenter();
        }
        // Bell heavy toll (charged) from above: the expanding ring.
        command("item replace entity @p weapon.mainhand with vestige:handbell");
        wait(4);
        camAt(-4.5, 4.5, -3.5, -52f, 42f);
        run(() -> ringsBefore = dev.vestige.client.fx.ActionFx.ringsShown);
        press(CombatInput.HEAVY_PRESS, Float.NaN);
        wait(4);
        press(CombatInput.HEAVY_RELEASE, Float.NaN);
        shotAt("a_bell_7_toll_ring", 17);
        check("heavy toll ring drawn", () -> dev.vestige.client.fx.ActionFx.ringsShown > ringsBefore, "no ring shown");
        wait(40);
        // Bow: hold a full draw (drawn model), side and first person, then loose at a dummy 9 blocks out.
        playerCamera();
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^9 {NoAI:1b,Rotation:[180f,0f]}");
        command("item replace entity @p weapon.mainhand with vestige:hunting_bow");
        waitUntil("bow armed and dummy visible", 80, () -> ClientCombat.armed() && nearest(EntityModule.HOLLOWED.get()) != null);
        run(() -> dummyHealthBefore = health(nearest(EntityModule.HOLLOWED.get())));
        lockOnto(EntityModule.HOLLOWED);
        sideCamera(-3.6, 0.3, -90f);
        press(CombatInput.HEAVY_PRESS, Float.NaN);
        waitUntil("bow fully drawn", 60, () -> mine() != null && mine().holding());
        wait(4);
        check("drawn bow model engaged", () -> dev.vestige.client.VestigeClient.drawProgress(mc().player) >= 0.5f,
                () -> "draw " + dev.vestige.client.VestigeClient.drawProgress(mc().player));
        shot("a_bow_8_full_draw_side");
        playerCamera();
        camera(CameraType.FIRST_PERSON);
        shot("a_bow_9_full_draw_firstperson");
        press(CombatInput.HEAVY_RELEASE, Float.NaN);
        waitUntil("arrow loosed", 40, () -> count(dev.vestige.combat.CombatModule.COMBAT_ARROW.get()) > 0);
        shot("a_bow_10_arrow_flight");
        waitUntil("arrow hit the dummy", 80, () -> health(nearest(EntityModule.HOLLOWED.get())) < dummyHealthBefore);
        check("aimed shot damaged the dummy", () -> health(nearest(EntityModule.HOLLOWED.get())) < dummyHealthBefore,
                () -> "health " + health(nearest(EntityModule.HOLLOWED.get())));
        command("kill @e[type=vestige:hollowed]");
        // Shields: kite and tower guards.
        command("item replace entity @p weapon.mainhand with vestige:keepsake_blade");
        for (String shield : new String[] {"iron_kite_shield", "timber_bulwark"}) {
            command("item replace entity @p weapon.offhand with vestige:" + shield);
            wait(4);
            camera(CameraType.THIRD_PERSON_FRONT);
            shot("a_" + shield + "_0_idle_front");
            press(CombatInput.GUARD_PRESS, Float.NaN);
            waitUntil("guard raised", 40, () -> mine() != null && mine().guarding());
            wait(4);
            shot("a_" + shield + "_1_guard_front");
            sideCamera(3.6, 0.3, 90f);
            shot("a_" + shield + "_2_guard_side");
            playerCamera();
            camera(CameraType.FIRST_PERSON);
            shot("a_" + shield + "_3_guard_firstperson");
            press(CombatInput.GUARD_RELEASE, Float.NaN);
            wait(10);
        }
        command("tick rate 20");
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    /**
     * The Tolling Ram: model and frozen poses from the side, its death, then a live fight where the
     * player reads the charge telegraph and rolls sideways out of the line (its lesson, GDD §18.1).
     */
    private static void buildRamScenario() {
        setup();
        command("difficulty normal");
        command("time set 6000");
        command("execute at @p rotated 0 0 run summon vestige:tolling_ram ^ ^ ^5 {NoAI:1b,Rotation:[180f,0f]}");
        run(() -> mc().options.hideGui = true);
        camera(CameraType.FIRST_PERSON);
        waitUntil("ram visible", 100, () -> nearest(EntityModule.TOLLING_RAM.get()) != null);
        wait(30);
        shot("r_front_idle");
        command("execute as @e[type=vestige:tolling_ram] at @s run tp @s ~ ~ ~ 90 0");
        wait(10);
        shot("r_side_idle");
        String[][] poses = {{"butt_charge", "3", "1_scrape"}, {"butt_charge", "18", "2_charge_peak"}, {"butt_charge", "21", "3_gallop_a"},
                {"butt_charge", "24", "4_gallop_b"}, {"butt_charge", "35", "5_skid"}, {"butt_charge", "42", "6_head_shake"},
                {"toll_ring", "15", "7_toll_rear"}, {"toll_ring", "17", "8_toll_slam"}, {"toll_ring", "20", "9_toll_ring"},
                {"headbutt", "9", "10_headbutt_rear"}, {"headbutt", "13", "11_headbutt_strike"}, {"stagger", "14", "12_stagger"},
                {"parried", "6", "13_parried"}, {"riposted", "25", "14_riposted"}};
        for (String[] pose : poses) {
            freeze(EntityModule.TOLLING_RAM, "vestige:tolling_ram#" + pose[0], Integer.parseInt(pose[1]));
            shot("r_side_" + pose[2]);
            unfreeze();
        }
        command("execute as @e[type=vestige:tolling_ram] at @s run tp @s ~ ~ ~ 140 0");
        wait(6);
        freeze(EntityModule.TOLLING_RAM, "vestige:tolling_ram#butt_charge", 18);
        shot("r_three_quarter_charge_peak");
        unfreeze();
        command("kill @e[type=vestige:tolling_ram]");
        wait(14);
        shot("r_side_15_death_mid");
        wait(16);
        shot("r_side_16_death_end");
        run(() -> mc().options.hideGui = false);
        // Live: the ram charges; the player reads the telegraph and rolls sideways.
        recenter();
        command("kill @e[type=item]");
        command("clear @p");
        selectSlot0();
        command("item replace entity @p weapon.mainhand with vestige:keepsake_blade");
        command("execute at @p rotated 0 0 run summon vestige:tolling_ram ^ ^ ^8 {Rotation:[180f,0f]}");
        camera(CameraType.THIRD_PERSON_BACK);
        lockOnto(EntityModule.TOLLING_RAM);
        command("tick rate 8");
        waitUntil("ram starts its charge", 400, () -> {
            RunningAction a = actionOf(EntityModule.TOLLING_RAM.get());
            return a != null && a.ref.action().equals("butt_charge") && a.elapsed() >= 12;
        });
        run(() -> remembered[0] = mc().player.getHealth());
        shot("r_live_1_charge_telegraph");
        waitUntil("charge about to run", 20, () -> {
            RunningAction a = actionOf(EntityModule.TOLLING_RAM.get());
            return a == null || a.elapsed() >= 17;
        });
        press(CombatInput.ROLL, 90f);
        wait(3);
        shot("r_live_2_roll_aside");
        waitUntil("charge over", 60, () -> {
            RunningAction a = actionOf(EntityModule.TOLLING_RAM.get());
            return a == null || !a.ref.action().equals("butt_charge") || a.elapsed() >= 33;
        });
        shot("r_live_3_charge_passed");
        check("rolled sideways out of the charge", () -> mc().player.getHealth() >= remembered[0],
                () -> "took " + (remembered[0] - mc().player.getHealth()));
        command("tick rate 20");
        command("kill @e[type=vestige:tolling_ram]");
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    private static net.minecraft.core.BlockPos waylightA;
    private static net.minecraft.core.BlockPos waylightB;
    private static long glimBefore;

    /** Right-clicks a block as a real player would (use-item-on packet). */
    private static void useBlock(java.util.function.Supplier<net.minecraft.core.BlockPos> pos) {
        run(() -> {
            var p = pos.get();
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p).add(0, 0.4, 0),
                    net.minecraft.core.Direction.UP, p, false);
            mc().gameMode.useItemOn(mc().player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        });
        wait(4);
    }

    private static dev.vestige.player.Flask flask() {
        return mc().player.getData(dev.vestige.player.PlayerModule.FLASK);
    }

    /**
     * Core RPG systems in the real client: lighting Waylights (title card), the Waylight menu, travel,
     * rest (kneel, refill), the Wick Flask (drink pose, flask in hand, HUD tile), Glim from a kill (HUD
     * popup), death leaving an Ember-Ghost, respawning at the rested Waylight and reclaiming the Glim.
     */
    private static void buildRpgScenario() {
        setup();
        command("difficulty normal");
        command("gamerule doImmediateRespawn true");
        run(() -> {
            var c = arenaCenter;
            waylightA = net.minecraft.core.BlockPos.containing(c.x + 2, c.y, c.z + 3);
            waylightB = net.minecraft.core.BlockPos.containing(c.x - 6, c.y, c.z + 8);
        });
        run(() -> {
            var a = waylightA;
            var b = waylightB;
            var server = mc().getSingleplayerServer();
            server.execute(() -> {
                var lvl = server.overworld();
                lvl.setBlock(a, dev.vestige.waylight.WaylightModule.WAYLIGHT.get().defaultBlockState(), 3);
                lvl.setBlock(b, dev.vestige.waylight.WaylightModule.WAYLIGHT.get().defaultBlockState()
                        .setValue(dev.vestige.waylight.WaylightBlock.FACING, net.minecraft.core.Direction.EAST), 3);
            });
        });
        wait(10);
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("w_00_unlit");
        // Light B first (walk over), then A: the title card.
        run(() -> {
            var b = waylightB;
            var server = mc().getSingleplayerServer();
            String cmd = String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5", b.getX() + 1, b.getY(), b.getZ());
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        });
        wait(6);
        useBlock(() -> waylightB);
        recenter();
        camera(CameraType.FIRST_PERSON);
        command("execute at @p run tp @p ~ ~ ~ -35 25");
        useBlock(() -> waylightA);
        wait(6);
        shot("w_01_waylight_kindled_title");
        check("waylight lit", () -> mc().level.getBlockState(waylightA).getValue(dev.vestige.waylight.WaylightBlock.LIT), "not lit");
        wait(60);
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("w_02_lit_waylight");
        // Menu, travel list.
        camera(CameraType.FIRST_PERSON);
        useBlock(() -> waylightA);
        waitUntil("waylight menu opens", 40, () -> dev.vestige.client.rpg.WaylightScreen.current != null);
        shot("w_03_menu");
        run(() -> dev.vestige.client.rpg.WaylightScreen.current.showTravel());
        wait(4);
        shot("w_04_travel_list");
        check("travel lists the other waylight", () -> dev.vestige.client.rpg.WaylightScreen.current != null
                && dev.vestige.client.rpg.WaylightScreen.current.data().destinations().size() == 1, "no destination");
        run(() -> dev.vestige.client.rpg.WaylightScreen.current.travelTo(0));
        waitUntil("arrived at the other waylight", 60, () -> mc().player.blockPosition().distSqr(waylightB) <= 3);
        check("travelled to the other waylight", () -> mc().player.blockPosition().distSqr(waylightB) <= 3,
                () -> "at " + mc().player.blockPosition() + ", B " + waylightB);
        // Rest at B: kneel, refill.
        onServerPlayer(sp -> dev.vestige.player.FlaskService.set(sp, 0, 3, 0));
        useBlock(() -> waylightB);
        waitUntil("waylight menu opens again", 40, () -> dev.vestige.client.rpg.WaylightScreen.current != null);
        run(() -> {
            var s = dev.vestige.client.rpg.WaylightScreen.current;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.vestige.waylight.WaylightPayloads.Act(s.data().pos(), false,
                    net.minecraft.core.GlobalPos.of(mc().level.dimension(), s.data().pos())));
            s.onClose();
        });
        camera(CameraType.THIRD_PERSON_FRONT);
        waitUntil("resting", 40, () -> mine() != null && mine().ref.action().equals("rest"));
        wait(6);
        shot("w_05_rest_kneel");
        sideCamera(0.4, 3.4, 180f);
        shot("w_06_rest_kneel_side");
        playerCamera();
        waitUntil("flask refilled", 40, () -> flask().charges() == 3);
        check("rest refilled the flask", () -> flask().charges() == 3, () -> "charges " + flask().charges());
        wait(30);
        // Wick Flask: drink at low health.
        recenter();
        command("tick rate 8");
        onServerPlayer(sp -> sp.setHealth(25f));
        wait(4);
        run(() -> remembered[0] = mc().player.getHealth());
        press(CombatInput.FLASK, Float.NaN);
        shotAt("f_01_drink_raise_front", 7);
        sideCamera(-3.4, 0.3, -90f);
        shotAt("f_02_drink_side", 18);
        playerCamera();
        camera(CameraType.FIRST_PERSON);
        waitUntil("drink over", 60, () -> mine() == null);
        shot("f_03_after_drink_hud");
        check("flask healed", () -> mc().player.getHealth() > remembered[0] + 30f, () -> remembered[0] + " -> " + mc().player.getHealth());
        check("flask used a charge", () -> flask().charges() == 2, () -> "charges " + flask().charges());
        command("tick rate 20");
        // Glim from a kill.
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^ ^ ^3 {NoAI:1b,Rotation:[180f,0f]}");
        waitUntil("hollowed visible", 60, () -> nearest(EntityModule.HOLLOWED.get()) != null);
        run(() -> glimBefore = mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount());
        onServerPlayer(sp -> sp.level().getEntitiesOfClass(dev.vestige.entity.mob.Hollowed.class, sp.getBoundingBox().inflate(8))
                .forEach(m -> m.hurt(sp.damageSources().playerAttack(sp), 100_000f)));
        waitUntil("glim awarded", 40, () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount() > glimBefore);
        wait(4);
        shot("g_01_glim_popup");
        check("kill gave glim", () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount() == glimBefore + 32,
                () -> "glim " + mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount());
        wait(40);
        // Death: the Ember-Ghost; respawn at the rested Waylight (B); come back and reclaim.
        onServerPlayer(sp -> dev.vestige.player.GlimService.set(sp, 500));
        recenter();
        run(() -> remembered[1] = (float) mc().player.getX());
        run(() -> remembered[2] = (float) mc().player.getZ());
        command("kill @p");
        waitUntil("respawned", 100, () -> mc().player != null && mc().player.isAlive() && mc().screen == null && worldTicks > 0);
        wait(20);
        check("respawned at the rested waylight", () -> mc().player.blockPosition().distSqr(waylightB) <= 4,
                () -> "at " + mc().player.blockPosition());
        check("glim left behind", () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount() == 0,
                () -> "glim " + mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount());
        run(() -> {
            var server = mc().getSingleplayerServer();
            String cmd = String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 20", remembered[1], arenaCenter.y, remembered[2] - 4.0);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        });
        waitUntil("ember-ghost visible", 60, () -> nearest(dev.vestige.player.PlayerModule.EMBER_GHOST.get()) != null);
        camera(CameraType.FIRST_PERSON);
        wait(10);
        shot("g_02_ember_ghost");
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("g_03_ember_ghost_behind_player");
        run(() -> {
            var server = mc().getSingleplayerServer();
            String cmd = String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f", remembered[1], arenaCenter.y, remembered[2]);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
        });
        waitUntil("glim reclaimed", 60, () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount() == 500);
        check("reclaimed the ghost's glim", () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount() == 500,
                () -> "glim " + mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount());
        camera(CameraType.FIRST_PERSON);
        shot("g_04_reclaimed");
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    // ------------------------------------------------------------------ equipment (Milestone 5)

    private static dev.vestige.equipment.RpgInventory rpg() {
        return mc().player.getData(dev.vestige.equipment.EquipmentModule.RPG_INVENTORY);
    }

    private static dev.vestige.stats.StatSheet sheet() {
        return dev.vestige.stats.Stats.of(mc().player);
    }

    /** Moves the real cursor (GUI coordinates), so hover tooltips render exactly as for a player. */
    private static void hover(java.util.function.IntSupplier gx, java.util.function.IntSupplier gy) {
        run(() -> {
            try {
                double scale = mc().getWindow().getGuiScale();
                var mh = mc().mouseHandler;
                var fx = mh.getClass().getDeclaredField("xpos");
                var fy = mh.getClass().getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mh, gx.getAsInt() * scale);
                fy.setDouble(mh, gy.getAsInt() * scale);
            } catch (ReflectiveOperationException e) {
                fail("hover", e.toString());
            }
        });
        wait(3);
    }

    /** Shift-clicks every inventory stack of the open equipment menu, as a player would (server routes it). */
    private static void shiftClickAll(java.util.function.Predicate<net.minecraft.world.item.ItemStack> which) {
        run(() -> {
            var menu = mc().player.containerMenu;
            for (int i = dev.vestige.equipment.EquipmentMenu.INV_START; i < dev.vestige.equipment.EquipmentMenu.INV_END; i++) {
                var stack = menu.slots.get(i).getItem();
                if (!stack.isEmpty() && which.test(stack)) {
                    mc().gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc().player);
                }
            }
        });
        wait(6);
    }

    private static void openEquipment() {
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.OPEN_EQUIPMENT)));
        run(() -> mc().getToasts().clear());
        waitUntil("equipment screen opens", 60, () -> dev.vestige.client.equipment.EquipmentScreen.current != null);
        wait(12); // fade-in
    }

    private static void closeScreen() {
        run(() -> {
            if (mc().screen != null) {
                mc().screen.onClose();
            }
        });
        wait(4);
    }

    private static void wearSet(String set) {
        onServerPlayer(sp -> {
            String[][] pieces = {
                {"wanderer_hood", "wanderer_coat", "wanderer_trousers", "wanderer_boots"},
                {"bellbronze_helm", "bellbronze_mail", "bellbronze_tassets", "bellbronze_sabatons"},
                {"wickiron_helm", "wickiron_plate", "wickiron_cuisses", "wickiron_greaves"}};
            int idx = set.equals("wanderer") ? 0 : set.equals("bellbronze") ? 1 : 2;
            net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(VestigeIds.id(pieces[idx][i]));
                sp.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(item));
            }
        });
        wait(6);
    }

    /**
     * Milestone 5 in the real client: the equipment screen (empty, filled by real shift-clicks,
     * tooltips with comparisons, the doll turned), stats reacting at once, the three armor sets in
     * third and first person, combat stance and the HUD Readiness Cross, relics (Pale Hook,
     * Windsail glide), statuses on the HUD, Attunement and Kindling at a Waylight.
     */
    private static void buildEquipmentScenario() {
        setup();
        command("difficulty peaceful");
        command("gamerule keepInventory true");
        float[] mem = new float[8];
        // A starting kit, as loot would give it.
        for (String id : new String[] {"keepsake_blade", "hand_maul", "wanderer_hood", "wanderer_coat", "wanderer_trousers", "wanderer_boots",
            "ring_of_quick_breath", "harts_ring", "talisman_of_the_grove", "pale_hook", "windsail_cape", "plank_buckler", "wickiron_greatblade"}) {
            command("give @p vestige:" + id);
        }
        selectSlot0();
        run(() -> mem[0] = mc().player.getMaxHealth());
        run(() -> mem[1] = sheet().absorption.get(dev.vestige.stats.DamageType.SLASH));
        openEquipment();
        shot("e_01_screen_empty");
        check("equipment screen open", () -> dev.vestige.client.equipment.EquipmentScreen.current != null, "no screen");
        // Hover the keepsake blade in the inventory: a weapon tooltip with AR per type, scaling, requirements.
        hover(() -> slotX(dev.vestige.equipment.EquipmentMenu.INV_START + 27), () -> slotY(dev.vestige.equipment.EquipmentMenu.INV_START + 27));
        shot("e_02_tooltip_weapon");
        // Equip everything with real shift-clicks (the greatblade stays: one Main weapon).
        shiftClickAll(st -> !st.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(VestigeIds.id("wickiron_greatblade")))
                && !st.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(VestigeIds.id("hand_maul"))));
        hover(() -> 0, () -> 0);
        shot("e_03_screen_equipped");
        check("armor equipped by shift-click", () -> !mc().player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
                "no chest piece");
        check("main weapon equipped", () -> !rpg().get(dev.vestige.equipment.EquipSlot.MAIN).isEmpty(), "main slot empty");
        check("buckler routed to the secondary slot", () -> !rpg().get(dev.vestige.equipment.EquipSlot.SECONDARY).isEmpty(), "secondary empty");
        check("rings equipped", () -> !rpg().get(dev.vestige.equipment.EquipSlot.RING_1).isEmpty()
                && !rpg().get(dev.vestige.equipment.EquipSlot.RING_2).isEmpty(), "rings missing");
        check("relics equipped", () -> !rpg().get(dev.vestige.equipment.EquipSlot.RELIC_1).isEmpty()
                && !rpg().get(dev.vestige.equipment.EquipSlot.RELIC_2).isEmpty(), "relics missing");
        check("stats updated at once (absorption)", () -> sheet().absorption.get(dev.vestige.stats.DamageType.SLASH) > mem[1] + 0.05f,
                () -> mem[1] + " -> " + sheet().absorption.get(dev.vestige.stats.DamageType.SLASH));
        check("wanderer set bonus active", () -> sheet().sets.getOrDefault("vestige:wanderer", 0) == 4, () -> "sets " + sheet().sets);
        check("ring perk active", () -> sheet().has(dev.vestige.stats.Perks.HARTS_RING), () -> "perks " + sheet().perks);
        // Comparison tooltip: hover the hand maul while the blade is the Main weapon.
        run(() -> {
            var menu = mc().player.containerMenu;
            for (int i = dev.vestige.equipment.EquipmentMenu.INV_START; i < dev.vestige.equipment.EquipmentMenu.INV_END; i++) {
                if (menu.slots.get(i).getItem().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(VestigeIds.id("hand_maul")))) {
                    hoverSlot = i;
                }
            }
        });
        hover(() -> slotX(hoverSlot), () -> slotY(hoverSlot));
        shot("e_04_tooltip_compare");
        hover(() -> slotX(dev.vestige.equipment.EquipmentMenu.ARMOR_START + 1), () -> slotY(dev.vestige.equipment.EquipmentMenu.ARMOR_START + 1));
        shot("e_05_tooltip_armor_set");
        hover(() -> slotX(dev.vestige.equipment.EquipSlot.RING_2.ordinal()), () -> slotY(dev.vestige.equipment.EquipSlot.RING_2.ordinal()));
        shot("e_06_tooltip_ring");
        hover(() -> slotX(dev.vestige.equipment.EquipSlot.RELIC_3.ordinal()), () -> slotY(dev.vestige.equipment.EquipSlot.RELIC_3.ordinal()));
        shot("e_07_tooltip_locked_slot");
        hover(() -> 0, () -> 0);
        run(() -> dev.vestige.client.equipment.EquipmentScreen.current.rotateDoll(140f));
        wait(4);
        shot("e_08_doll_turned");
        // Overburdened: the greatblade into Main (via the server menu), then back.
        closeScreen();
        // Armor sets on the player, third person front/back and first person.
        for (String set : new String[] {"wanderer", "bellbronze", "wickiron"}) {
            wearSet(set);
            recenter();
            camera(CameraType.THIRD_PERSON_FRONT);
            wait(10);
            shot("a_" + set + "_front");
            camera(CameraType.THIRD_PERSON_BACK);
            shot("a_" + set + "_back");
            sideCamera(-2.6, 0.2, -90f);
            shot("a_" + set + "_side");
            playerCamera();
            camera(CameraType.FIRST_PERSON);
            shot("a_" + set + "_first_person");
        }
        check("first-person armor sleeve drawn", () -> dev.vestige.client.equipment.EquipmentClient.sleevesDrawn > 0, "no sleeve");
        check("wickiron set counted", () -> sheet().sets.getOrDefault("vestige:wickiron", 0) == 4, () -> "sets " + sheet().sets);
        check("heavy armor raises the burden", () -> sheet().burden != dev.vestige.stats.Burden.LIGHT, () -> "burden " + sheet().burden);
        // Combat stance: V moves the Main/Secondary into the hands; the HUD cross lights.
        run(() -> mem[2] = (float) mc().player.getInventory().selected);
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.STANCE)));
        waitUntil("stance entered", 40, () -> rpg().inStance());
        wait(4);
        check("stance put the main weapon in hand", () -> dev.vestige.weapon.WeaponProfiles.of(mc().player.getMainHandItem(), true).isPresent(),
                () -> "hand " + mc().player.getMainHandItem());
        check("stance put the buckler in the off-hand", () -> !mc().player.getOffhandItem().isEmpty(), "off-hand empty");
        shot("s_01_stance_first_person_hud");
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("s_02_stance_third_person");
        check("hud cross rendered", () -> dev.vestige.client.rpg.RpgHud.rendered, "no HUD");
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.STANCE)));
        waitUntil("stance left", 40, () -> !rpg().inStance());
        check("leaving stance restored the slots", () -> !rpg().get(dev.vestige.equipment.EquipSlot.MAIN).isEmpty(), "main lost");
        // Relics: the Pale Hook pulls the player to a pillar ahead.
        camera(CameraType.FIRST_PERSON);
        recenter();
        command("execute at @p run fill ~-1 ~ ~9 ~1 ~4 ~9 minecraft:deepslate_bricks");
        command("execute at @p run tp @p ~ ~ ~ 0 0");
        run(() -> mem[3] = (float) mc().player.getZ());
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.USE_RELIC)));
        wait(5);
        shot("r_01_pale_hook_pull");
        wait(20);
        check("pale hook pulled the player", () -> mc().player.getZ() > mem[3] + 3f, () -> mem[3] + " -> " + mc().player.getZ());
        command("execute at @p run fill ~-1 ~ ~-1 ~1 ~4 ~3 minecraft:air");
        // Windsail: cycle to it, then fall holding jump.
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.CYCLE_RELIC)));
        wait(4);
        check("relic cycled", () -> rpg().activeRelic() == 1, () -> "active " + rpg().activeRelic());
        recenter();
        command("execute at @p run tp @p ~ ~14 ~");
        run(() -> mc().options.keyJump.setDown(true));
        wait(14);
        camera(CameraType.THIRD_PERSON_BACK);
        check("windsail glide engaged", () -> dev.vestige.client.equipment.EquipmentClient.gliding, () -> "vy " + mc().player.getDeltaMovement().y);
        shot("r_02_windsail_glide");
        waitUntil("landed", 200, () -> mc().player.onGround());
        run(() -> mc().options.keyJump.setDown(false));
        // Statuses on the HUD: a Scorch build-up and an active Hushed.
        camera(CameraType.FIRST_PERSON);
        onServerPlayer(sp -> {
            dev.vestige.combat.status.StatusService.build(sp, dev.vestige.stats.StatusType.SCORCH, 60f, null);
            dev.vestige.combat.status.StatusService.build(sp, dev.vestige.stats.StatusType.HUSHED, 150f, null);
            dev.vestige.combat.status.StatusService.build(sp, dev.vestige.stats.StatusType.REND, 35f, null);
        });
        wait(4);
        shot("h_01_status_hud");
        check("hushed synced to the client", () -> dev.vestige.combat.status.StatusService.active(mc().player, dev.vestige.stats.StatusType.HUSHED),
                "not hushed");
        // Attunement and Kindling at a Waylight.
        recenter();
        run(() -> waylightA = net.minecraft.core.BlockPos.containing(arenaCenter.x + 2, arenaCenter.y, arenaCenter.z + 3));
        run(() -> {
            var a = waylightA;
            var server = mc().getSingleplayerServer();
            server.execute(() -> server.overworld().setBlock(a, dev.vestige.waylight.WaylightModule.WAYLIGHT.get().defaultBlockState(), 3));
        });
        onServerPlayer(sp -> {
            dev.vestige.player.GlimService.set(sp, 6000);
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(dev.vestige.equipment.EquipmentItems.KINDLING_EMBER.get()));
        });
        run(() -> mem[4] = mc().player.getMaxHealth());
        useBlock(() -> waylightA); // lights it
        wait(30);
        useBlock(() -> waylightA);
        waitUntil("waylight menu", 40, () -> dev.vestige.client.rpg.WaylightScreen.current != null);
        shot("k_01_waylight_menu_attune_kindle");
        run(() -> dev.vestige.client.rpg.WaylightScreen.current.showAttune());
        waitUntil("attune screen", 20, () -> dev.vestige.client.equipment.AttuneScreen.current != null);
        run(() -> {
            var s = dev.vestige.client.equipment.AttuneScreen.current;
            for (int i = 0; i < 3; i++) {
                s.plan(dev.vestige.stats.Attribute.VITALITY, 1);
            }
            s.plan(dev.vestige.stats.Attribute.STRENGTH, 1);
            s.plan(dev.vestige.stats.Attribute.LAMPLIGHT, 1);
        });
        wait(4);
        shot("k_02_attune_planned");
        run(() -> dev.vestige.client.equipment.AttuneScreen.current.confirm());
        waitUntil("attuned", 40, () -> mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level() == 6);
        wait(4);
        shot("k_03_attune_done");
        check("attunement raised the level", () -> mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level() == 6,
                () -> "level " + mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level());
        check("attunement spent glim", () -> mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount()
                == 6000 - dev.vestige.equipment.Attunement.cost(1, 5), () -> "glim " + mc().player.getData(dev.vestige.player.PlayerModule.GLIM).amount());
        check("vitality raised max health at once", () -> mc().player.getMaxHealth() > mem[4] + 10f,
                () -> mem[4] + " -> " + mc().player.getMaxHealth());
        run(() -> dev.vestige.client.equipment.AttuneScreen.current.onClose());
        waitUntil("back on the waylight menu", 20, () -> dev.vestige.client.rpg.WaylightScreen.current != null);
        run(() -> dev.vestige.client.rpg.WaylightScreen.current.kindle());
        waitUntil("kindled", 40, () -> flask().kindling() == 1);
        check("kindling raised the flask", () -> flask().kindling() == 1, () -> "kindling " + flask().kindling());
        openEquipment();
        shot("e_09_screen_after_attune");
        closeScreen();
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    // ------------------------------------------------------------------ multiplayer (dedicated server, two clients)

    /** A chat command, as typed by an operator on a dedicated server. */
    private static void chat(String cmd) {
        run(() -> mc().player.connection.sendCommand(cmd));
        wait(4);
    }

    private static net.minecraft.world.entity.player.Player otherPlayer() {
        for (var p : mc().level.players()) {
            if (p != mc().player) {
                return p;
            }
        }
        return null;
    }

    /** Turns the local player (client-authoritative look) toward the other player's body. */
    private static void lookAtOther(double height) {
        run(() -> {
            var o = otherPlayer();
            if (o == null) {
                return;
            }
            var p = mc().player;
            double dx = o.getX() - p.getX(), dz = o.getZ() - p.getZ(), dy = o.getY() + height - p.getEyeY();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            p.setYRot(yaw);
            p.setXRot(pitch);
            p.yHeadRot = yaw;
        });
        wait(2);
    }

    private static void mpDone() {
        run(() -> {
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
        });
    }

    /**
     * Client A on a dedicated server: equips through the real menu, enters stance, cycles relics and
     * tries a forged Attune packet (refused: no Waylight). Every result is read back from server sync.
     */
    private static void buildMultiplayerA() {
        waitUntil("joined the server", 1200, () -> mc().player != null && mc().level != null && worldTicks > 80);
        check("connected to a dedicated server", () -> !mc().hasSingleplayerServer() && mc().getConnection() != null, "not remote");
        chat("gamemode survival");
        chat("difficulty peaceful");
        chat("time set day");
        chat("clear");
        chat("tp @s 0 200 0");
        chat("fill -6 199 -6 6 199 6 minecraft:polished_andesite");
        chat("fill -6 200 -6 6 204 6 minecraft:air");
        chat("tp @s 0 200 0 0 0");
        for (String id : new String[] {"keepsake_blade", "wanderer_hood", "wanderer_coat", "wanderer_trousers", "wanderer_boots",
            "plank_buckler", "harts_ring", "pale_hook", "windsail_cape"}) {
            chat("give @s vestige:" + id);
        }
        wait(20);
        float[] mem = new float[4];
        run(() -> mem[0] = sheet().absorption.get(dev.vestige.stats.DamageType.SLASH));
        openEquipment();
        shiftClickAll(st -> true);
        wait(10);
        shot("mp_a_01_equipment");
        check("mp: armor equipped (server-synced)", () -> mc().player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(
                dev.vestige.equipment.EquipmentArmor.WANDERER_COAT.get()), () -> "chest " + mc().player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        check("mp: rpg slots synced to the owner", () -> !rpg().get(dev.vestige.equipment.EquipSlot.MAIN).isEmpty()
                && !rpg().get(dev.vestige.equipment.EquipSlot.RING_1).isEmpty(), "rpg slots empty on the client");
        check("mp: stats recomputed on the client", () -> sheet().absorption.get(dev.vestige.stats.DamageType.SLASH) > mem[0] + 0.05f,
                () -> "absorption " + sheet().absorption.get(dev.vestige.stats.DamageType.SLASH));
        closeScreen();
        // Forged packet: Attune with no Waylight near. The server must refuse.
        run(() -> mem[1] = mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level());
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.vestige.equipment.EquipmentNetwork.Attune(
                mc().player.blockPosition(), java.util.List.of(20, 20, 20, 20, 20, 20))));
        wait(20);
        check("mp: forged attune refused", () -> mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level() == (int) mem[1],
                () -> "level " + mc().player.getData(dev.vestige.equipment.EquipmentModule.ATTRIBUTES).level());
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.CYCLE_RELIC)));
        waitUntil("relic cycled", 40, () -> rpg().activeRelic() == 1);
        check("mp: relic cycle round-trip", () -> rpg().activeRelic() == 1, () -> "active " + rpg().activeRelic());
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.STANCE)));
        waitUntil("stance entered", 40, () -> rpg().inStance());
        check("mp: stance wields the main weapon", () -> mc().player.getMainHandItem().is(dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE.get()),
                () -> "hand " + mc().player.getMainHandItem());
        run(() -> VestigeIds.LOG.info("DRIVER MP A READY"));
        camera(CameraType.THIRD_PERSON_FRONT);
        shot("mp_a_02_stance_hud");
        // Hold still while B looks; continue once B has left (or after 60 s).
        waitUntil("second player arrives", 1800, () -> mc().getConnection().getOnlinePlayers().size() > 1);
        waitUntil("second player leaves", 2400, () -> mc().getConnection().getOnlinePlayers().size() == 1);
        run(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new dev.vestige.equipment.EquipmentNetwork.Act(dev.vestige.equipment.EquipmentNetwork.Act.STANCE)));
        waitUntil("stance left", 40, () -> !rpg().inStance());
        check("mp: leaving stance restores the slots", () -> !rpg().get(dev.vestige.equipment.EquipSlot.MAIN).isEmpty()
                && mc().player.getMainHandItem().isEmpty(), () -> "hand " + mc().player.getMainHandItem());
        mpDone();
    }

    /** Client B: watches A's armor and stance weapon render, and confirms A's private slots are not sent to it. */
    private static void buildMultiplayerB() {
        waitUntil("joined the server", 1200, () -> mc().player != null && mc().level != null && worldTicks > 80);
        chat("gamemode survival");
        chat("execute at Dev run tp @s ^ ^ ^3.2");
        chat("execute at @s run tp @s ~ ~ ~ facing entity Dev feet");
        waitUntil("player A visible", 600, () -> otherPlayer() != null);
        waitUntil("A armed and armored", 1200, () -> otherPlayer() != null
                && otherPlayer().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(dev.vestige.equipment.EquipmentArmor.WANDERER_COAT.get())
                && otherPlayer().getMainHandItem().is(dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE.get()));
        lookAtOther(0.9);
        wait(30);
        check("mp: B sees A's armor", () -> otherPlayer() != null && otherPlayer().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(dev.vestige.equipment.EquipmentArmor.WANDERER_HOOD.get()), "no hood on A");
        check("mp: B sees A's stance weapon and shield", () -> otherPlayer() != null
                && otherPlayer().getMainHandItem().is(dev.vestige.weapon.WeaponModule.KEEPSAKE_BLADE.get())
                && otherPlayer().getOffhandItem().is(dev.vestige.weapon.WeaponModule.PLANK_BUCKLER.get()), "A not armed");
        check("mp: A's private slots are not sent to B", () -> otherPlayer() != null
                && otherPlayer().getData(dev.vestige.equipment.EquipmentModule.RPG_INVENTORY).get(dev.vestige.equipment.EquipSlot.RING_1).isEmpty(),
                "B received A's rings");
        run(() -> {
            mc().gui.getChat().clearMessages(false);
            mc().getToasts().clear();
        });
        wait(2);
        shot("mp_b_01_sees_a");
        chat("execute at Dev run tp @s ^2.2 ^ ^1.6");
        lookAtOther(0.9);
        run(() -> {
            mc().gui.getChat().clearMessages(false);
            mc().getToasts().clear();
        });
        wait(20);
        shot("mp_b_02_sees_a_angle");
        mpDone();
    }

    private static int hoverSlot;

    private static int slotX(int index) {
        var screen = dev.vestige.client.equipment.EquipmentScreen.current;
        return screen == null ? 0 : screen.getGuiLeft() + mc().player.containerMenu.slots.get(index).x + 8;
    }

    private static int slotY(int index) {
        var screen = dev.vestige.client.equipment.EquipmentScreen.current;
        return screen == null ? 0 : screen.getGuiTop() + mc().player.containerMenu.slots.get(index).y + 8;
    }

    /** Freezes an entity's mirrored action at a tick so a pose can be inspected (dev only). */
    private static void freeze(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, String action, int tick) {
        run(() -> mc().level.getEntities(mc().player, mc().player.getBoundingBox().inflate(12), e -> e.getType() == type.get())
                .forEach(e -> {
                    RunningAction ra = ClientActions.start(e.getId(), action, -1, Float.NaN, false);
                    if (ra != null) {
                        ra.setElapsed(tick);
                        ra.addHitstop(1_000_000);
                    }
                }));
        wait(4);
    }

    private static void unfreeze() {
        run(() -> new java.util.ArrayList<>(ClientActions.all().keySet()).forEach(ClientActions::end));
        wait(3);
    }

    /**
     * Model and animation inspection: Hollowed and Brute side by side, front and side views, frozen
     * at key poses, then the death collapse. HUD hidden for clean screenshots.
     */
    private static void buildModelsScenario() {
        setup();
        command("difficulty normal");
        command("time set 6000");
        command("execute at @p rotated 0 0 run summon vestige:hollowed ^1.3 ^ ^4 {NoAI:1b,Rotation:[180f,0f]}");
        command("execute at @p rotated 0 0 run summon vestige:hollowed_brute ^-1.6 ^ ^4.4 {NoAI:1b,Rotation:[180f,0f]}");
        run(() -> mc().options.hideGui = true);
        camera(CameraType.FIRST_PERSON);
        waitUntil("models visible", 100, () -> nearest(EntityModule.HOLLOWED.get()) != null && nearest(EntityModule.HOLLOWED_BRUTE.get()) != null);
        wait(20);
        for (String view : new String[] {"front", "side"}) {
            if (view.equals("side")) {
                command("execute as @e[type=vestige:hollowed] at @s run tp @s ~ ~ ~ 90 0");
                command("execute as @e[type=vestige:hollowed_brute] at @s run tp @s ~ ~ ~ 90 0");
                wait(10);
            }
            shot("m_" + view + "_1_idle");
            freeze(EntityModule.HOLLOWED, "vestige:hollowed#swipe", 14);
            freeze(EntityModule.HOLLOWED_BRUTE, "vestige:hollowed_brute#overhead_slam", 22);
            shot("m_" + view + "_2_windup_peak");
            unfreeze();
            freeze(EntityModule.HOLLOWED, "vestige:hollowed#swipe", 17);
            freeze(EntityModule.HOLLOWED_BRUTE, "vestige:hollowed_brute#overhead_slam", 25);
            shot("m_" + view + "_3_strike");
            unfreeze();
            freeze(EntityModule.HOLLOWED, "vestige:hollowed#lunge_grab", 18);
            freeze(EntityModule.HOLLOWED_BRUTE, "vestige:hollowed_brute#sweep", 16);
            shot("m_" + view + "_4_lunge_and_sweep_peak");
            unfreeze();
            freeze(EntityModule.HOLLOWED, "vestige:hollowed#parried", 8);
            freeze(EntityModule.HOLLOWED_BRUTE, "vestige:hollowed_brute#parried", 8);
            shot("m_" + view + "_5_parried");
            unfreeze();
            freeze(EntityModule.HOLLOWED, "vestige:hollowed#riposted", 32);
            freeze(EntityModule.HOLLOWED_BRUTE, "vestige:hollowed_brute#riposted", 32);
            shot("m_" + view + "_6_riposted");
            unfreeze();
        }
        command("kill @e[type=vestige:hollowed]");
        wait(12);
        shot("m_side_7_death_mid");
        wait(16);
        shot("m_side_8_death_end");
        run(() -> {
            mc().options.hideGui = false;
            VestigeIds.LOG.info("DRIVER DONE checks={} failures={} {}", checks, FAILURES.size(), FAILURES);
            quitSoon = 40;
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
