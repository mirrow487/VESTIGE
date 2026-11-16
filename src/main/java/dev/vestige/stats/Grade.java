package dev.vestige.stats;

import com.mojang.serialization.Codec;

/** Weapon scaling grades (Souls-style letters) and their coefficients. */
public enum Grade {
    S(1.40f), A(1.10f), B(0.80f), C(0.55f), D(0.35f), E(0.15f);

    public static final Codec<Grade> CODEC = Codec.STRING.xmap(s -> Grade.valueOf(s.toUpperCase(java.util.Locale.ROOT)), Grade::name);
    public final float coefficient;

    Grade(float coefficient) {
        this.coefficient = coefficient;
    }
}
