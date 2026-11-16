package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A player's attribute levels (GDD §8.6). Saved, kept on death, synced to the owner. */
public final class CharacterAttributes {
    public static final Codec<CharacterAttributes> CODEC = Codec.unboundedMap(Attribute.CODEC, Codec.INT)
            .xmap(CharacterAttributes::new, c -> Map.copyOf(c.values));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterAttributes> STREAM_CODEC =
            ByteBufCodecs.<io.netty.buffer.ByteBuf, Attribute, Integer, java.util.HashMap<Attribute, Integer>>map(java.util.HashMap::new,
                    ByteBufCodecs.idMapper(i -> Attribute.values()[i], Attribute::ordinal), ByteBufCodecs.VAR_INT)
            .<RegistryFriendlyByteBuf>cast().map(CharacterAttributes::new, c -> new java.util.HashMap<>(c.values));

    private final EnumMap<Attribute, Integer> values = new EnumMap<>(Attribute.class);

    public CharacterAttributes() {
        for (Attribute a : Attribute.values()) {
            values.put(a, AttributeRules.BASE);
        }
    }

    private CharacterAttributes(Map<Attribute, Integer> map) {
        this();
        map.forEach((k, v) -> values.put(k, Math.max(AttributeRules.BASE, Math.min(AttributeRules.MAX, v))));
    }

    public int get(Attribute a) {
        return values.get(a);
    }

    void set(Attribute a, int v) {
        values.put(a, Math.max(AttributeRules.BASE, Math.min(AttributeRules.MAX, v)));
    }

    public Map<Attribute, Integer> asMap() {
        return Map.copyOf(values);
    }

    public int level() {
        return AttributeRules.level(values);
    }
}
