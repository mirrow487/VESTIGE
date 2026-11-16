package dev.vestige.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tolling Ram (GDD §18.1, Plains): a bell-collared ram of the Bellwether's herd. Its lesson is
 * rolling sideways: the Butt Charge is a long, straight, red-glint run telegraphed by a pawing
 * scrape and a line on the ground, and it only aims during the scrape. Up close it rings its bell
 * into a low Toll Ring (jump or roll it) or butts with a parriable headbutt. Charges end in a
 * skid that leaves it open.
 */
public class TollingRam extends AnimatedMob {
    public TollingRam(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** Grazes on open grass in daylight, unlike the night-bound Hollowed. */
    public static boolean checkSpawn(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8
                && checkMobSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected double walkSpeed(float limbSwingAmount) {
        return Math.max(0.5, Math.min(1.8, limbSwingAmount * 2.0));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.GOAT_STEP, 0.25f, 0.8f);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7f;
    }
}
