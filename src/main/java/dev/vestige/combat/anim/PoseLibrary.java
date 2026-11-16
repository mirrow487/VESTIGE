package dev.vestige.combat.anim;

import static dev.vestige.combat.anim.Anchor.ACTIVE;
import static dev.vestige.combat.anim.Anchor.RECOVERY;
import static dev.vestige.combat.anim.Anchor.WINDUP;
import static dev.vestige.combat.anim.Channel.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Named pose sequences referenced by actions' {@code pose} field. Keys are anchored to phases, so
 * one sequence fits any timing: the windup peak sits at the start of the active frames and the
 * strike completes as they end.
 *
 * <p>Design language (GDD §7.4): slow, readable anticipation (OUTCUBIC into the windup peak),
 * a fast strike (OUTQUAD/OUTEXPO), and a weighty follow-through held briefly before recovering.
 */
public final class PoseLibrary {
    private static final Map<String, List<PoseKey>> POSES = new HashMap<>();

    private PoseLibrary() {}

    public static Optional<List<PoseKey>> get(String name) {
        return Optional.ofNullable(POSES.get(name));
    }

    public static java.util.Set<String> names() {
        return POSES.keySet();
    }

    private static PoseKey.Builder k(Anchor a, float f, Ease e) {
        return PoseKey.key(a, f, e);
    }

    private static void def(String name, PoseKey.Builder... keys) {
        POSES.put(name, java.util.Arrays.stream(keys).map(PoseKey.Builder::build).toList());
    }

