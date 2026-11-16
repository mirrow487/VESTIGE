package dev.vestige.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The Wick Flask (GDD §8.4): charges left, maximum charges (3 + Flask Wicks) and Kindling level. */
public final class Flask {
    public static final Codec<Flask> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("charges").forGetter(Flask::charges),
            Codec.INT.fieldOf("max").forGetter(Flask::max),
            Codec.INT.optionalFieldOf("kindling", 0).forGetter(Flask::kindling)
    ).apply(i, Flask::new));
    public static final StreamCodec<ByteBuf, Flask> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Flask::charges, ByteBufCodecs.VAR_INT, Flask::max, ByteBufCodecs.VAR_INT, Flask::kindling, Flask::new);

    private int charges;
    private int max;
    private int kindling;

    public Flask() {
        this(FlaskRules.BASE_CHARGES, FlaskRules.BASE_CHARGES, 0);
    }

    private Flask(int charges, int max, int kindling) {
        this.max = Math.max(1, Math.min(FlaskRules.MAX_CHARGES, max));
        this.charges = Math.max(0, Math.min(this.max, charges));
        this.kindling = Math.max(0, Math.min(FlaskRules.MAX_KINDLING, kindling));
    }

    public int charges() {
        return charges;
    }

    public int max() {
        return max;
    }

    public int kindling() {
        return kindling;
    }

    void setCharges(int value) {
        charges = Math.max(0, Math.min(max, value));
    }

    void setMax(int value) {
        max = Math.max(1, Math.min(FlaskRules.MAX_CHARGES, value));
        charges = Math.min(charges, max);
    }

    void setKindling(int value) {
        kindling = Math.max(0, Math.min(FlaskRules.MAX_KINDLING, value));
    }
}
