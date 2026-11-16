package dev.vestige.combat.status;

import dev.vestige.VestigeIds;
import dev.vestige.combat.CombatModule;
import dev.vestige.combat.CombatTarget;
import dev.vestige.combat.damage.CombatDamageTypes;
import dev.vestige.stats.StatusType;
import dev.vestige.stats.Stats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Status buildup, triggering, ticking and cure (GDD §7.10). Server side. */
public final class StatusService {
    public static final float THRESHOLD = 100f;
    public static final int SCORCH_TICKS = 120;
    public static final int HUSHED_TICKS = 200;
    /** Meters start draining this long after the last buildup, at 10 per second. */
    public static final int DECAY_DELAY = 60;
    public static final ResourceLocation VOIDBITE_ID = VestigeIds.id("voidbite");

    private StatusService() {}

    public static StatusState state(LivingEntity e) {
        return e.getData(CombatModule.STATUS);
    }

    public static boolean active(LivingEntity e, StatusType t) {
        if (!e.hasData(CombatModule.STATUS)) {
            return false;
        }
        StatusState s = state(e);
        return t == StatusType.VOIDBITE ? s.voidbitten : s.timers[t.ordinal()] > 0;
    }

    /** Healing multiplier (Scorch cuts healing by 30%). */
    public static float healFactor(LivingEntity e) {
        return active(e, StatusType.SCORCH) ? 0.7f : 1f;
    }

    /** Fraction of buildup the entity shrugs off. */
    public static float resist(LivingEntity e, StatusType t) {
        float r = 0f;
        if (e instanceof CombatTarget ct) {
            r = ct.statusResist(t);
        }
        if (e instanceof Player p) {
            var sheet = Stats.of(p);
            r = 1f - (1f - r) * (1f - sheet.statusResist);
            if (t == StatusType.HUSHED && sheet.has(dev.vestige.stats.Perks.BELL_OF_WARDING)) {
                r = 1f - (1f - r) * 0.4f;
            }
        }
        return Math.max(0f, Math.min(1f, r));
    }

    /** Adds buildup; triggers the status at 100. Returns true if it triggered. */
    public static boolean build(LivingEntity target, StatusType type, float amount, LivingEntity source) {
        if (amount <= 0f || target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        float add = amount * (1f - resist(target, type));
        if (add <= 0f) {
            return false;
        }
        StatusState s = state(target);
        // A running timed status (or a standing Voidbite) does not build further.
        if ((type == StatusType.VOIDBITE && s.voidbitten) || s.timers[type.ordinal()] > 0) {
            return false;
        }
        s.lastBuildup = target.level().getGameTime();
        s.meters[type.ordinal()] += add;
        boolean triggered = s.meters[type.ordinal()] >= THRESHOLD;
        if (triggered) {
            s.meters[type.ordinal()] = 0f;
            trigger(target, type, source);
        }
        sync(target);
        return triggered;
    }

    private static void trigger(LivingEntity target, StatusType type, LivingEntity source) {
        ServerLevel level = (ServerLevel) target.level();
        StatusState s = state(target);
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.6, z = target.getZ();
        switch (type) {
            case REND -> {
                float burst = target.getMaxHealth() * 0.12f + target.getHealth() * 0.05f;
                target.hurt(CombatDamageTypes.status(target, source), burst);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 20, 0.3, 0.4, 0.3, 0.2);
                level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 0.6f);
            }
            case SCORCH -> {
                s.timers[type.ordinal()] = SCORCH_TICKS;
                level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1f, 0.8f);
            }
            case HUSHED -> {
                s.timers[type.ordinal()] = HUSHED_TICKS;
                level.playSound(null, x, y, z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.5f, 1.6f);
            }
            case VOIDBITE -> {
                s.voidbitten = true;
                applyVoidbite(target);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 30, 0.3, 0.5, 0.3, 0.05);
                level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.5f);
            }
        }
    }

    private static void applyVoidbite(LivingEntity e) {
        var max = e.getAttribute(Attributes.MAX_HEALTH);
        if (max != null && max.getModifier(VOIDBITE_ID) == null) {
            max.addPermanentModifier(new AttributeModifier(VOIDBITE_ID, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            e.setHealth(Math.min(e.getHealth(), e.getMaxHealth()));
        }
    }

    /** Resting cures everything, Voidbite included. */
    public static void cure(LivingEntity e) {
        if (!e.hasData(CombatModule.STATUS)) {
            return;
        }
        StatusState s = state(e);
        java.util.Arrays.fill(s.meters, 0f);
        java.util.Arrays.fill(s.timers, 0);
        s.voidbitten = false;
        var max = e.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(VOIDBITE_ID);
        }
        sync(e);
    }

    /** Server tick for entities carrying status data. */
    public static void tick(LivingEntity e) {
        if (!e.hasData(CombatModule.STATUS) || !e.isAlive()) {
            return;
        }
        StatusState s = state(e);
        if (s.idle() && !s.voidbitten) {
            return;
        }
        boolean changed = false;
        long now = e.level().getGameTime();
        if (now - s.lastBuildup > DECAY_DELAY) {
            for (int i = 0; i < s.meters.length; i++) {
                if (s.meters[i] > 0f) {
                    s.meters[i] = Math.max(0f, s.meters[i] - 0.5f);
                    changed = true;
                }
            }
        }
        int scorch = StatusType.SCORCH.ordinal();
        if (s.timers[scorch] > 0) {
            s.timers[scorch]--;
            changed = true;
            if (s.timers[scorch] % 20 == 0) {
                e.hurt(CombatDamageTypes.status(e, null), e.getMaxHealth() * 0.015f);
            }
            if (e.tickCount % 4 == 0 && e.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.25, 0.4, 0.25, 0.01);
            }
        }
        int hushed = StatusType.HUSHED.ordinal();
        if (s.timers[hushed] > 0) {
            s.timers[hushed]--;
            changed = true;
            if (e.tickCount % 8 == 0 && e.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
            }
        }
        if (changed && (e.tickCount % 10 == 0 || s.idle())) {
            sync(e);
        }
    }

    private static void sync(LivingEntity e) {
        if (e instanceof ServerPlayer p) {
            p.syncData(CombatModule.STATUS);
        }
    }
}
