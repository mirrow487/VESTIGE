package dev.vestige.combat.resonance;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-player Resonance value (attachment; synced to its owner). */
public final class Resonance {
    public static final Codec<Resonance> CODEC = Codec.FLOAT.xmap(Resonance::new, r -> r.value);
    public static final StreamCodec<ByteBuf, Resonance> STREAM_CODEC = ByteBufCodecs.FLOAT.map(Resonance::new, r -> r.value);

    private float value;

    public Resonance() {
        this(0f);
    }

    public Resonance(float value) {
        this.value = ResonanceRules.clamp(value);
    }

    public float value() {
        return value;
    }

    void set(float v) {
        value = ResonanceRules.clamp(v);
    }
}
