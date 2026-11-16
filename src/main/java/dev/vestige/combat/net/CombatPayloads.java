package dev.vestige.combat.net;

import dev.vestige.VestigeIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Combat network messages (ARCHITECTURE §6). */
public final class CombatPayloads {
    private CombatPayloads() {}

    /** C→S: a combat button event. {@code dirYaw} is NaN when no direction applies. */
    public record Input(int seq, int input, float dirYaw) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(VestigeIds.id("combat_input"));
        public static final StreamCodec<ByteBuf, Input> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::seq, ByteBufCodecs.VAR_INT, Input::input, ByteBufCodecs.FLOAT, Input::dirYaw, Input::new);

        @Override
        public Type<Input> type() {
            return TYPE;
        }
    }

    /** S→C: an entity started an action (drives animation and telegraphs). */
    public record ActionStart(int entityId, String action, int seq, float dirYaw, boolean holding) implements CustomPacketPayload {
        public static final Type<ActionStart> TYPE = new Type<>(VestigeIds.id("action_start"));
        public static final StreamCodec<ByteBuf, ActionStart> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionStart::entityId, ByteBufCodecs.STRING_UTF8, ActionStart::action,
                ByteBufCodecs.VAR_INT, ActionStart::seq, ByteBufCodecs.FLOAT, ActionStart::dirYaw,
                ByteBufCodecs.BOOL, ActionStart::holding, ActionStart::new);

        @Override
        public Type<ActionStart> type() {
            return TYPE;
        }
    }

    /** Why an action ended early. */
    public enum EndReason { REJECTED, CANCELLED, INTERRUPTED }

    /** S→C: an action ended early. {@code seq} identifies a rejected predicted input. */
    public record ActionEnd(int entityId, int reason, int seq) implements CustomPacketPayload {
        public static final Type<ActionEnd> TYPE = new Type<>(VestigeIds.id("action_end"));
        public static final StreamCodec<ByteBuf, ActionEnd> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionEnd::entityId, ByteBufCodecs.VAR_INT, ActionEnd::reason, ByteBufCodecs.VAR_INT, ActionEnd::seq, ActionEnd::new);

        public EndReason endReason() {
            return EndReason.values()[Math.max(0, Math.min(EndReason.values().length - 1, reason))];
        }

        @Override
        public Type<ActionEnd> type() {
            return TYPE;
        }
    }

    /** S→C: a chargeable action started or stopped holding its charge. */
    public record ActionHold(int entityId, boolean holding) implements CustomPacketPayload {
        public static final Type<ActionHold> TYPE = new Type<>(VestigeIds.id("action_hold"));
        public static final StreamCodec<ByteBuf, ActionHold> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionHold::entityId, ByteBufCodecs.BOOL, ActionHold::holding, ActionHold::new);

        @Override
        public Type<ActionHold> type() {
            return TYPE;
        }
    }

    /** S→C: a hit landed (hitstop, particles, camera shake, sound). */
    public record HitFeedback(int attackerId, int targetId, float x, float y, float z, int hitstop, boolean heavy, boolean critical)
            implements CustomPacketPayload {
        public static final Type<HitFeedback> TYPE = new Type<>(VestigeIds.id("hit_feedback"));
        public static final StreamCodec<ByteBuf, HitFeedback> CODEC = new StreamCodec<>() {
            @Override
            public HitFeedback decode(ByteBuf buf) {
                return new HitFeedback(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean(), buf.readBoolean());
            }

            @Override
            public void encode(ByteBuf buf, HitFeedback p) {
                ByteBufCodecs.VAR_INT.encode(buf, p.attackerId);
                ByteBufCodecs.VAR_INT.encode(buf, p.targetId);
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                ByteBufCodecs.VAR_INT.encode(buf, p.hitstop);
                buf.writeBoolean(p.heavy);
                buf.writeBoolean(p.critical);
            }
        };

        @Override
        public Type<HitFeedback> type() {
            return TYPE;
        }
    }

    /** One-off combat events for feedback. */
    public enum EventKind { PERFECT_DODGE, STAGGER_BREAK }

    /** S→C: a combat event worth signalling (e.g. perfect dodge). */
    public record Event(int kind, int entityId) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(VestigeIds.id("combat_event"));
        public static final StreamCodec<ByteBuf, Event> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Event::kind, ByteBufCodecs.VAR_INT, Event::entityId, Event::new);

        public EventKind eventKind() {
            return EventKind.values()[Math.max(0, Math.min(EventKind.values().length - 1, kind))];
        }

        @Override
        public Type<Event> type() {
            return TYPE;
        }
    }
}
