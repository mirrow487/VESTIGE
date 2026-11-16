package dev.vestige.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Base stats of a weapon (GDD §10), loaded from {@code data/<ns>/vestige/weapons/*.json}.
 * One profile may cover several items (e.g. all vanilla iron swords share one).
 *
 * @param items        item ids this profile applies to
 * @param weaponClass  class id: blade, pike, maul, ... (GDD §10.1)
 * @param moveset      combat moveset id (combat module)
 * @param attackRating physical attack rating at +0
 * @param poiseDamage  multiplier on each action's poise damage
 * @param weight       equip weight (GDD §9.3)
 */
public record WeaponProfile(List<ResourceLocation> items, String weaponClass, ResourceLocation moveset,
                            float attackRating, float poiseDamage, float weight) {
    public static final Codec<WeaponProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(WeaponProfile::items),
            Codec.STRING.fieldOf("class").forGetter(WeaponProfile::weaponClass),
            ResourceLocation.CODEC.fieldOf("moveset").forGetter(WeaponProfile::moveset),
            Codec.floatRange(0f, 10_000f).fieldOf("attack_rating").forGetter(WeaponProfile::attackRating),
            Codec.floatRange(0f, 100f).optionalFieldOf("poise_damage", 1f).forGetter(WeaponProfile::poiseDamage),
            Codec.floatRange(0f, 1000f).optionalFieldOf("weight", 1f).forGetter(WeaponProfile::weight)
    ).apply(i, WeaponProfile::new));
}
