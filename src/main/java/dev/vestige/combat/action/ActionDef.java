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
                        int tracking, int inflictStagger, Defense defense, Optional<Art> art, Effects effects) {

    /**
     * What an action does besides its melee hitbox, triggered on its first active tick (GDD §7.11).
     *
     * @param projectile fires a projectile along the attacker's aim (Longbow)
     * @param cloud      leaves a lingering damage cloud (Censer incense)
     * @param fx         client visual for the active frames: "wake" (a ground shockwave running along a
     *                   thrust line), "toll" (sound ring; also drawn for every RING hitbox)
     * @param keen       after this action, the next hit within this many ticks is a critical (Quickstep)
     */
    public record Effects(Optional<Projectile> projectile, Optional<Cloud> cloud, Optional<String> fx, int keen) {
        public static final Effects NONE = new Effects(Optional.empty(), Optional.empty(), Optional.empty(), 0);
        public static final Codec<Effects> CODEC = RecordCodecBuilder.create(i -> i.group(
                Projectile.CODEC.optionalFieldOf("projectile").forGetter(Effects::projectile),
                Cloud.CODEC.optionalFieldOf("cloud").forGetter(Effects::cloud),
                Codec.STRING.optionalFieldOf("fx").forGetter(Effects::fx),
                Codec.intRange(0, 200).optionalFieldOf("keen", 0).forGetter(Effects::keen)
        ).apply(i, Effects::new));
    }

    /**
     * @param speed       launch speed in blocks per tick at full draw
     * @param chargeSpeed extra fraction of speed gained from a full charge (0 = none)
     * @param ammo        consumes an arrow from the inventory (not in creative)
     */
    public record Projectile(float speed, float chargeSpeed, boolean ammo) {
        public static final Codec<Projectile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.1f, 10f).fieldOf("speed").forGetter(Projectile::speed),
                Codec.floatRange(0f, 2f).optionalFieldOf("charge_speed", 0f).forGetter(Projectile::chargeSpeed),
                Codec.BOOL.optionalFieldOf("ammo", true).forGetter(Projectile::ammo)
        ).apply(i, Projectile::new));
    }

    /**
     * A lingering cloud centred {@code offset} blocks ahead: every {@code interval} ticks for
     * {@code duration} ticks, everything hostile inside {@code radius} takes {@code motionValue} of the
     * attacker's attack rating (no poise damage, no flinch).
     */
    public record Cloud(float radius, int duration, int interval, float motionValue, float offset) {
        public static final Codec<Cloud> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.5f, 12f).fieldOf("radius").forGetter(Cloud::radius),
                Codec.intRange(1, 1200).fieldOf("duration").forGetter(Cloud::duration),
                Codec.intRange(1, 100).optionalFieldOf("interval", 10).forGetter(Cloud::interval),
                Codec.floatRange(0f, 5f).fieldOf("motion_value").forGetter(Cloud::motionValue),
                Codec.FLOAT.optionalFieldOf("offset", 0f).forGetter(Cloud::offset)
        ).apply(i, Cloud::new));
    }

    /**
     * Defensive properties (GDD §7.7).
     *
     * @param guard        while held at its peak, this action blocks frontal attacks (guard stats come from
     *                     the shield or weapon in use)
     * @param parry        ticks (from action start, inclusive/exclusive) during which parriable attacks are deflected
     * @param invulnerable no damage at all for the whole action (ripostes)
     */
    public record Defense(boolean guard, Optional<Window> parry, boolean invulnerable) {
        public static final Defense NONE = new Defense(false, Optional.empty(), false);
        public static final Codec<Defense> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("guard", false).forGetter(Defense::guard),
                Window.CODEC.optionalFieldOf("parry").forGetter(Defense::parry),
                Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(Defense::invulnerable)
        ).apply(i, Defense::new));
    }

    /** A tick window relative to the action start: start inclusive, end exclusive. */
    public record Window(int start, int end) {
        public static final Codec<Window> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 200).fieldOf("start").forGetter(Window::start),
                Codec.intRange(0, 200).fieldOf("end").forGetter(Window::end)
        ).apply(i, Window::new));

        public boolean contains(int tick) {
            return tick >= start && tick < end;
        }
    }

    /**
     * A weapon art (GDD §7.11): costs Resonance; may chain into a counter when its parry window succeeds.
     *
     * @param cost    Resonance spent on start
     * @param onParry action (same moveset) started automatically after a successful parry
     */
    public record Art(float cost, Optional<String> onParry) {
        public static final Codec<Art> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 100f).fieldOf("cost").forGetter(Art::cost),
                Codec.STRING.optionalFieldOf("on_parry").forGetter(Art::onParry)
        ).apply(i, Art::new));
    }

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
     * @param physical     overrides the weapon's physical type for this action (a sword's thrust pierces)
     * @param types        enemy actions: fraction of {@code damage} per type
     */
    public record Power(float stamina, float motionValue, float poiseDamage, float damage,
                        Optional<dev.vestige.stats.DamageType> physical, dev.vestige.stats.DamageSpread types) {
        public static final Power NONE = new Power(0f, 0f, 0f, 0f, Optional.empty(), dev.vestige.stats.DamageSpread.EMPTY);
        public static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(Power::stamina),
                Codec.FLOAT.optionalFieldOf("motion_value", 1f).forGetter(Power::motionValue),
                Codec.FLOAT.optionalFieldOf("poise_damage", 0f).forGetter(Power::poiseDamage),
                Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(Power::damage),
                dev.vestige.stats.DamageType.CODEC.optionalFieldOf("physical").forGetter(Power::physical),
                dev.vestige.stats.DamageSpread.CODEC.optionalFieldOf("types", dev.vestige.stats.DamageSpread.EMPTY).forGetter(Power::types)
        ).apply(i, Power::new));

        /**
         * An enemy action's attack per type: {@code damage} split by {@code types} fractions (default:
         * all Slash, or the {@code physical} override).
         */
        public dev.vestige.stats.DamageSpread enemySpread() {
            if (types.isEmpty()) {
                return dev.vestige.stats.DamageSpread.single(physical.orElse(dev.vestige.stats.DamageType.SLASH), damage);
            }
            return types.scaled(damage / Math.max(1e-6f, types.total()));
        }
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
                Codec.intRange(1, 72000).fieldOf("max_ticks").forGetter(Charge::maxTicks),
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
            Codec.intRange(0, 200).optionalFieldOf("inflict_stagger", 0).forGetter(ActionDef::inflictStagger),
            Defense.CODEC.optionalFieldOf("defense", Defense.NONE).forGetter(ActionDef::defense),
            Art.CODEC.optionalFieldOf("art").forGetter(ActionDef::art),
            Effects.CODEC.optionalFieldOf("effects", Effects.NONE).forGetter(ActionDef::effects)
    ).apply(i, ActionDef::new));

    /** Parriable attacks are those telegraphed with a white glint (GDD §7.12). */
    public boolean parriable() {
        return telegraph.map(t -> t.glint() == Telegraph.Glint.WHITE).orElse(true);
    }

    /** Grabs cannot be blocked; everything else can. */
    public boolean blockable() {
        return telegraph.map(t -> t.glint() != Telegraph.Glint.GRAB).orElse(true);
    }

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
