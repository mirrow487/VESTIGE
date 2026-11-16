package dev.vestige.entity.mob;

import dev.vestige.entity.VestigeMob;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Hollowed (GDD §18.1): villagers emptied by the Pale. Fodder that teaches spacing and rolling:
 * a white-glint swipe with an optional backhand follow-up and a red-glint lunging grab. The brute
 * variant adds a ground slam with a decal and a wide sweep, and has hyperarmor on both.
 */
public class Hollowed extends VestigeMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation death;

    public Hollowed(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        String prefix = "animation." + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + ".";
        idle = RawAnimation.begin().thenLoop(prefix + "idle");
        walk = RawAnimation.begin().thenLoop(prefix + "walk");
        death = RawAnimation.begin().thenPlayAndHold(prefix + "death");
    }

    /**
     * Keyframed body language (GeckoLib): breathing idle, shambling walk, death collapse. Attack
     * poses are layered on top by the client model from the shared combat timeline, so they stay
     * frame-locked to hitboxes (ADR-007).
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 6, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(death);
            }
            if (state.isMoving()) {
                state.getController().setAnimationSpeed(Math.max(0.6, Math.min(1.6, state.getLimbSwingAmount() * 2.4)));
                return state.setAndContinue(walk);
            }
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static AttributeSupplier.Builder attributes(double speed, double knockbackResistance) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75f;
    }
}
