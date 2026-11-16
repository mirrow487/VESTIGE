package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestige.stats.Rarity;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A ring, talisman or relic (GDD §12), {@code data/<ns>/vestige/accessories/*.json}. Rings shape
 * numbers, talismans change rules, relics add verbs. Accessories of the same {@code family} cannot
 * be worn together (e.g. two roll modifiers).
 */
public record AccessoryProfile(List<ResourceLocation> items, EquipSlot.Kind kind, Optional<String> family, float weight, StatMods mods,
                               Rarity rarity, String description) {
    public static final Codec<EquipSlot.Kind> KIND_CODEC = Codec.STRING.xmap(s -> EquipSlot.Kind.valueOf(s.toUpperCase(java.util.Locale.ROOT)),
            k -> k.name().toLowerCase(java.util.Locale.ROOT));
    public static final Codec<AccessoryProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(AccessoryProfile::items),
            KIND_CODEC.fieldOf("kind").forGetter(AccessoryProfile::kind),
            Codec.STRING.optionalFieldOf("family").forGetter(AccessoryProfile::family),
            Codec.FLOAT.optionalFieldOf("weight", 0.5f).forGetter(AccessoryProfile::weight),
            StatMods.CODEC.optionalFieldOf("stats", StatMods.NONE).forGetter(AccessoryProfile::mods),
            Rarity.CODEC.optionalFieldOf("rarity", Rarity.UNCOMMON).forGetter(AccessoryProfile::rarity),
            Codec.STRING.optionalFieldOf("description", "").forGetter(AccessoryProfile::description)
    ).apply(i, AccessoryProfile::new));
}
