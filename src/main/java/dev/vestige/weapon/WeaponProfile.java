package dev.vestige.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.DamageType;
import dev.vestige.stats.Grade;
import dev.vestige.stats.Rarity;
import dev.vestige.stats.StatusType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Base stats of a weapon (GDD §10), loaded from {@code data/<ns>/vestige/weapons/*.json}.
 * One profile may cover several items (e.g. all vanilla iron swords share one).
 *
 * @param items        item ids this profile applies to
 * @param weaponClass  class id: blade, pike, maul, ... (GDD §10.1)
 * @param moveset      combat moveset id (combat module)
 * @param attackRating physical attack rating at +0 (used when {@code damage} is absent)
 * @param poiseDamage  multiplier on each action's poise damage
 * @param weight       equip weight (GDD §9.3)
 * @param damage       attack rating per damage type (GDD §7.10); physical goes to the class's type
 * @param scaling      attribute scaling grades (GDD §8.6)
 * @param requirements minimum attributes; below them attack rating ×0.6
 * @param status       status buildup per hit
 * @param rarity       display rarity
 */
public record WeaponProfile(List<ResourceLocation> items, String weaponClass, ResourceLocation moveset,
                            float attackRating, float poiseDamage, float weight, Optional<DamageSpread> damage,
                            Map<Attribute, Grade> scaling, Map<Attribute, Integer> requirements, Map<StatusType, Float> status,
                            Rarity rarity) {
    public static final Codec<WeaponProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(WeaponProfile::items),
            Codec.STRING.fieldOf("class").forGetter(WeaponProfile::weaponClass),
            ResourceLocation.CODEC.fieldOf("moveset").forGetter(WeaponProfile::moveset),
            Codec.floatRange(0f, 10_000f).fieldOf("attack_rating").forGetter(WeaponProfile::attackRating),
            Codec.floatRange(0f, 100f).optionalFieldOf("poise_damage", 1f).forGetter(WeaponProfile::poiseDamage),
            Codec.floatRange(0f, 1000f).optionalFieldOf("weight", 1f).forGetter(WeaponProfile::weight),
            DamageSpread.CODEC.optionalFieldOf("damage").forGetter(WeaponProfile::damage),
            Codec.unboundedMap(Attribute.CODEC, Grade.CODEC).optionalFieldOf("scaling", Map.of()).forGetter(WeaponProfile::scaling),
            Codec.unboundedMap(Attribute.CODEC, Codec.intRange(1, 99)).optionalFieldOf("requirements", Map.of()).forGetter(WeaponProfile::requirements),
            Codec.unboundedMap(StatusType.CODEC, Codec.FLOAT).optionalFieldOf("status", Map.of()).forGetter(WeaponProfile::status),
            Rarity.CODEC.optionalFieldOf("rarity", Rarity.COMMON).forGetter(WeaponProfile::rarity)
    ).apply(i, WeaponProfile::new));

    /** The class's own physical type (an action may override it, e.g. a sword's thrust pierces). */
    public DamageType physicalType() {
        return switch (weaponClass) {
            case "pike", "longbow" -> DamageType.PIERCE;
            case "maul", "censer" -> DamageType.STRIKE;
            case "bell" -> DamageType.RESONANCE;
            default -> DamageType.SLASH;
        };
    }

    /** Base attack rating per type at +0, before attribute scaling. */
    public DamageSpread baseDamage() {
        return damage.orElseGet(() -> DamageSpread.single(physicalType(), attackRating));
    }
}
