package dev.vestige.combat.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * Readable warning for an enemy attack (GDD §7.12).
 *
 * @param glint white = parriable, red = roll it, grab = red + rising chime
 * @param lead  ticks before the active frames at which the glint flashes
 * @param decal optional ground marker that fills up over the windup
 */
public record Telegraph(Glint glint, int lead, Optional<Decal> decal) {
    public enum Glint implements StringRepresentable {
        NONE, WHITE, RED, GRAB;

        public static final Codec<Glint> CODEC = StringRepresentable.fromEnum(Glint::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Ground marker: CIRCLE uses radius/offset, LINE uses length/width. */
    public record Decal(HitShape.Kind kind, float radius, float offset, float length, float width) {
        public static final Codec<Decal> CODEC = RecordCodecBuilder.create(i -> i.group(
                HitShape.Kind.CODEC.fieldOf("type").forGetter(Decal::kind),
                Codec.FLOAT.optionalFieldOf("radius", 2f).forGetter(Decal::radius),
                Codec.FLOAT.optionalFieldOf("offset", 0f).forGetter(Decal::offset),
                Codec.FLOAT.optionalFieldOf("length", 4f).forGetter(Decal::length),
                Codec.FLOAT.optionalFieldOf("width", 1f).forGetter(Decal::width)
        ).apply(i, Decal::new));
    }

    public static final Codec<Telegraph> CODEC = RecordCodecBuilder.create(i -> i.group(
            Glint.CODEC.optionalFieldOf("glint", Glint.NONE).forGetter(Telegraph::glint),
            Codec.intRange(0, 100).optionalFieldOf("lead", 6).forGetter(Telegraph::lead),
            Decal.CODEC.optionalFieldOf("decal").forGetter(Telegraph::decal)
    ).apply(i, Telegraph::new));
}
