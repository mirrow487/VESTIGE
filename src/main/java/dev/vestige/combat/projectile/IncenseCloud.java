package dev.vestige.combat.projectile;

import dev.vestige.combat.damage.CombatDamage;
import dev.vestige.combat.damage.CombatDamageTypes;
import dev.vestige.combat.machine.RunningAction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Censer incense (GDD §7.11 Smolder Wake): a smouldering cloud that burns everything hostile inside
 * it every {@code interval} ticks (no poise damage, no flinch, so it rewards fighting inside it).
 * Not saved: a cloud is a few seconds of combat state.
 */
public class IncenseCloud extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(IncenseCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(IncenseCloud.class, EntityDataSerializers.INT);

    private LivingEntity owner;
    private RunningAction action;
    private double attackRating;
    private float motionValue;
    private int interval = 10;
    /** For tests: damage ticks dealt. */
    public int pulses;

    public IncenseCloud(EntityType<? extends IncenseCloud> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configure(LivingEntity owner, RunningAction action, double attackRating, float radius, int duration, int interval,
                          float motionValue) {
        this.owner = owner;
        this.action = action;
        this.attackRating = attackRating;
        this.motionValue = motionValue;
        this.interval = Math.max(1, interval);
        entityData.set(RADIUS, radius);
        entityData.set(DURATION, duration);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 2.5f);
        builder.define(DURATION, 60);
    }

    @Override
    public void tick() {
        super.tick();
        int duration = entityData.get(DURATION);
        if (level().isClientSide) {
            particles();
            return;
        }
        if (tickCount >= duration || owner == null || action == null) {
            discard();
            return;
        }
        if (tickCount % interval == 0) {
            pulse((ServerLevel) level());
        }
    }

    private void pulse(ServerLevel level) {
        float r = radius();
        AABB box = new AABB(getX() - r, getY() - 0.5, getZ() - r, getX() + r, getY() + 2.5, getZ() + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner)) {
            boolean friendly = owner instanceof Player ? e instanceof Player : !(e instanceof Player);
            if (friendly || e.distanceToSqr(getX(), e.getY(), getZ()) > r * r) {
                continue;
            }
            CombatDamage.apply(owner, e, action, attackRating, 0f, 0f, CombatDamageTypes.WEAPON, CombatDamage.Options.cloud(motionValue));
            pulses++;
        }
    }

    private void particles() {
        float r = radius();
        int n = 3 + (int) (r * 2);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            double x = getX() + Math.cos(a) * d, z = getZ() + Math.sin(a) * d;
            // Low-lying smoke and ash, so the cloud reads clearly without blinding anyone standing in it.
            double y = getY() + 0.05 + random.nextDouble() * 0.5;
            level().addParticle(i % 4 == 0 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.004, 0);
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.WHITE_ASH, x, y + 0.3, z, 0, 0.01, 0);
            }
            if (random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, x, getY() + 0.1, z, 0, 0.01, 0);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
