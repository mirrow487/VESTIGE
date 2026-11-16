package dev.vestige.client.combat;

import dev.vestige.client.anim.PlayerAnimations;
import dev.vestige.combat.CombatData;
import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.machine.CombatInput;
import dev.vestige.combat.machine.PlayerCombatMachine;
import dev.vestige.combat.machine.PlayerCombatMachine.MoveSetHandle;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.combat.net.CombatPayloads;
import dev.vestige.combat.player.PlayerCombat;
import dev.vestige.vitals.VitalsModule;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Local player combat: reads inputs, predicts actions with the shared {@link PlayerCombatMachine}
 * so animations start the instant a button is pressed, sends inputs to the server, applies root
 * motion and dodge movement, and reconciles with the server's verdict.
 */
public final class ClientCombat {
    private static final PlayerCombatMachine MACHINE = new PlayerCombatMachine();
    private static long clientTick;
    private static int nextSeq = 1;
    private static boolean heavyWasDown;
    private static boolean guardWasDown;
    private static int dodgeMoveTick = -1;
    private static RunningAction dodgeAction;

    private ClientCombat() {}

    public static PlayerCombatMachine machine() {
        return MACHINE;
    }

    public static long clientTick() {
        return clientTick;
    }

    /** True while holding a weapon that has a moveset (combat controls active). */
    public static boolean armed() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !p.isSpectator() && PlayerCombat.weapon(p).isPresent();
    }

    private static PlayerCombatMachine.Context context(LocalPlayer player) {
        return new PlayerCombatMachine.Context() {
            @Override
            public Optional<MoveSetHandle> weapon() {
                return PlayerCombat.weapon(player).flatMap(w -> CombatData.handle(true, w.moveset()));
            }

            @Override
            public Optional<MoveSetHandle> common() {
                return CombatData.handle(true, CombatData.PLAYER_MOVESET);
            }

            @Override
            public boolean canSpendStamina() {
                return player.isCreative() || player.getData(VitalsModule.STAMINA).current() > 0f;
            }

            @Override
            public boolean sprinting() {
                return player.isSprinting();
            }

            @Override
            public Optional<String> guardAction() {
                return Optional.of(PlayerCombat.guardActionFor(player));
            }

            @Override
            public boolean canSpendResonance(float cost) {
                // Hushed: arts are locked (GDD §7.10).
                if (dev.vestige.combat.status.StatusService.active(player, dev.vestige.stats.StatusType.HUSHED)) {
                    return false;
                }
                return player.isCreative() || player.getData(dev.vestige.combat.CombatModule.RESONANCE).value() + 1.0e-4f >= cost;
            }

            @Override
            public boolean riposteAvailable() {
                return riposteTarget(player).isPresent();
            }

            @Override
            public boolean hasAmmo() {
                return dev.vestige.combat.projectile.Ammo.has(player);
            }

            @Override
            public String rollAction() {
                var sheet = dev.vestige.stats.Stats.of(player);
                return sheet.has(dev.vestige.stats.Perks.HOLLOW_RING) && sheet.burden != dev.vestige.stats.Burden.OVERBURDENED
                        ? "roll_blink" : sheet.burden.rollAction;
            }

            @Override
            public boolean hasFlaskCharge() {
                return PlayerCombat.hasFlaskCharge(player);
            }
        };
    }

    /** Client view of a critical target, from mirrored enemy actions (same geometry as the server). */
    public static Optional<net.minecraft.world.entity.LivingEntity> riposteTarget(LocalPlayer player) {
        return dev.vestige.combat.player.RiposteTargeting.find(player, e -> reeling(ClientActions.of(e)),
                e -> !isAction(ClientActions.of(e), "riposted"));
    }

    static boolean reeling(RunningAction a) {
        return isAction(a, "stagger") || isAction(a, "parried");
    }

    private static boolean isAction(RunningAction a, String name) {
        return a != null && a.ref.action().equals(name);
    }

    /** Presses a combat input as if from the keyboard (also used by the dev test driver). */
    public static void press(CombatInput input, float dirYaw) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int seq = nextSeq++;
        Optional<PlayerCombatMachine.Start> started = MACHINE.input(input, seq, dirYaw, clientTick, context(player));
        // Send everything the machine accepted or buffered; inputs it rejects outright (no weapon,
        // no stamina) would be rejected by the server too.
        // Releases are always sent: the server must never keep a guard or charge the player let go of.
        boolean send = input == CombatInput.HEAVY_RELEASE || input == CombatInput.GUARD_RELEASE
                || started.isPresent() || MACHINE.hasBuffered();
        started.ifPresent(s -> onStarted(player, s.action()));
        if (send) {
            PacketDistributor.sendToServer(new CombatPayloads.Input(seq, input.ordinal(), dirYaw));
        }
    }

    private static void onStarted(LocalPlayer player, RunningAction action) {
        PlayerAnimations.play(player, action);
        if (action.def.dodge().isPresent()) {
            dodgeAction = action;
            dodgeMoveTick = 0;
        }
    }

    /** Before the vanilla tick: turn clicks into combat inputs while armed. */
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // The Wick Flask works whether or not a weapon is held.
        while (player != null && mc.screen == null && CombatKeys.FLASK.consumeClick()) {
            press(CombatInput.FLASK, Float.NaN);
        }
        if (player == null || mc.screen != null || !armed()) {
            if (guardWasDown && player != null) {
                press(CombatInput.GUARD_RELEASE, Float.NaN);
            }
            heavyWasDown = false;
            guardWasDown = false;
            return;
        }
        while (mc.options.keyAttack.consumeClick()) {
            press(CombatInput.LIGHT, Float.NaN);
        }
        boolean heavyDown = CombatKeys.HEAVY.isDown();
        if (heavyDown && !heavyWasDown) {
            press(CombatInput.HEAVY_PRESS, Float.NaN);
        } else if (!heavyDown && heavyWasDown) {
            press(CombatInput.HEAVY_RELEASE, Float.NaN);
        }
        heavyWasDown = heavyDown;
        while (CombatKeys.DODGE.consumeClick()) {
            press(CombatInput.ROLL, dodgeDirection(player));
        }
        // Guard (RMB) unless the player is aiming at a block to use it (doors, chests, levers).
        boolean guardDown = mc.options.keyUse.isDown() && !aimingAtUsableBlock(mc);
        if (guardDown && !guardWasDown) {
            press(CombatInput.GUARD_PRESS, Float.NaN);
        } else if (!guardDown && guardWasDown) {
            press(CombatInput.GUARD_RELEASE, Float.NaN);
        }
        guardWasDown = guardDown;
        // Weapon art (F). Vanilla's offhand swap shares the key; it is suppressed while armed.
        while (mc.options.keySwapOffhand.consumeClick()) {
            // swallowed: F is the weapon art while armed
        }
        while (CombatKeys.ART.consumeClick()) {
            press(CombatInput.ART, dodgeDirection(player)); // used only by dodging arts (Quickstep)
        }
    }

    static boolean aimingAtUsableBlock(Minecraft mc) {
        return !LockOn.locked() && mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    /** True while the local guard is raised (for HUD and feedback). */
    public static boolean guarding() {
        RunningAction c = MACHINE.current();
        return c != null && c.guarding();
    }

    /** Direction of movement input relative to facing (0 = forward, 90 = right), NaN if none. */
    static float dodgeDirection(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float right = -player.input.leftImpulse;
        if (Math.abs(forward) < 0.1f && Math.abs(right) < 0.1f) {
            return Float.NaN;
        }
        return (float) Math.toDegrees(Math.atan2(right, forward));
    }

    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            MACHINE.interrupt(clientTick, 0);
            ClientActions.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clientTick++;
        MACHINE.tick(clientTick, context(mc.player)).ifPresent(s -> onStarted(mc.player, s.action()));
        ClientActions.tick();
        PlayerAnimations.tick();
        resonanceAura(mc);
    }

    /** Resonant players hum: faint musical notes drift around them (GDD §8.3). */
    private static void resonanceAura(Minecraft mc) {
        float r = mc.player.getData(dev.vestige.combat.CombatModule.RESONANCE).value();
        if (r >= dev.vestige.combat.resonance.ResonanceRules.RESONANT && clientTick % (r >= 100f ? 4 : 10) == 0) {
            var rnd = mc.player.getRandom();
            double a = rnd.nextDouble() * Math.PI * 2;
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, mc.player.getX() + Math.cos(a) * 0.7,
                    mc.player.getY() + 0.6 + rnd.nextDouble() * 1.2, mc.player.getZ() + Math.sin(a) * 0.7, rnd.nextDouble(), 0, 0);
        }
    }

    /** Suppress vanilla attack/mining/pick-block while armed; combat handles those buttons. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!armed()) {
            return;
        }
        boolean guardUse = event.isUseItem() && !aimingAtUsableBlock(Minecraft.getInstance());
        if (event.isAttack() || event.isPickBlock() || guardUse) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Root motion and dodge movement replace walking input while acting (GDD §7.4). */
    static void onMovementInput(MovementInputUpdateEvent event) {
        LocalPlayer player = (LocalPlayer) event.getEntity();
        RunningAction current = MACHINE.current();
        boolean stunned = MACHINE.stunned(clientTick);
        if (current == null && !stunned) {
            // Out of stamina in combat stance: no sprinting (the server drains 6/s while sprinting armed).
            if (armed() && !player.isCreative() && player.getData(VitalsModule.STAMINA).current() <= 0f) {
                player.setSprinting(false);
            }
            return;
        }
        if (current != null && (current.kind == RunningAction.Kind.GUARD || current.kind == RunningAction.Kind.FLASK)) {
            // Guarding or drinking: a slow, deliberate walk (GDD §7.7, §8.4); a tower shield is slower still.
            float speed = current.ref.action().equals("guard_bulwark") ? 0.22f : current.kind == RunningAction.Kind.FLASK ? 0.4f : 0.35f;
            event.getInput().forwardImpulse *= speed;
            event.getInput().leftImpulse *= speed;
            event.getInput().jumping = false;
            player.setSprinting(false);
            return;
        }
        event.getInput().forwardImpulse = 0f;
        event.getInput().leftImpulse = 0f;
        event.getInput().jumping = false;
        player.setSprinting(false);
        if (current == null) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (current.def.dodge().isPresent() && current == dodgeAction) {
            ActionDef.Dodge d = current.def.dodge().get();
            if (dodgeMoveTick >= 0 && dodgeMoveTick < d.moveTicks()) {
                float speed = dodgeSpeed(d, dodgeMoveTick);
                float yaw = player.getYRot() + (Float.isNaN(current.dirYaw) ? 180f : current.dirYaw);
                Vec3 dir = Vec3.directionFromRotation(0f, yaw);
                player.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
                dodgeMoveTick++;
            }
            return;
        }
        if (current.hitstop() > 0) {
            player.setDeltaMovement(0, v.y, 0);
            return;
        }
        ActionDef.Motion m = current.def.motion();
        float speed = switch (current.phase()) {
            case WINDUP -> m.windup();
            case ACTIVE -> m.active();
            case RECOVERY -> m.recovery();
            case DONE -> 0f;
        };
        if (speed > 0f) {
            Vec3 dir = Vec3.directionFromRotation(0f, player.getYRot());
            player.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        }
    }

    /** Ease-out speed curve whose sum over the move ticks equals the dodge distance. */
    static float dodgeSpeed(ActionDef.Dodge d, int tick) {
        int n = d.moveTicks();
        float sum = n * (n + 1) / 2f;
        return d.distance() * (n - tick) / sum;
    }

    // ------------------------------------------------------------------ server reconciliation

    static void onServerStart(CombatPayloads.ActionStart p) {
        LocalPlayer player = Minecraft.getInstance().player;
        RunningAction current = MACHINE.current();
        if (p.seq() > 0 && current != null && current.seq == p.seq()) {
            return; // already predicted
        }
        ActionRef ref = ActionRef.parse(p.action());
        CombatData.resolve(true, ref).ifPresent(def -> {
            RunningAction.Kind kind = def.dodge().isPresent() ? RunningAction.Kind.DODGE : RunningAction.Kind.OTHER;
            RunningAction ra = new RunningAction(ref, def, p.seq(), p.dirYaw(), clientTick, kind);
            ra.setHolding(p.holding());
            MACHINE.adopt(ra);
            if (player != null) {
                onStarted(player, ra);
            }
        });
    }

    static void onServerEnd(CombatPayloads.ActionEnd p) {
        RunningAction current = MACHINE.current();
        switch (p.endReason()) {
            case REJECTED -> {
                if (current != null && current.seq == p.seq()) {
                    MACHINE.interrupt(clientTick, 0);
                    PlayerAnimations.stop(Minecraft.getInstance().player);
                }
            }
            case CANCELLED -> {
                MACHINE.interrupt(clientTick, 0);
                PlayerAnimations.stop(Minecraft.getInstance().player);
            }
            case INTERRUPTED -> MACHINE.interrupt(clientTick, dev.vestige.combat.player.PlayerCombat.DEFAULT_HIT_STUN);
        }
    }

    /** Remaining ticks of the current dodge's movement (for camera feel). */
    public static float dodgeProgress() {
        RunningAction current = MACHINE.current();
        if (current == null || current.def.dodge().isEmpty()) {
            return -1f;
        }
        return Mth.clamp(current.elapsed() / (float) current.def.dodge().get().moveTicks(), 0f, 1f);
    }

    public static boolean inPhase(ActionPhase phase) {
        RunningAction c = MACHINE.current();
        return c != null && c.phase() == phase;
    }
}
