package dev.vestige.entity.mob;

import dev.vestige.entity.VestigeMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A VESTIGE enemy drawn with GeckoLib (ADR-011 rev. 2). Keyframed body language (idle, walk,
 * death) comes from {@code animations/entity/<type>.animation.json}; attack poses are layered on top
 * by the client model from the shared combat timeline, so they stay frame-locked to hitboxes.
 */
public abstract class AnimatedMob extends VestigeMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation death;

    protected AnimatedMob(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        String prefix = "animation." + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + ".";
        idle = RawAnimation.begin().thenLoop(prefix + "idle");
        walk = RawAnimation.begin().thenLoop(prefix + "walk");
        death = RawAnimation.begin().thenPlayAndHold(prefix + "death");
    }

    /** Walk cycle speed for a given limb swing (gait tuning per creature). */
    protected double walkSpeed(float limbSwingAmount) {
        return Math.max(0.6, Math.min(1.6, limbSwingAmount * 2.4));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 6, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(death);
            }
            if (state.isMoving()) {
                state.getController().setAnimationSpeed(walkSpeed(state.getLimbSwingAmount()));
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
}
