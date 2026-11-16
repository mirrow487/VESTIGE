package dev.vestige.combat.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pose sequence laid out on a concrete action's tick timeline. The same object is sampled
 * directly for enemy models and converted to playerAnimator keyframes for players, so both share
 * one animation definition. Pure; unit-tested.
 */
public final class PoseTimeline {
    /** A keyframe at an absolute tick. The ease shapes the segment leading into it. */
    public record Frame(float tick, Map<Channel, Float> values, Ease ease) {}

    private final List<Frame> frames;
    private final Set<Channel> channels;
    private final int length;

    private PoseTimeline(List<Frame> frames, Set<Channel> channels, int length) {
        this.frames = frames;
        this.channels = channels;
        this.length = length;
    }

    /**
     * Lays {@code keys} onto an action of the given phase lengths. Adds a neutral frame at tick 0
     * and at the end so every channel starts and finishes at rest; channels a key omits are 0.
     */
    public static PoseTimeline build(List<PoseKey> keys, int windup, int active, int recovery) {
        int total = windup + active + recovery;
        Set<Channel> used = EnumSet.noneOf(Channel.class);
        keys.forEach(k -> used.addAll(k.values().keySet()));
        List<Frame> frames = new ArrayList<>();
        frames.add(new Frame(0f, Map.of(), Ease.LINEAR));
        for (PoseKey k : keys) {
            float tick = Math.max(0f, Math.min(total, k.anchor().tick(windup, active, recovery, k.fraction())));
            frames.add(new Frame(tick, k.values(), k.ease()));
        }
        frames.sort(Comparator.comparingDouble(Frame::tick));
        if (frames.get(frames.size() - 1).tick() < total) {
            frames.add(new Frame(total, Map.of(), Ease.INOUTSINE));
        }
        return new PoseTimeline(List.copyOf(frames), used.isEmpty() ? Set.of() : EnumSet.copyOf(used), total);
    }

    public List<Frame> frames() {
        return frames;
    }

    public Set<Channel> channels() {
        return channels;
    }

    public int length() {
        return length;
    }

    /** Value of a channel at a (fractional) tick. */
    public float sample(Channel c, float tick) {
        if (frames.isEmpty()) {
            return 0f;
        }
        Frame prev = frames.get(0);
        for (int i = 1; i < frames.size(); i++) {
            Frame next = frames.get(i);
            if (tick <= next.tick()) {
                float span = next.tick() - prev.tick();
                float f = span <= 0f ? 1f : (tick - prev.tick()) / span;
                float a = prev.values().getOrDefault(c, 0f);
                float b = next.values().getOrDefault(c, 0f);
                return a + (b - a) * next.ease().apply(f);
            }
            prev = next;
        }
        return prev.values().getOrDefault(c, 0f);
    }

    /** All channel values at a tick (for model posing). */
    public Map<Channel, Float> sampleAll(float tick) {
        Map<Channel, Float> out = new EnumMap<>(Channel.class);
        for (Channel c : channels) {
            out.put(c, sample(c, tick));
        }
        return out;
    }
}
