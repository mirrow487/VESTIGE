package dev.vestige.stats;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * VESTIGE damage types (GDD §7.10). Each has a mechanical identity beyond its number, applied in the
 * combat damage pipeline:
 * <ul>
 *   <li>SLASH: builds Rend (a bleeding burst).</li>
 *   <li>STRIKE: +50% poise damage; heavier on guards (guard stamina ×1.4).</li>
 *   <li>PIERCE: counter-hit ×1.25 against a target that is winding up or attacking.</li>
 *   <li>CINDER: builds Scorch (burning over time, healing cut by 30%).</li>
 *   <li>RESONANCE: shakes poise (adds 80% of its damage as poise damage); shields absorb only half of it.</li>
 *   <li>HUSH: ignores half of the target's defence; builds Hushed.</li>
 *   <li>VOID: ignores absorption entirely; builds Voidbite (max HP drained until rest).</li>
 *   <li>RADIANCE: ×1.3 against the Pale-touched, Hush and Void; 4% of it heals the wielder.</li>
 * </ul>
 */
public enum DamageType implements StringRepresentable {
    SLASH(0xFFC8C8D2, true), STRIKE(0xFFB8AE9C, true), PIERCE(0xFFD9D2C0, true),
    CINDER(0xFFE0703C, false), RESONANCE(0xFFE8B84A, false), HUSH(0xFF5FA8A8, false),
    VOID(0xFF9A6AD0, false), RADIANCE(0xFFF2E6A8, false);

    public static final Codec<DamageType> CODEC = StringRepresentable.fromEnum(DamageType::values);
    private static final DamageType[] VALUES = values();

    /** UI colour (ARGB). */
    public final int color;
    public final boolean physical;

    DamageType(int color, boolean physical) {
        this.color = color;
        this.physical = physical;
    }

    public static DamageType[] all() {
        return VALUES;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
