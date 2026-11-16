package dev.vestige.stats;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Character attributes (GDD §8.6). Raised with Glim at Waylights ("Attunement").
 * <ul>
 *   <li>VITALITY: maximum health.</li>
 *   <li>ENDURANCE: stamina, equip capacity (lighter rolls in heavier gear) and poise.</li>
 *   <li>STRENGTH: scaling of heavy and Strike weapons; requirement for great weapons and heavy armor.</li>
 *   <li>DEXTERITY: scaling of quick, Slash and Pierce weapons and bows.</li>
 *   <li>CADENCE: sound and silence. Scales Resonance, Hush and Void damage, weapon art damage and Resonance gain.</li>
 *   <li>LAMPLIGHT: the Lamplighters' flame. Scales Radiance and Cinder damage, Wick Flask healing and status resistance.</li>
 * </ul>
 */
public enum Attribute implements StringRepresentable {
    VITALITY("VIT"), ENDURANCE("END"), STRENGTH("STR"), DEXTERITY("DEX"), CADENCE("CAD"), LAMPLIGHT("LMP");

    public static final Codec<Attribute> CODEC = StringRepresentable.fromEnum(Attribute::values);
    public final String abbreviation;

    Attribute(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
