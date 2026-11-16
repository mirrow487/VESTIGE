package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestige.stats.Rarity;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** An armor piece's stats (GDD §11), {@code data/<ns>/vestige/armor/*.json}. */
public record ArmorProfile(List<ResourceLocation> items, String slot, Optional<String> set, float weight, StatMods mods, Rarity rarity) {
    public static final Codec<ArmorProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(ArmorProfile::items),
            Codec.STRING.fieldOf("slot").forGetter(ArmorProfile::slot),
            Codec.STRING.optionalFieldOf("set").forGetter(ArmorProfile::set),
            Codec.FLOAT.optionalFieldOf("weight", 0f).forGetter(ArmorProfile::weight),
            StatMods.CODEC.optionalFieldOf("stats", StatMods.NONE).forGetter(ArmorProfile::mods),
            Rarity.CODEC.optionalFieldOf("rarity", Rarity.COMMON).forGetter(ArmorProfile::rarity)
    ).apply(i, ArmorProfile::new));
}