    static {
        // ------------------------------------------------------------ one-handed blade
        def("blade_slash_rl",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -115).set(RIGHT_ARM_YAW, 70).set(RIGHT_ARM_ROLL, 20)
                        .set(LEFT_ARM_PITCH, -25).set(LEFT_ARM_ROLL, -15).set(TORSO_YAW, 30).set(TORSO_PITCH, 4).set(HEAD_YAW, -15)
                        .set(ITEM_ROLL, -30),
                k(ACTIVE, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -95).set(RIGHT_ARM_YAW, 10).set(RIGHT_ARM_ROLL, 10)
                        .set(LEFT_ARM_PITCH, -5).set(TORSO_YAW, 5).set(ITEM_ROLL, -20),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -75).set(RIGHT_ARM_YAW, -70).set(RIGHT_ARM_ROLL, -5)
                        .set(LEFT_ARM_PITCH, 15).set(LEFT_ARM_ROLL, -10).set(TORSO_YAW, -30).set(TORSO_PITCH, 10).set(HEAD_YAW, 20)
                        .set(ITEM_ROLL, -10),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -65).set(RIGHT_ARM_YAW, -60).set(TORSO_YAW, -25)
                        .set(TORSO_PITCH, 8).set(HEAD_YAW, 15).set(LEFT_ARM_PITCH, 10));
        def("blade_slash_lr",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, -70).set(RIGHT_ARM_ROLL, -25)
                        .set(LEFT_ARM_PITCH, 10).set(TORSO_YAW, -30).set(HEAD_YAW, 15).set(ITEM_ROLL, 30),
                k(ACTIVE, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -95).set(RIGHT_ARM_YAW, 0).set(TORSO_YAW, 0).set(ITEM_ROLL, 20),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -80).set(RIGHT_ARM_YAW, 75).set(RIGHT_ARM_ROLL, 15)
                        .set(LEFT_ARM_PITCH, -20).set(TORSO_YAW, 30).set(TORSO_PITCH, 8).set(HEAD_YAW, -20).set(ITEM_ROLL, 10),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -70).set(RIGHT_ARM_YAW, 65).set(TORSO_YAW, 25)
                        .set(TORSO_PITCH, 6).set(HEAD_YAW, -15));
        def("blade_overhead",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -165).set(RIGHT_ARM_YAW, 15).set(RIGHT_ARM_ROLL, 10)
                        .set(LEFT_ARM_PITCH, -40).set(TORSO_PITCH, -8).set(HEAD_PITCH, -5),
                k(ACTIVE, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -110).set(RIGHT_ARM_YAW, 5).set(TORSO_PITCH, 5),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -35).set(RIGHT_ARM_YAW, -10).set(LEFT_ARM_PITCH, 10)
                        .set(TORSO_PITCH, 18).set(HEAD_PITCH, 10),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -30).set(TORSO_PITCH, 14).set(HEAD_PITCH, 8));
        def("blade_thrust",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -25).set(RIGHT_ARM_YAW, 15).set(RIGHT_ARM_BEND, 45)
                        .set(TORSO_YAW, 25).set(LEFT_ARM_PITCH, -45).set(LEFT_ARM_YAW, -20).set(ITEM_PITCH, 70),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -90).set(RIGHT_ARM_YAW, -5).set(RIGHT_ARM_BEND, 0)
                        .set(TORSO_YAW, -20).set(TORSO_PITCH, 8).set(LEFT_ARM_PITCH, 15).set(ITEM_PITCH, 85)
                        .set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 15),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -85).set(TORSO_YAW, -15).set(TORSO_PITCH, 6)
                        .set(ITEM_PITCH, 80).set(RIGHT_LEG_PITCH, -15).set(LEFT_LEG_PITCH, 10));
        def("blade_heavy",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -170).set(RIGHT_ARM_YAW, 40).set(RIGHT_ARM_ROLL, 25)
                        .set(LEFT_ARM_PITCH, -150).set(LEFT_ARM_YAW, -20).set(TORSO_YAW, 35).set(TORSO_PITCH, -10).set(HEAD_YAW, -15)
                        .set(RIGHT_LEG_PITCH, 10).set(LEFT_LEG_PITCH, -15),
                k(ACTIVE, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -110).set(RIGHT_ARM_YAW, 0).set(LEFT_ARM_PITCH, -100)
                        .set(TORSO_YAW, 0).set(TORSO_PITCH, 8),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -40).set(RIGHT_ARM_YAW, -45).set(LEFT_ARM_PITCH, -45)
                        .set(LEFT_ARM_YAW, -10).set(TORSO_YAW, -35).set(TORSO_PITCH, 20).set(HEAD_PITCH, 10).set(HEAD_YAW, 20)
                        .set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 20),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -35).set(RIGHT_ARM_YAW, -40).set(LEFT_ARM_PITCH, -30)
                        .set(TORSO_YAW, -30).set(TORSO_PITCH, 16).set(HEAD_YAW, 15).set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 15));
        def("blade_running",
                k(WINDUP, 0.5f, Ease.OUTQUAD).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 25).set(TORSO_PITCH, 12),
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -125).set(RIGHT_ARM_YAW, 60).set(TORSO_PITCH, 15)
                        .set(TORSO_YAW, 20).set(RIGHT_LEG_PITCH, -35).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -70).set(RIGHT_ARM_YAW, -60).set(TORSO_YAW, -25)
                        .set(TORSO_PITCH, 20).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -60).set(RIGHT_ARM_YAW, -50).set(TORSO_PITCH, 14)
                        .set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 20));
        def("blade_rolling",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -20).set(RIGHT_ARM_YAW, -40).set(TORSO_PITCH, 22)
                        .set(TORSO_YAW, -20).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 20),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -150).set(RIGHT_ARM_YAW, 40).set(TORSO_PITCH, -5)
                        .set(TORSO_YAW, 20),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -140).set(RIGHT_ARM_YAW, 35).set(TORSO_YAW, 15));

        // ------------------------------------------------------------ pike (two hands)
        def("pike_thrust",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -40).set(RIGHT_ARM_YAW, 20).set(RIGHT_ARM_BEND, 40)
                        .set(LEFT_ARM_PITCH, -70).set(LEFT_ARM_YAW, 35).set(TORSO_YAW, 30).set(ITEM_PITCH, 70),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -85).set(RIGHT_ARM_YAW, 0).set(LEFT_ARM_PITCH, -95)
                        .set(LEFT_ARM_YAW, 15).set(TORSO_YAW, -10).set(TORSO_PITCH, 10).set(ITEM_PITCH, 80)
                        .set(RIGHT_LEG_PITCH, -15).set(LEFT_LEG_PITCH, 12),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -80).set(LEFT_ARM_PITCH, -90).set(LEFT_ARM_YAW, 15)
                        .set(TORSO_PITCH, 8).set(ITEM_PITCH, 80));
        def("pike_thrust_low",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -20).set(RIGHT_ARM_YAW, 20).set(RIGHT_ARM_BEND, 45)
                        .set(LEFT_ARM_PITCH, -55).set(LEFT_ARM_YAW, 35).set(TORSO_YAW, 30).set(TORSO_PITCH, 10).set(ITEM_PITCH, 60),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -60).set(LEFT_ARM_PITCH, -75).set(LEFT_ARM_YAW, 15)
                        .set(TORSO_YAW, -10).set(TORSO_PITCH, 22).set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 15),
                k(RECOVERY, 0.35f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -58).set(LEFT_ARM_PITCH, -70).set(TORSO_PITCH, 18)
                        .set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 12));
        def("pike_heavy",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -15).set(RIGHT_ARM_YAW, 35).set(RIGHT_ARM_BEND, 55)
                        .set(LEFT_ARM_PITCH, -60).set(LEFT_ARM_YAW, 45).set(TORSO_YAW, 40).set(TORSO_PITCH, -5).set(ITEM_PITCH, 65)
                        .set(LEFT_LEG_PITCH, 20).set(RIGHT_LEG_PITCH, -5),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -90).set(LEFT_ARM_PITCH, -95).set(LEFT_ARM_YAW, 10)
                        .set(TORSO_YAW, -15).set(TORSO_PITCH, 20).set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -85).set(LEFT_ARM_PITCH, -90).set(TORSO_PITCH, 16)
                        .set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 25));
        def("pike_running",
                k(WINDUP, 0.5f, Ease.OUTQUAD).set(TORSO_PITCH, 15).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 25)
                        .set(RIGHT_ARM_PITCH, -30).set(LEFT_ARM_PITCH, -60).set(ITEM_PITCH, 70),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -90).set(LEFT_ARM_PITCH, -95).set(TORSO_PITCH, 22)
                        .set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -85).set(LEFT_ARM_PITCH, -90).set(TORSO_PITCH, 16)
                        .set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 20));

        // ------------------------------------------------------------ maul (two hands, heavy)
        def("maul_overhead",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -175).set(RIGHT_ARM_YAW, 10).set(LEFT_ARM_PITCH, -170)
                        .set(LEFT_ARM_YAW, -10).set(TORSO_PITCH, -15).set(HEAD_PITCH, -10).set(RIGHT_LEG_PITCH, 8).set(LEFT_LEG_PITCH, -8),
                k(RECOVERY, 0f, Ease.INQUAD).set(RIGHT_ARM_PITCH, -50).set(LEFT_ARM_PITCH, -50).set(TORSO_PITCH, 30)
                        .set(HEAD_PITCH, 15).set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 20),
                k(RECOVERY, 0.45f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -45).set(LEFT_ARM_PITCH, -45).set(TORSO_PITCH, 26)
                        .set(HEAD_PITCH, 12).set(RIGHT_LEG_PITCH, -18).set(LEFT_LEG_PITCH, 18));
        def("maul_swing",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, 100).set(LEFT_ARM_PITCH, -90)
                        .set(LEFT_ARM_YAW, 70).set(TORSO_YAW, 50).set(HEAD_YAW, -25),
                k(ACTIVE, 0.5f, Ease.INQUAD).set(RIGHT_ARM_PITCH, -90).set(RIGHT_ARM_YAW, 10).set(LEFT_ARM_PITCH, -85)
                        .set(LEFT_ARM_YAW, 15).set(TORSO_YAW, 5),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -80).set(RIGHT_ARM_YAW, -70).set(LEFT_ARM_PITCH, -80)
                        .set(LEFT_ARM_YAW, -50).set(TORSO_YAW, -45).set(HEAD_YAW, 20),
                k(RECOVERY, 0.45f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -70).set(RIGHT_ARM_YAW, -60).set(LEFT_ARM_PITCH, -70)
                        .set(LEFT_ARM_YAW, -45).set(TORSO_YAW, -40).set(HEAD_YAW, 18));
        def("maul_heavy",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -178).set(LEFT_ARM_PITCH, -178).set(TORSO_PITCH, -20)
                        .set(HEAD_PITCH, -15).set(RIGHT_LEG_PITCH, 10).set(LEFT_LEG_PITCH, -10),
                k(RECOVERY, 0f, Ease.INCUBIC).set(RIGHT_ARM_PITCH, -30).set(LEFT_ARM_PITCH, -30).set(TORSO_PITCH, 40)
                        .set(HEAD_PITCH, 20).set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 25),
                k(RECOVERY, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -28).set(LEFT_ARM_PITCH, -28).set(TORSO_PITCH, 36)
                        .set(HEAD_PITCH, 18).set(RIGHT_LEG_PITCH, -22).set(LEFT_LEG_PITCH, 22));

        // ------------------------------------------------------------ dodges and reactions (players and enemies)
        def("roll",
                k(ACTIVE, 0.15f, Ease.OUTQUAD).set(TORSO_PITCH, 35).set(RIGHT_ARM_PITCH, -50).set(LEFT_ARM_PITCH, -50)
                        .set(RIGHT_LEG_PITCH, -70).set(LEFT_LEG_PITCH, -70).set(BODY_PITCH, 40).set(HEAD_PITCH, 25),
                k(ACTIVE, 0.55f, Ease.LINEAR).set(TORSO_PITCH, 40).set(RIGHT_ARM_PITCH, -60).set(LEFT_ARM_PITCH, -60)
                        .set(RIGHT_LEG_PITCH, -80).set(LEFT_LEG_PITCH, -80).set(BODY_PITCH, 220).set(HEAD_PITCH, 30),
                k(ACTIVE, 1f, Ease.OUTQUAD).set(TORSO_PITCH, 15).set(RIGHT_ARM_PITCH, -25).set(LEFT_ARM_PITCH, -25)
                        .set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, -10).set(BODY_PITCH, 360).set(HEAD_PITCH, 10),
                k(RECOVERY, 0.5f, Ease.INOUTSINE).set(TORSO_PITCH, 8).set(BODY_PITCH, 360).set(RIGHT_LEG_PITCH, -10));
        def("backstep",
                k(ACTIVE, 0.3f, Ease.OUTQUAD).set(TORSO_PITCH, -12).set(HEAD_PITCH, -5).set(RIGHT_LEG_PITCH, 25)
                        .set(LEFT_LEG_PITCH, -15).set(RIGHT_ARM_PITCH, -20).set(LEFT_ARM_PITCH, -30),
                k(ACTIVE, 1f, Ease.INOUTSINE).set(TORSO_PITCH, 8).set(RIGHT_LEG_PITCH, -10).set(LEFT_LEG_PITCH, 10)
                        .set(RIGHT_ARM_PITCH, -15).set(LEFT_ARM_PITCH, -20));
        def("flinch",
                k(RECOVERY, 0.2f, Ease.OUTEXPO).set(TORSO_PITCH, -15).set(HEAD_PITCH, -20).set(RIGHT_ARM_ROLL, 30)
                        .set(LEFT_ARM_ROLL, -30).set(RIGHT_ARM_PITCH, -20).set(LEFT_ARM_PITCH, -20),
                k(RECOVERY, 0.6f, Ease.INOUTSINE).set(TORSO_PITCH, -5).set(HEAD_PITCH, -8).set(RIGHT_ARM_ROLL, 10).set(LEFT_ARM_ROLL, -10));
        def("stagger",
                k(RECOVERY, 0.12f, Ease.OUTEXPO).set(TORSO_PITCH, 35).set(HEAD_PITCH, 30).set(RIGHT_ARM_PITCH, -25)
                        .set(LEFT_ARM_PITCH, -20).set(RIGHT_ARM_ROLL, 10).set(LEFT_ARM_ROLL, -10).set(RIGHT_LEG_PITCH, -25)
                        .set(LEFT_LEG_PITCH, 15),
                k(RECOVERY, 0.8f, Ease.LINEAR).set(TORSO_PITCH, 30).set(HEAD_PITCH, 28).set(RIGHT_ARM_PITCH, -22)
                        .set(LEFT_ARM_PITCH, -18).set(RIGHT_LEG_PITCH, -22).set(LEFT_LEG_PITCH, 12));

        // ------------------------------------------------------------ guard, parry, arts, criticals (M4)
        def("guard_weapon",
                k(ACTIVE, 0f, Ease.OUTBACK).set(RIGHT_ARM_PITCH, -75).set(RIGHT_ARM_YAW, -35).set(RIGHT_ARM_ROLL, 10)
                        .set(ITEM_ROLL, -65).set(LEFT_ARM_PITCH, -45).set(LEFT_ARM_YAW, 30).set(TORSO_PITCH, 10).set(HEAD_PITCH, -6)
                        .set(RIGHT_LEG_PITCH, -16).set(LEFT_LEG_PITCH, 14),
                k(ACTIVE, 1f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -75).set(RIGHT_ARM_YAW, -35).set(RIGHT_ARM_ROLL, 10)
                        .set(ITEM_ROLL, -65).set(LEFT_ARM_PITCH, -45).set(LEFT_ARM_YAW, 30).set(TORSO_PITCH, 10).set(HEAD_PITCH, -6)
                        .set(RIGHT_LEG_PITCH, -16).set(LEFT_LEG_PITCH, 14));
        def("guard_shield",
                k(ACTIVE, 0f, Ease.OUTBACK).set(LEFT_ARM_PITCH, -62).set(LEFT_ARM_YAW, 32).set(RIGHT_ARM_PITCH, -28)
                        .set(RIGHT_ARM_YAW, 10).set(TORSO_PITCH, 12).set(TORSO_YAW, -8).set(HEAD_PITCH, -8)
                        .set(RIGHT_LEG_PITCH, 14).set(LEFT_LEG_PITCH, -18),
                k(ACTIVE, 1f, Ease.LINEAR).set(LEFT_ARM_PITCH, -62).set(LEFT_ARM_YAW, 32).set(RIGHT_ARM_PITCH, -28)
                        .set(RIGHT_ARM_YAW, 10).set(TORSO_PITCH, 12).set(TORSO_YAW, -8).set(HEAD_PITCH, -8)
                        .set(RIGHT_LEG_PITCH, 14).set(LEFT_LEG_PITCH, -18));
        def("parry_buckler",
                // Anticipation: a quick tuck, forearm bent in, shoulders coiling to the right.
                k(WINDUP, 1f, Ease.OUTQUAD).set(LEFT_ARM_PITCH, -40).set(LEFT_ARM_YAW, 20).set(LEFT_ARM_BEND, 40)
                        .set(TORSO_YAW, 12).set(TORSO_PITCH, 8).set(RIGHT_ARM_PITCH, -18).set(RIGHT_LEG_PITCH, 10).set(LEFT_LEG_PITCH, -14),
                // Parry window: the buckler punches out across the chest, face forward, in view.
                k(ACTIVE, 0.35f, Ease.OUTEXPO).set(LEFT_ARM_PITCH, -68).set(LEFT_ARM_YAW, 36).set(LEFT_ARM_BEND, 0)
                        .set(TORSO_YAW, -14).set(TORSO_PITCH, 10).set(HEAD_PITCH, -4).set(RIGHT_ARM_PITCH, -30).set(RIGHT_ARM_YAW, -10)
                        .set(RIGHT_LEG_PITCH, -14).set(LEFT_LEG_PITCH, 16),
                k(ACTIVE, 1f, Ease.LINEAR).set(LEFT_ARM_PITCH, -70).set(LEFT_ARM_YAW, 32).set(TORSO_YAW, -16).set(TORSO_PITCH, 10)
                        .set(RIGHT_ARM_PITCH, -30).set(RIGHT_ARM_YAW, -10).set(RIGHT_LEG_PITCH, -14).set(LEFT_LEG_PITCH, 16),
                // Swat outward with overshoot, then settle back to a ready stance.
                k(RECOVERY, 0.2f, Ease.OUTBACK).set(LEFT_ARM_PITCH, -82).set(LEFT_ARM_YAW, -16).set(LEFT_ARM_ROLL, -18)
                        .set(TORSO_YAW, -24).set(TORSO_PITCH, 6).set(RIGHT_ARM_PITCH, -34).set(RIGHT_LEG_PITCH, -10).set(LEFT_LEG_PITCH, 12),
                k(RECOVERY, 0.6f, Ease.INOUTSINE).set(LEFT_ARM_PITCH, -55).set(LEFT_ARM_YAW, 18).set(LEFT_ARM_BEND, 20)
                        .set(TORSO_YAW, -6).set(TORSO_PITCH, 6).set(RIGHT_ARM_PITCH, -24));
        def("guard_break",
                k(RECOVERY, 0.12f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -130).set(RIGHT_ARM_ROLL, 40).set(LEFT_ARM_PITCH, -125)
                        .set(LEFT_ARM_ROLL, -40).set(TORSO_PITCH, -22).set(HEAD_PITCH, -28).set(RIGHT_LEG_PITCH, 20).set(LEFT_LEG_PITCH, -10),
                k(RECOVERY, 0.45f, Ease.INOUTSINE).set(RIGHT_ARM_PITCH, -40).set(RIGHT_ARM_ROLL, 25).set(LEFT_ARM_PITCH, -35)
                        .set(LEFT_ARM_ROLL, -25).set(TORSO_PITCH, 18).set(HEAD_PITCH, 20).set(RIGHT_LEG_PITCH, -15).set(LEFT_LEG_PITCH, 10),
                k(RECOVERY, 0.75f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -30).set(LEFT_ARM_PITCH, -28).set(TORSO_PITCH, 15).set(HEAD_PITCH, 15));
        def("blade_riposte_stance",
                k(ACTIVE, 0f, Ease.OUTBACK).set(RIGHT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, -12).set(RIGHT_ARM_BEND, 25)
                        .set(LEFT_ARM_PITCH, -85).set(LEFT_ARM_YAW, 28).set(TORSO_PITCH, 12).set(TORSO_YAW, 15).set(HEAD_PITCH, -4)
                        .set(RIGHT_LEG_PITCH, -26).set(LEFT_LEG_PITCH, 24),
                k(RECOVERY, 0f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, -12).set(RIGHT_ARM_BEND, 25)
                        .set(LEFT_ARM_PITCH, -85).set(LEFT_ARM_YAW, 28).set(TORSO_PITCH, 12).set(TORSO_YAW, 15).set(HEAD_PITCH, -4)
                        .set(RIGHT_LEG_PITCH, -26).set(LEFT_LEG_PITCH, 24));
        def("blade_counter",
                k(ACTIVE, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -105).set(RIGHT_ARM_YAW, 85).set(RIGHT_ARM_ROLL, 20)
                        .set(TORSO_YAW, 40).set(TORSO_PITCH, 8).set(LEFT_ARM_PITCH, -35).set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 18),
                k(ACTIVE, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -95).set(RIGHT_ARM_YAW, 5).set(TORSO_YAW, 0).set(TORSO_PITCH, 10)
                        .set(RIGHT_LEG_PITCH, -32).set(LEFT_LEG_PITCH, 26),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -78).set(RIGHT_ARM_YAW, -88).set(TORSO_YAW, -48)
                        .set(TORSO_PITCH, 14).set(LEFT_ARM_PITCH, 18).set(HEAD_YAW, 20).set(RIGHT_LEG_PITCH, -42).set(LEFT_LEG_PITCH, 34),
                k(RECOVERY, 0.45f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -70).set(RIGHT_ARM_YAW, -78).set(TORSO_YAW, -40)
                        .set(TORSO_PITCH, 12).set(HEAD_YAW, 15).set(RIGHT_LEG_PITCH, -34).set(LEFT_LEG_PITCH, 28));
        def("blade_riposte",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -38).set(RIGHT_ARM_YAW, 22).set(RIGHT_ARM_BEND, 60)
                        .set(ITEM_PITCH, 80).set(LEFT_ARM_PITCH, -55).set(LEFT_ARM_YAW, 25).set(TORSO_YAW, 35).set(TORSO_PITCH, 6)
                        .set(RIGHT_LEG_PITCH, -12).set(LEFT_LEG_PITCH, 10),
                k(ACTIVE, 1f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -88).set(RIGHT_ARM_YAW, 0).set(RIGHT_ARM_BEND, 0)
                        .set(ITEM_PITCH, 85).set(LEFT_ARM_PITCH, -70).set(LEFT_ARM_YAW, 20).set(TORSO_YAW, -15).set(TORSO_PITCH, 14)
                        .set(RIGHT_LEG_PITCH, -38).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0.3f, Ease.INOUTSINE).set(RIGHT_ARM_PITCH, -85).set(RIGHT_ARM_ROLL, 22).set(ITEM_PITCH, 85)
                        .set(ITEM_ROLL, 90).set(LEFT_ARM_PITCH, -70).set(TORSO_YAW, -20).set(TORSO_PITCH, 16)
                        .set(RIGHT_LEG_PITCH, -38).set(LEFT_LEG_PITCH, 30),
                k(RECOVERY, 0.65f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -48).set(RIGHT_ARM_YAW, 28).set(RIGHT_ARM_BEND, 40)
                        .set(ITEM_PITCH, 60).set(TORSO_YAW, 20).set(TORSO_PITCH, 6).set(RIGHT_LEG_PITCH, -15).set(LEFT_LEG_PITCH, 10));
        def("pike_charge",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -22).set(RIGHT_ARM_YAW, 32).set(RIGHT_ARM_BEND, 55)
                        .set(LEFT_ARM_PITCH, -62).set(LEFT_ARM_YAW, 42).set(TORSO_YAW, 38).set(TORSO_PITCH, 18).set(ITEM_PITCH, 70)
                        .set(RIGHT_LEG_PITCH, -25).set(LEFT_LEG_PITCH, 30),
                k(ACTIVE, 0.4f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -88).set(LEFT_ARM_PITCH, -96).set(LEFT_ARM_YAW, 12)
                        .set(TORSO_YAW, -12).set(TORSO_PITCH, 26).set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -55).set(LEFT_LEG_PITCH, 42),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -84).set(LEFT_ARM_PITCH, -90).set(TORSO_PITCH, 20)
                        .set(ITEM_PITCH, 85).set(RIGHT_LEG_PITCH, -35).set(LEFT_LEG_PITCH, 28));
        def("pike_riposte",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -30).set(RIGHT_ARM_YAW, 28).set(RIGHT_ARM_BEND, 55)
                        .set(LEFT_ARM_PITCH, -65).set(LEFT_ARM_YAW, 40).set(TORSO_YAW, 35).set(ITEM_PITCH, 75),
                k(ACTIVE, 1f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -90).set(LEFT_ARM_PITCH, -98).set(LEFT_ARM_YAW, 12)
                        .set(TORSO_YAW, -12).set(TORSO_PITCH, 18).set(ITEM_PITCH, 88).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 32),
                k(RECOVERY, 0.35f, Ease.INOUTSINE).set(RIGHT_ARM_PITCH, -82).set(LEFT_ARM_PITCH, -90).set(ITEM_PITCH, 88)
                        .set(ITEM_ROLL, 80).set(TORSO_PITCH, 18).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 32),
                k(RECOVERY, 0.7f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -40).set(LEFT_ARM_PITCH, -60).set(ITEM_PITCH, 60)
                        .set(TORSO_PITCH, 6).set(RIGHT_LEG_PITCH, -15).set(LEFT_LEG_PITCH, 10));
        def("maul_tremor",
                k(WINDUP, 0.35f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -120).set(LEFT_ARM_PITCH, -115).set(TORSO_PITCH, -8)
                        .set(LEFT_LEG_PITCH, -22).set(RIGHT_LEG_PITCH, 8),
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -178).set(LEFT_ARM_PITCH, -176).set(TORSO_PITCH, -26)
                        .set(HEAD_PITCH, -18).set(LEFT_LEG_PITCH, -28).set(RIGHT_LEG_PITCH, 14),
                k(ACTIVE, 1f, Ease.INCUBIC).set(RIGHT_ARM_PITCH, -22).set(LEFT_ARM_PITCH, -22).set(TORSO_PITCH, 52).set(ITEM_PITCH, 55)
                        .set(HEAD_PITCH, 22).set(RIGHT_LEG_PITCH, -42).set(LEFT_LEG_PITCH, 32).set(RIGHT_LEG_BEND, 40).set(LEFT_LEG_BEND, 30),
                k(RECOVERY, 0.45f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -20).set(LEFT_ARM_PITCH, -20).set(TORSO_PITCH, 48).set(ITEM_PITCH, 50)
                        .set(HEAD_PITCH, 20).set(RIGHT_LEG_PITCH, -40).set(LEFT_LEG_PITCH, 30).set(RIGHT_LEG_BEND, 38).set(LEFT_LEG_BEND, 28));
        def("maul_riposte",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -176).set(LEFT_ARM_PITCH, -172).set(TORSO_PITCH, -18)
                        .set(HEAD_PITCH, -10),
                k(ACTIVE, 1f, Ease.INCUBIC).set(RIGHT_ARM_PITCH, -25).set(LEFT_ARM_PITCH, -25).set(TORSO_PITCH, 48)
                        .set(HEAD_PITCH, 22).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 24),
                k(RECOVERY, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -24).set(LEFT_ARM_PITCH, -24).set(TORSO_PITCH, 44)
                        .set(HEAD_PITCH, 20).set(RIGHT_LEG_PITCH, -28).set(LEFT_LEG_PITCH, 22));
        // Enemy reactions to parries and criticals.
        def("parried",
                k(RECOVERY, 0.08f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -150).set(RIGHT_ARM_ROLL, 30).set(LEFT_ARM_PITCH, -115)
                        .set(LEFT_ARM_ROLL, -30).set(TORSO_PITCH, -24).set(HEAD_PITCH, -26).set(RIGHT_LEG_PITCH, 18).set(LEFT_LEG_PITCH, -12),
                k(RECOVERY, 0.3f, Ease.OUTBACK).set(RIGHT_ARM_PITCH, -95).set(RIGHT_ARM_ROLL, 35).set(LEFT_ARM_PITCH, -70)
                        .set(LEFT_ARM_ROLL, -30).set(TORSO_PITCH, -10).set(HEAD_PITCH, -10).set(RIGHT_LEG_PITCH, 12).set(LEFT_LEG_PITCH, -8),
                k(RECOVERY, 0.55f, Ease.INOUTSINE).set(RIGHT_ARM_PITCH, -15).set(LEFT_ARM_PITCH, -12).set(TORSO_PITCH, 30)
                        .set(HEAD_PITCH, 28).set(RIGHT_LEG_PITCH, -22).set(LEFT_LEG_PITCH, 12),
                k(RECOVERY, 0.85f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -12).set(LEFT_ARM_PITCH, -10).set(TORSO_PITCH, 26)
                        .set(HEAD_PITCH, 24).set(RIGHT_LEG_PITCH, -18).set(LEFT_LEG_PITCH, 10));
        // Impaled: a jerk back, a held beat, then the knees give and the body folds onto them.
        // BODY_Y is a root height offset in model pixels (GeckoLib enemies).
        def("riposted",
                k(RECOVERY, 0.12f, Ease.OUTEXPO).set(TORSO_PITCH, -18).set(HEAD_PITCH, -28).set(RIGHT_ARM_PITCH, -45)
                        .set(RIGHT_ARM_ROLL, 40).set(LEFT_ARM_PITCH, -45).set(LEFT_ARM_ROLL, -40).set(RIGHT_LEG_PITCH, 10).set(LEFT_LEG_PITCH, -6),
                k(RECOVERY, 0.32f, Ease.LINEAR).set(TORSO_PITCH, -12).set(HEAD_PITCH, -24).set(RIGHT_ARM_PITCH, -55)
                        .set(RIGHT_ARM_ROLL, 45).set(LEFT_ARM_PITCH, -55).set(LEFT_ARM_ROLL, -45).set(RIGHT_LEG_PITCH, 8).set(LEFT_LEG_PITCH, -4),
                k(RECOVERY, 0.55f, Ease.INQUAD).set(TORSO_PITCH, 32).set(HEAD_PITCH, 38).set(RIGHT_ARM_PITCH, -12).set(LEFT_ARM_PITCH, -10)
                        .set(RIGHT_ARM_ROLL, 8).set(LEFT_ARM_ROLL, -8).set(RIGHT_LEG_PITCH, -78).set(LEFT_LEG_PITCH, -62)
                        .set(RIGHT_LEG_BEND, 95).set(LEFT_LEG_BEND, 82).set(BODY_Y, -6.5f),
                k(RECOVERY, 0.85f, Ease.LINEAR).set(TORSO_PITCH, 38).set(HEAD_PITCH, 42).set(RIGHT_ARM_PITCH, -8).set(LEFT_ARM_PITCH, -6)
                        .set(RIGHT_LEG_PITCH, -80).set(LEFT_LEG_PITCH, -64).set(RIGHT_LEG_BEND, 96).set(LEFT_LEG_BEND, 84).set(BODY_Y, -6.8f));

        // ------------------------------------------------------------ Hollowed (claws, no weapon)
        def("hollowed_swipe",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -150).set(RIGHT_ARM_YAW, 50).set(RIGHT_ARM_ROLL, 30)
                        .set(TORSO_YAW, 30).set(TORSO_PITCH, -5).set(LEFT_ARM_PITCH, -30).set(HEAD_PITCH, -5),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -50).set(RIGHT_ARM_YAW, -50).set(TORSO_YAW, -30)
                        .set(TORSO_PITCH, 15).set(LEFT_ARM_PITCH, 10).set(HEAD_PITCH, 10),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -40).set(RIGHT_ARM_YAW, -40).set(TORSO_YAW, -25)
                        .set(TORSO_PITCH, 18).set(HEAD_PITCH, 12));
        def("hollowed_backhand",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, -60).set(TORSO_YAW, -30)
                        .set(LEFT_ARM_PITCH, -10),
                k(RECOVERY, 0f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -90).set(RIGHT_ARM_YAW, 70).set(RIGHT_ARM_ROLL, 20)
                        .set(TORSO_YAW, 30).set(TORSO_PITCH, 10),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -70).set(RIGHT_ARM_YAW, 60).set(TORSO_YAW, 25).set(TORSO_PITCH, 12));
        def("hollowed_lunge",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, 35).set(LEFT_ARM_PITCH, 35).set(RIGHT_ARM_ROLL, 25)
                        .set(LEFT_ARM_ROLL, -25).set(TORSO_PITCH, 30).set(HEAD_PITCH, -25).set(RIGHT_LEG_PITCH, 25).set(LEFT_LEG_PITCH, -20),
                k(ACTIVE, 1f, Ease.OUTEXPO).set(RIGHT_ARM_PITCH, -100).set(LEFT_ARM_PITCH, -100).set(RIGHT_ARM_YAW, -15)
                        .set(LEFT_ARM_YAW, 15).set(TORSO_PITCH, 30).set(HEAD_PITCH, -10).set(RIGHT_LEG_PITCH, -45).set(LEFT_LEG_PITCH, 35),
                k(RECOVERY, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -70).set(LEFT_ARM_PITCH, -70).set(TORSO_PITCH, 35)
                        .set(HEAD_PITCH, 10).set(RIGHT_LEG_PITCH, -30).set(LEFT_LEG_PITCH, 25));
        def("brute_slam",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -175).set(LEFT_ARM_PITCH, -175).set(RIGHT_ARM_ROLL, -10)
                        .set(LEFT_ARM_ROLL, 10).set(TORSO_PITCH, -15).set(HEAD_PITCH, -15),
                k(RECOVERY, 0f, Ease.INCUBIC).set(RIGHT_ARM_PITCH, -40).set(LEFT_ARM_PITCH, -40).set(TORSO_PITCH, 35)
                        .set(HEAD_PITCH, 20).set(RIGHT_LEG_PITCH, -20).set(LEFT_LEG_PITCH, 20),
                k(RECOVERY, 0.5f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -35).set(LEFT_ARM_PITCH, -35).set(TORSO_PITCH, 32)
                        .set(HEAD_PITCH, 18).set(RIGHT_LEG_PITCH, -18).set(LEFT_LEG_PITCH, 18));
        def("brute_sweep",
                k(ACTIVE, 0f, Ease.OUTCUBIC).set(RIGHT_ARM_PITCH, -90).set(RIGHT_ARM_YAW, 110).set(LEFT_ARM_PITCH, -90)
                        .set(LEFT_ARM_YAW, 80).set(TORSO_YAW, 45).set(HEAD_YAW, -20),
                k(RECOVERY, 0f, Ease.OUTQUAD).set(RIGHT_ARM_PITCH, -85).set(RIGHT_ARM_YAW, -90).set(LEFT_ARM_PITCH, -85)
                        .set(LEFT_ARM_YAW, -60).set(TORSO_YAW, -45).set(HEAD_YAW, 20),
                k(RECOVERY, 0.4f, Ease.LINEAR).set(RIGHT_ARM_PITCH, -75).set(RIGHT_ARM_YAW, -80).set(LEFT_ARM_PITCH, -75)
                        .set(LEFT_ARM_YAW, -55).set(TORSO_YAW, -40).set(HEAD_YAW, 18));
    }
}
