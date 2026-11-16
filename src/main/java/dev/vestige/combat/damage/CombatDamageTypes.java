package dev.vestige.combat.damage;

import dev.vestige.VestigeIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * VESTIGE damage types (data: {@code data/vestige/damage_type}). Both are tagged to bypass vanilla
 * armor, cooldown and knockback (VESTIGE handles those) and {@code vestige:native_scale}.
 */
public final class CombatDamageTypes {
    public static final ResourceKey<DamageType> WEAPON = ResourceKey.create(Registries.DAMAGE_TYPE, VestigeIds.id("weapon"));
    public static final ResourceKey<DamageType> ENEMY_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE, VestigeIds.id("enemy_attack"));

    private CombatDamageTypes() {}

    public static DamageSource source(Entity attacker, ResourceKey<DamageType> type) {
        return attacker.level().damageSources().source(type, attacker);
    }
}
