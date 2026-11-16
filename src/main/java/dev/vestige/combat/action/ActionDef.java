package dev.vestige.combat.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * One combat action (an attack, roll, flinch...) shared by players, enemies and bosses
 * (ARCHITECTURE ADR-006). All times are ticks. The same numbers drive server hit timing and the
 * generated animation (ADR-007), so they cannot drift apart.
 */
public record ActionDef(Timing timing, Power power, boolean hyperarmor, Optional<HitShape> hit, Motion motion,
                        Optional<Telegraph> telegraph, String pose, Optional<Charge> charge, Optional<Dodge> dodge,
                        int tracking, int inflictStagger) {

    /**
     * @param comboWindow fraction of recovery after which the next attack may start (GDD: last 60%)
     * @param cancelWindow fraction of recovery after which a dodge may cancel it
     */
    public record Timing(int windup, int active, int recovery, float comboWindow, float cancelWindow, int hitstop) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 400).fieldOf("windup").forGetter(Timing::windup),
                Codec.intRange(0, 400).fieldOf("active").forGetter(Timing::active),
                Codec.intRange(0, 400).fieldOf("recovery").forGetter(Timing::recovery),
                Codec.floatRange(0f, 1f).optionalFieldOf("combo_window", 0.4f).forGetter(Timing::comboWindow),
                Codec.floatRange(0f, 1f).optionalFieldOf("cancel_window", 0.4f).forGetter(Timing::cancelWindow),
                Codec.intRange(0, 20).optionalFieldOf("hitstop", 3).forGetter(Timing::hitstop)
        ).apply(i, Timing::new));

        public int total() {
            return windup + active + recovery;
        }
    }

    /**
     * @param stamina      stamina cost (players)
     * @param motionValue  multiplier on the weapon's attack rating
     * @param poiseDamage  poise damage dealt per hit
     * @param damage       flat attack rating for enemy actions (players use their weapon instead)
     */
    public record Power(float stamina, float motionValue, float poiseDamage, float damage) {
        public static final Power NONE = new Power(0f, 0f, 0f, 0f);
        public static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(Power::stamina),
                Codec.FLOAT.optionalFieldOf("motion_value", 1f).forGetter(Power::motionValue),
                Codec.FLOAT.optionalFieldOf("poise_damage", 0f).forGetter(Power::poiseDamage),
                Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(Power::damage)
        ).apply(i, Power::new));
    }

    /** Forward root motion in blocks per tick for each phase. */
    public record Motion(float windup, float active, float recovery) {
        public static final Motion NONE = new Motion(0f, 0f, 0f);
        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("windup", 0f).forGetter(Motion::windup),
                Codec.FLOAT.optionalFieldOf("active", 0f).forGetter(Motion::active),
                Codec.FLOAT.optionalFieldOf("recovery", 0f).forGetter(Motion::recovery)
        ).apply(i, Motion::new));
    }

    /** Hold-to-charge heavy attack: windup pauses at its peak for up to {@code maxTicks}. */
    public record Charge(int maxTicks, float motionBonus, float poiseBonus) {
        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 200).fieldOf("max_ticks").forGetter(Charge::maxTicks),
                Codec.FLOAT.fieldOf("motion_bonus").forGetter(Charge::motionBonus),
                Codec.FLOAT.optionalFieldOf("poise_bonus", 0f).forGetter(Charge::poiseBonus)
        ).apply(i, Charge::new));
    }

    /**
     * Dodge movement and invincibility (GDD §7.6). I-frame ticks are relative to the action start,
     * inclusive start, exclusive end.
     */
    public record Dodge(int iframeStart, int iframeEnd, float distance, int moveTicks) {
        public static final Codec<Dodge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 100).fieldOf("iframe_start").forGetter(Dodge::iframeStart),
                Codec.intRange(0, 100).fieldOf("iframe_end").forGetter(Dodge::iframeEnd),
                Codec.floatRange(0f, 20f).fieldOf("distance").forGetter(Dodge::distance),
                Codec.intRange(1, 100).fieldOf("move_ticks").forGetter(Dodge::moveTicks)
        ).apply(i, Dodge::new));
    }

    public static final Codec<ActionDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Timing.CODEC.fieldOf("timing").forGetter(ActionDef::timing),
            Power.CODEC.optionalFieldOf("power", Power.NONE).forGetter(ActionDef::power),
            Codec.BOOL.optionalFieldOf("hyperarmor", false).forGetter(ActionDef::hyperarmor),
            HitShape.CODEC.optionalFieldOf("hit").forGetter(ActionDef::hit),
            Motion.CODEC.optionalFieldOf("motion", Motion.NONE).forGetter(ActionDef::motion),
            Telegraph.CODEC.optionalFieldOf("telegraph").forGetter(ActionDef::telegraph),
            Codec.STRING.optionalFieldOf("pose", "").forGetter(ActionDef::pose),
            Charge.CODEC.optionalFieldOf("charge").forGetter(ActionDef::charge),
            Dodge.CODEC.optionalFieldOf("dodge").forGetter(ActionDef::dodge),
            Codec.intRange(0, 360).optionalFieldOf("tracking", 30).forGetter(ActionDef::tracking),
            Codec.intRange(0, 200).optionalFieldOf("inflict_stagger", 0).forGetter(ActionDef::inflictStagger)
    ).apply(i, ActionDef::new));

    public int total() {
        return timing.total();
    }

    public ActionPhase phaseAt(int elapsed) {
        if (elapsed < timing.windup()) {
            return ActionPhase.WINDUP;
        }
        if (elapsed < timing.windup() + timing.active()) {
            return ActionPhase.ACTIVE;
        }
        return elapsed < total() ? ActionPhase.RECOVERY : ActionPhase.DONE;
    }

    /** First tick at which the next attack may chain. */
    public int comboOpensAt() {
        return timing.windup() + timing.active() + (int) Math.floor(timing.recovery() * timing.comboWindow());
    }

    /** First tick at which a dodge may cancel this action. */
    public int cancelOpensAt() {
        return timing.windup() + timing.active() + (int) Math.floor(timing.recovery() * timing.cancelWindow());
    }
}
