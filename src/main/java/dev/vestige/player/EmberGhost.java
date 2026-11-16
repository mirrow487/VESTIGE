package dev.vestige.player;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ember-Ghost (GDD §8.5): the Glim a player carried when they died, waiting where they fell as a
 * smouldering hooded shade. Only its owner can reclaim it, by touching it; dying again first
 * replaces it (see {@link EmberGhosts}).
 */
public class EmberGhost extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Long> GLIM = SynchedEntityData.defineId(EmberGhost.class, EntityDataSerializers.LONG);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ember_ghost.idle");
    /** Reach within which the owner reclaims it. */
    public static final double REACH = 1.4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;

    public EmberGhost(EntityType<? extends EmberGhost> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void configure(ServerPlayer owner, long glim) {
        this.owner = owner.getUUID();
        entityData.set(GLIM, glim);
        setCustomName(Component.translatable("entity.vestige.ember_ghost.named", owner.getGameProfile().getName(), glim));
        setCustomNameVisible(true);
    }

    public long glim() {
        return entityData.get(GLIM);
    }

    public UUID owner() {
        return owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GLIM, 0L);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                        getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 0.2 + random.nextDouble() * 1.4,
                        getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (owner == null || !EmberGhosts.get(level.getServer()).isCurrent(owner, getUUID())) {
            fadeAway(level);
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.isAlive() && player.level() == level && player.distanceToSqr(this) <= REACH * REACH * 2) {
            reclaim(player);
        }
    }

    /** The owner touched it: the Glim returns to their purse. */
    public void reclaim(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        GlimService.add(player, glim());
        EmberGhosts.get(level.getServer()).clear(owner, getUUID());
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.9, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.9, getZ(), 12, 0.2, 0.4, 0.2, 0.08);
        player.displayClientMessage(Component.translatable("message.vestige.glim_reclaimed", glim()), true);
        discard();
    }

    private void fadeAway(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.9, getZ(), 16, 0.2, 0.5, 0.2, 0.02);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            owner = tag.getUUID("owner");
        }
        entityData.set(GLIM, tag.getLong("glim"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putLong("glim", glim());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
