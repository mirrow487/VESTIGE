package dev.vestige.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Off-hand shield stats (GDD §7.7, §10.1), from {@code data/<ns>/vestige/shields/*.json}.
 *
 * @param kind       buckler: the guard button parries; kite/bulwark: the guard button blocks
 * @param absorption fraction of physical damage stopped while blocking
 * @param stability  0–100; reduces stamina lost when blocking
 */
public record ShieldProfile(List<ResourceLocation> items, Kind kind, float absorption, float stability, float weight) {
    public enum Kind implements StringRepresentable {
        BUCKLER, KITE, BULWARK;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Codec<ShieldProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(ShieldProfile::items),
            Kind.CODEC.fieldOf("kind").forGetter(ShieldProfile::kind),
            Codec.floatRange(0f, 1f).fieldOf("absorption").forGetter(ShieldProfile::absorption),
            Codec.floatRange(0f, 100f).fieldOf("stability").forGetter(ShieldProfile::stability),
            Codec.floatRange(0f, 100f).optionalFieldOf("weight", 2f).forGetter(ShieldProfile::weight)
    ).apply(i, ShieldProfile::new));
}
