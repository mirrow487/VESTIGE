package dev.vestige.stats;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Item rarity (GDD §23.2): name colour and tile accent in the UI. */
public enum Rarity implements StringRepresentable {
    COMMON(0xFFB4B0A8), UNCOMMON(0xFF7FBF6A), RARE(0xFF6A9FD8), RELIC(0xFFB07AD8), WICK(0xFFE08A3C);

    public static final Codec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);
    public final int color;

    Rarity(int color) {
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
