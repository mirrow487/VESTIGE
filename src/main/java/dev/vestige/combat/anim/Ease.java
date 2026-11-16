package dev.vestige.combat.anim;

/** Easing curves. Names match playerAnimator's {@code Ease} so player and mob animations agree. */
public enum Ease {
    LINEAR, INSINE, OUTSINE, INOUTSINE, INQUAD, OUTQUAD, INOUTQUAD, INCUBIC, OUTCUBIC, INOUTCUBIC,
    INQUART, OUTQUART, INOUTQUART, INEXPO, OUTEXPO, INBACK, OUTBACK, CONSTANT;

    public float apply(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return switch (this) {
            case LINEAR -> t;
            case CONSTANT -> 0f;
            case INSINE -> 1f - (float) Math.cos(t * Math.PI / 2);
            case OUTSINE -> (float) Math.sin(t * Math.PI / 2);
            case INOUTSINE -> (float) (-(Math.cos(Math.PI * t) - 1) / 2);
            case INQUAD -> t * t;
            case OUTQUAD -> 1 - (1 - t) * (1 - t);
            case INOUTQUAD -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
            case INCUBIC -> t * t * t;
            case OUTCUBIC -> 1 - (float) Math.pow(1 - t, 3);
            case INOUTCUBIC -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            case INQUART -> t * t * t * t;
            case OUTQUART -> 1 - (float) Math.pow(1 - t, 4);
            case INOUTQUART -> t < 0.5f ? 8 * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;
            case INEXPO -> t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
            case OUTEXPO -> t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
            case INBACK -> {
                float c1 = 1.70158f;
                yield (c1 + 1) * t * t * t - c1 * t * t;
            }
            case OUTBACK -> {
                float c1 = 1.70158f;
                float u = t - 1;
                yield 1 + (c1 + 1) * u * u * u + c1 * u * u;
            }
        };
    }
}
