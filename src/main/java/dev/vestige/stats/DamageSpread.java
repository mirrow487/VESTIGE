package dev.vestige.stats;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** A value per damage type (attack ratings, defences, absorptions...). Immutable. */
public final class DamageSpread {
    public static final DamageSpread EMPTY = new DamageSpread(new EnumMap<>(DamageType.class));
    public static final Codec<DamageSpread> CODEC = Codec.unboundedMap(DamageType.CODEC, Codec.FLOAT)
            .xmap(DamageSpread::of, s -> Map.copyOf(s.values));

    private final EnumMap<DamageType, Float> values;

    private DamageSpread(EnumMap<DamageType, Float> values) {
        this.values = values;
    }

    public static DamageSpread of(Map<DamageType, Float> map) {
        EnumMap<DamageType, Float> m = new EnumMap<>(DamageType.class);
        map.forEach((k, v) -> {
            if (v != null && v != 0f) {
                m.put(k, v);
            }
        });
        return new DamageSpread(m);
    }

    public static DamageSpread single(DamageType type, float value) {
        return of(Map.of(type, value));
    }

    /** The same value for every type. */
    public static DamageSpread uniform(float value) {
        EnumMap<DamageType, Float> m = new EnumMap<>(DamageType.class);
        for (DamageType t : DamageType.all()) {
            m.put(t, value);
        }
        return new DamageSpread(m);
    }

    public float get(DamageType type) {
        return values.getOrDefault(type, 0f);
    }

    public float total() {
        float t = 0f;
        for (float v : values.values()) {
            t += v;
        }
        return t;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void forEach(BiConsumer<DamageType, Float> f) {
        values.forEach(f);
    }

    public DamageSpread plus(DamageSpread other) {
        EnumMap<DamageType, Float> m = new EnumMap<>(values);
        other.values.forEach((k, v) -> m.merge(k, v, Float::sum));
        return new DamageSpread(m);
    }

    public DamageSpread scaled(float factor) {
        EnumMap<DamageType, Float> m = new EnumMap<>(DamageType.class);
        values.forEach((k, v) -> m.put(k, v * factor));
        return new DamageSpread(m);
    }

    /** Multiplicative stacking of absorptions: 1 - (1-a)(1-b). */
    public DamageSpread stackAbsorption(DamageSpread other) {
        EnumMap<DamageType, Float> m = new EnumMap<>(DamageType.class);
        for (DamageType t : DamageType.all()) {
            float v = 1f - (1f - get(t)) * (1f - other.get(t));
            if (v != 0f) {
                m.put(t, v);
            }
        }
        return new DamageSpread(m);
    }

    public Map<DamageType, Float> asMap() {
        return Map.copyOf(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
