package dev.vestige.combat.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Damage volume of an action, relative to the attacker (GDD §7.4). Angles are degrees, 0 = straight
 * ahead, positive = attacker's right. Heights are relative to the attacker's feet.
 *
 * <ul>
 *   <li>ARC: horizontal sweep from {@code from} to {@code to} over the active ticks, out to {@code reach}.</li>
 *   <li>THRUST: capsule straight ahead, {@code reach} long, {@code radius} thick.</li>
 *   <li>CIRCLE: disc of {@code radius} centred {@code offset} blocks ahead.</li>
 * </ul>
 */
public record HitShape(Kind kind, float reach, float from, float to, float radius, float offset, float yMin, float yMax) {
    public enum Kind implements StringRepresentable {
        ARC, THRUST, CIRCLE;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Codec<HitShape> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("type").forGetter(HitShape::kind),
            Codec.FLOAT.optionalFieldOf("reach", 2.5f).forGetter(HitShape::reach),
            Codec.FLOAT.optionalFieldOf("from", -60f).forGetter(HitShape::from),
            Codec.FLOAT.optionalFieldOf("to", 60f).forGetter(HitShape::to),
            Codec.FLOAT.optionalFieldOf("radius", 0.6f).forGetter(HitShape::radius),
            Codec.FLOAT.optionalFieldOf("offset", 0f).forGetter(HitShape::offset),
            Codec.FLOAT.optionalFieldOf("y_min", 0f).forGetter(HitShape::yMin),
            Codec.FLOAT.optionalFieldOf("y_max", 2.2f).forGetter(HitShape::yMax)
    ).apply(i, HitShape::new));
}
