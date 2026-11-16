package dev.vestige.combat.anim;

import java.util.EnumMap;
import java.util.Map;

/** One keyframe: channel values reached at (anchor, fraction), approached with {@code ease}. */
public record PoseKey(Anchor anchor, float fraction, Map<Channel, Float> values, Ease ease) {
    /** Fluent builder: {@code key(ACTIVE, 0, OUTQUAD).set(RIGHT_ARM_PITCH, -90)}. */
    public static Builder key(Anchor anchor, float fraction, Ease ease) {
        return new Builder(anchor, fraction, ease);
    }

    public static final class Builder {
        private final Anchor anchor;
        private final float fraction;
        private final Ease ease;
        private final Map<Channel, Float> values = new EnumMap<>(Channel.class);

        Builder(Anchor anchor, float fraction, Ease ease) {
            this.anchor = anchor;
            this.fraction = fraction;
            this.ease = ease;
        }

        public Builder set(Channel c, float v) {
            values.put(c, v);
            return this;
        }

        public PoseKey build() {
            return new PoseKey(anchor, fraction, Map.copyOf(values), ease);
        }
    }
}
