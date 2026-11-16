package dev.vestige.player;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The Glim purse (GDD §23.1): a number, not an item. Lost to an Ember-Ghost on death (§8.5). */
public final class Glim {
    public static final Codec<Glim> CODEC = Codec.LONG.xmap(Glim::new, g -> g.amount);
    public static final StreamCodec<ByteBuf, Glim> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(Glim::new, g -> g.amount);

    private long amount;

    public Glim() {}

    private Glim(long amount) {
        this.amount = Math.max(0, amount);
    }

    public long amount() {
        return amount;
    }

    void set(long value) {
        amount = Math.max(0, value);
    }
}
