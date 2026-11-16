package dev.vestige.vitals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-player stamina state, stored as a data attachment and synced to its owner. */
public final class Stamina {
    public static final Codec<Stamina> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("current").forGetter(s -> s.current),
            Codec.FLOAT.fieldOf("max").forGetter(s -> s.max)
    ).apply(i, Stamina::new));

    public static final StreamCodec<ByteBuf, Stamina> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, s -> s.current,
            ByteBufCodecs.FLOAT, s -> s.max,
            Stamina::new);

    private float current;
    private float max;
    /** Game time of the last spend; not persisted. */
    private long lastSpendTick = Long.MIN_VALUE / 2;

    public Stamina() {
        this(100f, 100f);
    }

    public Stamina(float current, float max) {
        this.current = current;
        this.max = max;
    }

    public float current() {
        return current;
    }

    public float max() {
        return max;
    }

    public long lastSpendTick() {
        return lastSpendTick;
    }

    void setCurrent(float value) {
        current = Math.max(0f, Math.min(max, value));
    }

    void setMax(float value) {
        max = value;
        current = Math.min(current, max);
    }

    void markSpent(long gameTime) {
        lastSpendTick = gameTime;
    }

    public Stamina copy() {
        Stamina s = new Stamina(current, max);
        s.lastSpendTick = lastSpendTick;
        return s;
    }
}
