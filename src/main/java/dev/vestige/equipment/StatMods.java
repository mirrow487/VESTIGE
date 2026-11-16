package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestige.stats.DamageSpread;
import java.util.List;
import java.util.Optional;

/**
 * What one source (an armor piece, an accessory, a set bonus) adds to a {@code StatSheet}. All
 * fields optional in JSON. {@code perk} switches on a rule-changing effect (see {@code Perks}).
 */
public record StatMods(float health, float stamina, float staminaRegen, float poise, float capacity, float burdenThreshold,
                       DamageSpread absorption, DamageSpread typeBonus, float damageBonus, float resonanceGain, float flaskHeal,
                       float statusResist, float glimGain, float sprintSpeed, float sprintStaminaFactor, Optional<String> perk) {
    public static final StatMods NONE = new StatMods(0, 0, 0, 0, 0, 0, DamageSpread.EMPTY, DamageSpread.EMPTY, 0, 0, 0, 0, 0, 0, 0,
            Optional.empty());

    private record A(float health, float stamina, float staminaRegen, float poise, float capacity, float burdenThreshold) {}

    private record B(DamageSpread absorption, DamageSpread typeBonus, float damageBonus, float resonanceGain, float flaskHeal,
                     float statusResist, float glimGain, float sprintSpeed, float sprintStaminaFactor, Optional<String> perk) {}

    private static final com.mojang.serialization.MapCodec<A> A_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("health", 0f).forGetter(A::health),
            Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(A::stamina),
            Codec.FLOAT.optionalFieldOf("stamina_regen", 0f).forGetter(A::staminaRegen),
            Codec.FLOAT.optionalFieldOf("poise", 0f).forGetter(A::poise),
            Codec.FLOAT.optionalFieldOf("capacity", 0f).forGetter(A::capacity),
            Codec.FLOAT.optionalFieldOf("burden_threshold", 0f).forGetter(A::burdenThreshold)
    ).apply(i, A::new));

    private static final com.mojang.serialization.MapCodec<B> B_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageSpread.CODEC.optionalFieldOf("absorption", DamageSpread.EMPTY).forGetter(B::absorption),
            DamageSpread.CODEC.optionalFieldOf("type_bonus", DamageSpread.EMPTY).forGetter(B::typeBonus),
            Codec.FLOAT.optionalFieldOf("damage_bonus", 0f).forGetter(B::damageBonus),
            Codec.FLOAT.optionalFieldOf("resonance_gain", 0f).forGetter(B::resonanceGain),
            Codec.FLOAT.optionalFieldOf("flask_heal", 0f).forGetter(B::flaskHeal),
            Codec.FLOAT.optionalFieldOf("status_resist", 0f).forGetter(B::statusResist),
            Codec.FLOAT.optionalFieldOf("glim_gain", 0f).forGetter(B::glimGain),
            Codec.FLOAT.optionalFieldOf("sprint_speed", 0f).forGetter(B::sprintSpeed),
            Codec.FLOAT.optionalFieldOf("sprint_stamina_factor", 0f).forGetter(B::sprintStaminaFactor),
            Codec.STRING.optionalFieldOf("perk").forGetter(B::perk)
    ).apply(i, B::new));

    public static final com.mojang.serialization.MapCodec<StatMods> MAP_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            A_CODEC.forGetter(m -> new A(m.health, m.stamina, m.staminaRegen, m.poise, m.capacity, m.burdenThreshold)),
            B_CODEC.forGetter(m -> new B(m.absorption, m.typeBonus, m.damageBonus, m.resonanceGain, m.flaskHeal, m.statusResist,
                    m.glimGain, m.sprintSpeed, m.sprintStaminaFactor, m.perk))
    ).apply(i, (a, b) -> new StatMods(a.health, a.stamina, a.staminaRegen, a.poise, a.capacity, a.burdenThreshold, b.absorption,
            b.typeBonus, b.damageBonus, b.resonanceGain, b.flaskHeal, b.statusResist, b.glimGain, b.sprintSpeed, b.sprintStaminaFactor, b.perk)));
    public static final Codec<StatMods> CODEC = MAP_CODEC.codec();

    /** Plain-language lines for tooltips and the stat panel. */
    public List<String> describe() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (health != 0) out.add(fmt(health) + " max HP");
        if (stamina != 0) out.add(fmt(stamina) + " stamina");
        if (staminaRegen != 0) out.add(pct(staminaRegen) + " stamina regeneration");
        if (poise != 0) out.add(fmt(poise) + " poise");
        if (capacity != 0) out.add(fmt(capacity) + " equip capacity");
        if (burdenThreshold != 0) out.add(pct(burdenThreshold) + " burden thresholds");
        absorption.forEach((t, v) -> out.add(pct(v) + " " + name(t) + " absorption"));
        typeBonus.forEach((t, v) -> out.add(pct(v) + " " + name(t) + " damage"));
        if (damageBonus != 0) out.add(pct(damageBonus) + " damage");
        if (resonanceGain != 0) out.add(pct(resonanceGain) + " Resonance gain");
        if (flaskHeal != 0) out.add(pct(flaskHeal) + " Wick Flask healing");
        if (statusResist != 0) out.add(pct(statusResist) + " status resistance");
        if (glimGain != 0) out.add(pct(glimGain) + " Glim from kills");
        if (sprintSpeed != 0) out.add(pct(sprintSpeed) + " sprint speed");
        if (sprintStaminaFactor != 0) out.add(pct(-sprintStaminaFactor) + " sprint stamina cost");
        return out;
    }

    static String name(dev.vestige.stats.DamageType t) {
        String n = t.getSerializedName();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String fmt(float v) {
        return (v > 0 ? "+" : "") + (v == Math.round(v) ? Integer.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v));
    }

    private static String pct(float v) {
        return (v > 0 ? "+" : "") + Math.round(v * 100) + "%";
    }
}
