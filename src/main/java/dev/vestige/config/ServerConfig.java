package dev.vestige.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world server configuration ({@code serverconfig/vestige-server.toml}).
 *
 * <p>Only options read by implemented systems exist here. GDD §27 lists the full planned set.
 */
public final class ServerConfig {
    /** Every translation key used by this spec, consumed by datagen to produce en_us entries. */
    public static final List<Entry> ENTRIES;

    public static final ModConfigSpec.BooleanValue SCALE_DIMENSION_BORDERS;
    public static final ModConfigSpec.DoubleValue NETHER_BORDER_DIVISOR;
    public static final ModConfigSpec.DoubleValue END_BORDER_DIVISOR;
    public static final ModConfigSpec.DoubleValue MIN_SCALED_BORDER_SIZE;

    public static final ModConfigSpec.DoubleValue HEALTH_SCALE;
    public static final ModConfigSpec.DoubleValue STAMINA_BASE;
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue STAMINA_REGEN_DELAY_TICKS;
    public static final ModConfigSpec.IntValue IFRAME_LATENCY_TOLERANCE;

    public static final ModConfigSpec SPEC;

    /** A config entry's translation key, display name and tooltip. */
    public record Entry(String key, String name, String tooltip) {}

    static {
        List<Entry> entries = new ArrayList<>();
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("The Pale: world border behaviour across dimensions (GDD §6.1).").push("border");
        entries.add(new Entry(key("border"), "The Pale (World Border)", "World border behaviour across dimensions."));

        SCALE_DIMENSION_BORDERS = b
                .comment("Scale the Nether and End world borders from the Overworld border.",
                        "Vanilla copies the Overworld border size unscaled to every dimension.")
                .translation(entry(entries, "border.scaleDimensions", "Scale Dimension Borders",
                        "Scale the Nether and End borders from the Overworld border. Vanilla copies the size unscaled."))
                .define("scaleDimensions", true);

        NETHER_BORDER_DIVISOR = b
                .comment("Nether border size = Overworld border size / this value.")
                .translation(entry(entries, "border.netherDivisor", "Nether Border Divisor",
                        "Nether border size = Overworld border size divided by this value."))
                .defineInRange("netherDivisor", 4.0, 1.0, 16.0);

        END_BORDER_DIVISOR = b
                .comment("End border size = Overworld border size / this value.")
                .translation(entry(entries, "border.endDivisor", "End Border Divisor",
                        "End border size = Overworld border size divided by this value."))
                .defineInRange("endDivisor", 1.0, 1.0, 16.0);

        MIN_SCALED_BORDER_SIZE = b
                .comment("Smallest size (diameter, in blocks) a scaled dimension border may shrink to.")
                .translation(entry(entries, "border.minimumScaledSize", "Minimum Scaled Border Size",
                        "Smallest diameter in blocks a scaled dimension border may shrink to."))
                .defineInRange("minimumScaledSize", 32.0, 1.0, 1024.0);

        b.pop();

        b.comment("Health and stamina (GDD §8).").push("vitals");
        entries.add(new Entry(key("vitals"), "Vitals", "Health and stamina."));
        HEALTH_SCALE = b
                .comment("Multiplier applied to the max health of every living entity and to vanilla damage and healing.",
                        "VESTIGE combat numbers assume 5 (player = 100 HP). Applied as entities load.")
                .translation(entry(entries, "vitals.healthScale", "Health Scale",
                        "Multiplies max health of all creatures and vanilla damage/healing. Combat is balanced for 5."))
                .defineInRange("healthScale", 5.0, 1.0, 10.0);
        STAMINA_BASE = b
                .comment("Base maximum stamina.")
                .translation(entry(entries, "vitals.staminaBase", "Base Stamina", "Base maximum stamina."))
                .defineInRange("staminaBase", 100.0, 10.0, 1000.0);
        STAMINA_REGEN_PER_SECOND = b
                .comment("Stamina regenerated per second once regeneration starts.")
                .translation(entry(entries, "vitals.staminaRegen", "Stamina Regeneration", "Stamina regained per second."))
                .defineInRange("staminaRegen", 32.0, 1.0, 500.0);
        STAMINA_REGEN_DELAY_TICKS = b
                .comment("Ticks after spending stamina before it starts to regenerate.")
                .translation(entry(entries, "vitals.staminaRegenDelay", "Stamina Regeneration Delay", "Ticks before stamina starts regenerating after use."))
                .defineInRange("staminaRegenDelay", 12, 0, 200);
        b.pop();

        b.comment("Combat (GDD §7).").push("combat");
        entries.add(new Entry(key("combat"), "Combat", "Combat timing."));
        IFRAME_LATENCY_TOLERANCE = b
                .comment("Ticks a dodge's invincibility window is backdated to compensate for network latency.")
                .translation(entry(entries, "combat.iframeLatencyTolerance", "Dodge Latency Tolerance",
                        "Ticks a dodge's invincibility is backdated to compensate for latency."))
                .defineInRange("iframeLatencyTolerance", 2, 0, 6);
        b.pop();

        SPEC = b.build();
        ENTRIES = Collections.unmodifiableList(entries);
    }

    private ServerConfig() {}

    private static String key(String path) {
        return "vestige.configuration." + path;
    }

    private static String entry(List<Entry> entries, String path, String name, String tooltip) {
        String key = key(path);
        entries.add(new Entry(key, name, tooltip));
        return key;
    }
}
