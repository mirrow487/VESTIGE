package dev.vestige.client.fx;

import dev.vestige.client.combat.ClientActions;
import dev.vestige.client.combat.ClientCombat;
import dev.vestige.client.hud.CombatHud;
import dev.vestige.combat.net.CombatPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Hit feel (GDD §7.4): hitstop on both sides, directional sparks, weight-scaled sounds and camera
 * shake; perfect-dodge flash; dodge camera tilt.
 */
public final class HitFx {
    private static float shake;
    private static final RandomSource RANDOM = RandomSource.create();
    /** For tests/driver. */
    public static volatile int hitsSeen;

    private HitFx() {}

    public static void onHit(CombatPayloads.HitFeedback p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (mc.level == null || me == null) {
            return;
        }
        hitsSeen++;
        // Hitstop on attacker and target (the local player's own action lives in the predicted machine).
        boolean ranged = p.hitKind() == CombatPayloads.HitKind.RANGED;
        for (int id : ranged ? new int[] {p.targetId()} : new int[] {p.attackerId(), p.targetId()}) {
            if (id == me.getId()) {
                if (ClientCombat.machine().current() != null) {
                    ClientCombat.machine().current().addHitstop(p.hitstop());
                }
            } else {
                ClientActions.addHitstop(id, p.hitstop());
            }
        }
        switch (p.hitKind()) {
            case PARRY -> {
                parry(mc, p, me);
                return;
            }
            case BLOCK -> {
                block(mc, p);
                return;
            }
            case GUARD_BREAK -> {
                mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f, false);
                burst(mc, ParticleTypes.CRIT, p, 16, 0.9);
                if (p.targetId() == me.getId()) {
                    shake = Math.max(shake, 3.0f);
                }
                return;
            }
            case RIPOSTE -> {
                parriesOrCrits++;
                burst(mc, ParticleTypes.DAMAGE_INDICATOR, p, 14, 0.7);
                burst(mc, ParticleTypes.CRIT, p, 24, 1.1);
                mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.7f, false);
                mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 0.6f, false);
                if (p.attackerId() == me.getId()) {
                    shake = Math.max(shake, 2.6f);
                    CombatHud.flash(0xC03020, 0.7f);
                }
                return;
            }
            default -> {
            }
        }

        int sparks = p.heavy() ? 14 : 7;
        for (int i = 0; i < sparks; i++) {
            mc.level.addParticle(p.critical() ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, p.x(), p.y(), p.z(),
                    (RANDOM.nextDouble() - 0.5) * 0.8, RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.8);
        }
        if (p.heavy()) {
            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), p.y(), p.z(), 0, 0, 0);
        }
        var sound = p.critical() ? SoundEvents.PLAYER_ATTACK_CRIT : p.heavy() ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG;
        mc.level.playLocalSound(p.x(), p.y(), p.z(), sound, SoundSource.PLAYERS, 1f, p.heavy() ? 0.8f : 1f, false);

        if (ranged && p.attackerId() == me.getId()) {
            // Hit confirmation for the archer, wherever the arrow landed.
            me.playSound(SoundEvents.ARROW_HIT_PLAYER, 0.6f, p.critical() ? 1.4f : 1.1f);
        } else if (p.attackerId() == me.getId()) {
            shake = Math.max(shake, p.heavy() ? 1.4f : 0.6f);
        } else if (p.targetId() == me.getId()) {
            shake = Math.max(shake, 2.4f);
        }
    }

    /** For tests/driver: parries and criticals seen. */
    public static volatile int parriesOrCrits;

    private static void burst(Minecraft mc, net.minecraft.core.particles.ParticleOptions type, CombatPayloads.HitFeedback p, int n, double spread) {
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(type, p.x(), p.y(), p.z(), (RANDOM.nextDouble() - 0.5) * spread, RANDOM.nextDouble() * spread * 0.6,
                    (RANDOM.nextDouble() - 0.5) * spread);
        }
    }

    /** The parry is the most important feedback in the game: unmistakable sight, sound and feel. */
    private static void parry(Minecraft mc, CombatPayloads.HitFeedback p, LocalPlayer me) {
        parriesOrCrits++;
        if (p.targetId() == me.getId() && ClientCombat.machine().current() != null) {
            // Mirror the server: a successful parry can be cancelled into a riposte at once.
            ClientCombat.machine().current().parrySucceeded = true;
        }
        burst(mc, ParticleTypes.ENCHANTED_HIT, p, 22, 1.2);
        burst(mc, ParticleTypes.END_ROD, p, 10, 0.5);
        burst(mc, ParticleTypes.FIREWORK, p, 6, 0.35);
        mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.7f, false);
        mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f, false);
        if (p.targetId() == me.getId()) {
            shake = Math.max(shake, 1.8f);
            CombatHud.flash(0xF2D27A, 1.0f);
        }
    }

    private static void block(Minecraft mc, CombatPayloads.HitFeedback p) {
        burst(mc, ParticleTypes.CRIT, p, 6, 0.6);
        mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.9f + RANDOM.nextFloat() * 0.2f, false);
        if (p.targetId() == mc.player.getId()) {
            shake = Math.max(shake, 0.8f);
        }
    }

    public static void onEvent(CombatPayloads.Event p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        switch (p.eventKind()) {
            case PERFECT_DODGE -> {
                CombatHud.flashPerfectDodge();
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                        SoundSource.PLAYERS, 1f, 1.8f, false);
            }
            case STAGGER_BREAK -> {
                Entity e = mc.level.getEntity(p.entityId());
                if (e != null) {
                    mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1f, 0.7f, false);
                    for (int i = 0; i < 12; i++) {
                        mc.level.addParticle(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                                (RANDOM.nextDouble() - 0.5), RANDOM.nextDouble() * 0.5, (RANDOM.nextDouble() - 0.5));
                    }
                }
            }
        }
    }

    /** Camera shake from hits, plus a tilt and dip while dodging. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake > 0.01f) {
            float t = Minecraft.getInstance().level == null ? 0 : (Minecraft.getInstance().level.getGameTime() + (float) event.getPartialTick()) * 2.3f;
            event.setYaw(event.getYaw() + Mth.sin(t * 3.1f) * shake * 0.5f);
            event.setPitch(event.getPitch() + Mth.cos(t * 2.7f) * shake);
            event.setRoll(event.getRoll() + Mth.sin(t * 1.9f) * shake * 0.6f);
            shake *= 0.86f;
        }
        float dodge = ClientCombat.dodgeProgress();
        if (dodge >= 0f && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            var current = ClientCombat.machine().current();
            float dir = current == null || Float.isNaN(current.dirYaw) ? 0f : current.dirYaw;
            float arc = Mth.sin(dodge * Mth.PI);
            event.setRoll(event.getRoll() + Mth.sin((float) Math.toRadians(dir)) * 7f * arc);
            event.setPitch(event.getPitch() + 6f * arc);
        }
    }
}
