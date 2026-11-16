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
        // Hitstop on attacker and target.
        if (p.attackerId() == me.getId() && ClientCombat.machine().current() != null) {
            ClientCombat.machine().current().addHitstop(p.hitstop());
        } else {
            ClientActions.addHitstop(p.attackerId(), p.hitstop());
        }
        ClientActions.addHitstop(p.targetId(), p.hitstop());

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

        if (p.attackerId() == me.getId()) {
            shake = Math.max(shake, p.heavy() ? 1.4f : 0.6f);
        } else if (p.targetId() == me.getId()) {
            shake = Math.max(shake, 2.4f);
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
