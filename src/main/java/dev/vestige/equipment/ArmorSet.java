package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A set bonus (GDD §11): 2 pieces give a minor bonus, 4 pieces the major one. {@code vestige/armor_sets}. */
public record ArmorSet(String name, StatMods twoPiece, String twoText, StatMods fourPiece, String fourText) {
    public static final Codec<ArmorSet> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(ArmorSet::name),
            StatMods.CODEC.optionalFieldOf("two_piece", StatMods.NONE).forGetter(ArmorSet::twoPiece),
            Codec.STRING.optionalFieldOf("two_text", "").forGetter(ArmorSet::twoText),
            StatMods.CODEC.optionalFieldOf("four_piece", StatMods.NONE).forGetter(ArmorSet::fourPiece),
            Codec.STRING.optionalFieldOf("four_text", "").forGetter(ArmorSet::fourText)
    ).apply(i, ArmorSet::new));
}
