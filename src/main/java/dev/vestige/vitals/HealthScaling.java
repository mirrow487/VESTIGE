package dev.vestige.vitals;

import dev.vestige.VestigeIds;
import dev.vestige.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * GDD §8.1: every creature's health is multiplied (default ×5) so VESTIGE combat numbers have
 * room to breathe; vanilla damage and healing are scaled by the same factor so vanilla balance is
 * preserved. VESTIGE's own damage types (tag {@code vestige:native_scale}) are already in the
 * scaled units and pass through unchanged.
 */
public final class HealthScaling {
    public static final ResourceLocation MODIFIER_ID = VestigeIds.id("health_scale");
    public static final TagKey<DamageType> NATIVE_SCALE = TagKey.create(Registries.DAMAGE_TYPE, VestigeIds.id("native_scale"));

    private HealthScaling() {}

    public static double scale() {
        return ServerConfig.HEALTH_SCALE.get();
    }

    /** Entities that define their health directly in VESTIGE units (custom enemies) opt out. */
    public interface NativeHealth {}

    /**
     * Scales a vanilla damage or heal amount, saturating instead of overflowing: {@code /kill}
     * deals {@code Float.MAX_VALUE}, and ×5 would become Infinity, which vanilla's armor math
     * turns into NaN health (the entity would never die).
     */
    public static float scaleAmount(float amount) {
        double scaled = amount * scale();
        return scaled >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) scaled;
    }

    public static boolean applies(LivingEntity entity) {
        return !(entity instanceof NativeHealth) && !(entity instanceof ArmorStand);
    }

    /**
     * Applies or updates the max-health modifier. Keeps the current health fraction when the
     * modifier changes, and fills new entities to full.
     */
    public static void apply(LivingEntity entity, boolean freshlySpawned) {
        if (!applies(entity)) {
            return;
        }
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double amount = scale() - 1.0;
        AttributeModifier existing = maxHealth.getModifier(MODIFIER_ID);
        if (existing != null && existing.amount() == amount) {
            return;
        }
        float fraction = entity.getMaxHealth() > 0 ? entity.getHealth() / entity.getMaxHealth() : 1f;
        maxHealth.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        entity.setHealth(freshlySpawned && existing == null ? entity.getMaxHealth() : fraction * entity.getMaxHealth());
    }
}
