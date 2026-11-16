package dev.vestige.waylight;

import dev.vestige.VestigeIds;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Waylight menu traffic (ARCHITECTURE §6: WaylightTravel). */
public final class WaylightPayloads {
    private WaylightPayloads() {}

    /** A travel destination as the menu shows it. */
    public record Destination(GlobalPos pos, String name, String dimension, int distance) {
        public static final StreamCodec<ByteBuf, Destination> CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Destination::pos, ByteBufCodecs.STRING_UTF8, Destination::name,
                ByteBufCodecs.STRING_UTF8, Destination::dimension, ByteBufCodecs.VAR_INT, Destination::distance, Destination::new);
    }

    /** S→C: open the menu of the Waylight at {@code pos}; refusals are rule names (NONE = allowed). */
    public record Open(BlockPos pos, String name, String restRefusal, String travelRefusal, List<Destination> destinations)
            implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(VestigeIds.id("waylight_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos, ByteBufCodecs.STRING_UTF8, Open::name, ByteBufCodecs.STRING_UTF8, Open::restRefusal,
                ByteBufCodecs.STRING_UTF8, Open::travelRefusal, Destination.CODEC.apply(ByteBufCodecs.list(64)), Open::destinations, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C→S: rest at, or travel from, the Waylight at {@code pos}. */
    public record Act(BlockPos pos, boolean travel, GlobalPos target) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(VestigeIds.id("waylight_act"));
        public static final StreamCodec<ByteBuf, Act> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.BOOL, Act::travel, GlobalPos.STREAM_CODEC, Act::target, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
