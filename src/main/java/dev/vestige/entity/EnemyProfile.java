package dev.vestige.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Combat stats and attack table of an enemy, loaded from
 * {@code data/<ns>/vestige/enemies/<entity path>.json}. Readability rules (GDD §7.12, §7.13) are
 * expressed here: ranges, cooldowns and the pause between attacks.
 */
public record EnemyProfile(ResourceLocation moveset, float health, float defense, float poise, int poiseRegenDelay,
                           Engage engage, List<Attack> attacks) {

    /** Preferred spacing and the pause between attacks. */
    public record Engage(float min, float max, float strafeSpeed, int pauseMin, int pauseMax) {
        public static final Codec<Engage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min").forGetter(Engage::min),
                Codec.FLOAT.fieldOf("max").forGetter(Engage::max),
                Codec.FLOAT.optionalFieldOf("strafe_speed", 0.5f).forGetter(Engage::strafeSpeed),
                Codec.intRange(0, 400).optionalFieldOf("pause_min", 10).forGetter(Engage::pauseMin),
                Codec.intRange(0, 400).optionalFieldOf("pause_max", 30).forGetter(Engage::pauseMax)
        ).apply(i, Engage::new));
    }

    /** One entry of the attack table. */
    public record Attack(String action, int weight, float minRange, float maxRange, int cooldown,
                         Optional<String> followUp, float followChance) {
        public static final Codec<Attack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("action").forGetter(Attack::action),
                Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(Attack::weight),
                Codec.FLOAT.optionalFieldOf("min_range", 0f).forGetter(Attack::minRange),
                Codec.FLOAT.fieldOf("max_range").forGetter(Attack::maxRange),
                Codec.intRange(0, 2000).optionalFieldOf("cooldown", 20).forGetter(Attack::cooldown),
                Codec.STRING.optionalFieldOf("follow_up").forGetter(Attack::followUp),
                Codec.floatRange(0f, 1f).optionalFieldOf("follow_chance", 0f).forGetter(Attack::followChance)
        ).apply(i, Attack::new));
    }

    public static final Codec<EnemyProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("moveset").forGetter(EnemyProfile::moveset),
            Codec.floatRange(1f, 100_000f).fieldOf("health").forGetter(EnemyProfile::health),
            Codec.floatRange(0f, 10_000f).optionalFieldOf("defense", 0f).forGetter(EnemyProfile::defense),
            Codec.floatRange(1f, 10_000f).optionalFieldOf("poise", 20f).forGetter(EnemyProfile::poise),
            Codec.intRange(0, 2000).optionalFieldOf("poise_regen_delay", 60).forGetter(EnemyProfile::poiseRegenDelay),
            Engage.CODEC.fieldOf("engage").forGetter(EnemyProfile::engage),
            Attack.CODEC.listOf().fieldOf("attacks").forGetter(EnemyProfile::attacks)
    ).apply(i, EnemyProfile::new));
}
