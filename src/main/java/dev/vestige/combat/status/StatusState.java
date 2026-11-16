package dev.vestige.combat.status;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestige.stats.StatusType;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Status buildup meters (0-100) and running statuses of one living entity (GDD §7.10). Saved so a
 * Voidbite survives relogging until the player rests.
 */
public final class StatusState {
    public static final Codec<StatusState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.listOf().fieldOf("meters").forGetter(s -> s.metersList()),
            Codec.INT.listOf().fieldOf("timers").forGetter(s -> s.timersList()),
            Codec.BOOL.optionalFieldOf("voidbite", false).forGetter(s -> s.voidbitten)
    ).apply(i, StatusState::new));
    public static final StreamCodec<ByteBuf, StatusState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), StatusState::metersList,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), StatusState::timersList,
            ByteBufCodecs.BOOL, s -> s.voidbitten, StatusState::new);

    final float[] meters = new float[StatusType.values().length];
    /** Ticks left on timed statuses (Scorch, Hushed). */
    final int[] timers = new int[StatusType.values().length];
    boolean voidbitten;
    long lastBuildup;

    public StatusState() {}

    private StatusState(List<Float> meters, List<Integer> timers, boolean voidbitten) {
        for (int i = 0; i < Math.min(meters.size(), this.meters.length); i++) {
            this.meters[i] = meters.get(i);
        }
        for (int i = 0; i < Math.min(timers.size(), this.timers.length); i++) {
            this.timers[i] = timers.get(i);
        }
        this.voidbitten = voidbitten;
    }

    private List<Float> metersList() {
        Float[] out = new Float[meters.length];
        for (int i = 0; i < meters.length; i++) {
            out[i] = meters[i];
        }
        return List.of(out);
    }

    private List<Integer> timersList() {
        Integer[] out = new Integer[timers.length];
        for (int i = 0; i < timers.length; i++) {
            out[i] = timers[i];
        }
        return List.of(out);
    }

    public float meter(StatusType t) {
        return meters[t.ordinal()];
    }

    public int timer(StatusType t) {
        return timers[t.ordinal()];
    }

    public boolean voidbitten() {
        return voidbitten;
    }

    public boolean idle() {
        for (int i = 0; i < meters.length; i++) {
            if (meters[i] > 0f || timers[i] > 0) {
                return false;
            }
        }
        return true;
    }
}
