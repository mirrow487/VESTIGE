package dev.vestige.combat.anim;

/**
 * Where a keyframe sits on an action's timeline. A key at (ACTIVE, 0.5) is halfway through the
 * active frames whatever their length, so animations always line up with hit timing (ADR-007).
 */
public enum Anchor {
    WINDUP, ACTIVE, RECOVERY;

    public float tick(int windup, int active, int recovery, float fraction) {
        return switch (this) {
            case WINDUP -> fraction * windup;
            case ACTIVE -> windup + fraction * active;
            case RECOVERY -> windup + active + fraction * recovery;
        };
    }
}
