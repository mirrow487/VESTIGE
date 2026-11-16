package dev.vestige.combat;

import net.minecraft.world.entity.LivingEntity;

/**
 * Implemented by entities with VESTIGE combat stats (enemies, bosses). Entities without it use
 * vanilla-derived defence and a small knockback instead of poise.
 */
public interface CombatTarget {
    /** Physical defence (GDD §7.10). */
    float combatDefense();

    /** Applies poise damage; the entity decides whether it flinches or staggers. */
    void receivePoiseDamage(float amount, LivingEntity attacker);

    /** True while staggered: hits deal critical damage. */
    boolean isStaggered();

    /** Pauses the entity's current action for the given hitstop ticks. */
    void applyHitstop(int ticks);

    /** Its attack was parried: it reels, open to a riposte. */
    void receiveParry(LivingEntity parrier);

    /** Reeling from a parry or a poise break (front ripostes allowed). */
    boolean isReeling();

    /** Whether critical attacks (ripostes, backstabs) can start against it now. */
    boolean canBeCritical();

    /** A riposte or backstab has begun: play the victim reaction for the critical's duration. */
    void beginCritical(LivingEntity attacker, int ticks);

    /** Forced stagger from an attack's {@code inflict_stagger} (e.g. a pinning thrust). */
    void forceStagger(int ticks);
}
