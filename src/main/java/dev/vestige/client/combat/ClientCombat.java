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
        };
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
        boolean send = input == CombatInput.HEAVY_RELEASE || started.isPresent() || MACHINE.hasBuffered();
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
        if (player == null || mc.screen != null || !armed()) {
            heavyWasDown = false;
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
    }

    /** Suppress vanilla attack/mining/pick-block while armed; combat handles those buttons. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (armed() && (event.isAttack() || event.isPickBlock())) {
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
