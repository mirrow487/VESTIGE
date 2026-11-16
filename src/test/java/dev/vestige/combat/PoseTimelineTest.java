package dev.vestige.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestige.combat.anim.Anchor;
import dev.vestige.combat.anim.Channel;
import dev.vestige.combat.anim.Ease;
import dev.vestige.combat.anim.PoseKey;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.combat.anim.PoseTimeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoseTimelineTest {
    @Test
    void keysAreAnchoredToPhases() {
        List<PoseKey> keys = List.of(
                PoseKey.key(Anchor.ACTIVE, 0f, Ease.LINEAR).set(Channel.RIGHT_ARM_PITCH, -100).build(),
                PoseKey.key(Anchor.RECOVERY, 0f, Ease.LINEAR).set(Channel.RIGHT_ARM_PITCH, -40).build());
        PoseTimeline t = PoseTimeline.build(keys, 5, 3, 7);
        assertEquals(15, t.length());
        assertEquals(0f, t.sample(Channel.RIGHT_ARM_PITCH, 0f), 1e-4);
        assertEquals(-100f, t.sample(Channel.RIGHT_ARM_PITCH, 5f), 1e-4, "windup peak exactly when active frames start");
        assertEquals(-40f, t.sample(Channel.RIGHT_ARM_PITCH, 8f), 1e-4, "strike complete when active frames end");
        assertEquals(0f, t.sample(Channel.RIGHT_ARM_PITCH, 15f), 1e-4, "returns to rest at the end");
        assertEquals(-70f, t.sample(Channel.RIGHT_ARM_PITCH, 6.5f), 1e-4);
    }

    @Test
    void sameKeysScaleToSlowerTimings() {
        List<PoseKey> keys = List.of(PoseKey.key(Anchor.ACTIVE, 0f, Ease.LINEAR).set(Channel.TORSO_YAW, 30).build());
        PoseTimeline slow = PoseTimeline.build(keys, 20, 5, 20);
        assertEquals(30f, slow.sample(Channel.TORSO_YAW, 20f), 1e-4);
        assertEquals(15f, slow.sample(Channel.TORSO_YAW, 10f), 1e-4);
    }

    @Test
    void easeShapesSegmentIntoKey() {
        List<PoseKey> keys = List.of(PoseKey.key(Anchor.ACTIVE, 0f, Ease.OUTCUBIC).set(Channel.HEAD_PITCH, 10).build());
        PoseTimeline t = PoseTimeline.build(keys, 10, 1, 1);
        // OUTCUBIC is fast at first: halfway in time is well past halfway in value.
        assertTrue(t.sample(Channel.HEAD_PITCH, 5f) > 8f);
    }

    @Test
    void everyActionPoseInDataExists() {
        for (String name : List.of("blade_slash_rl", "blade_slash_lr", "blade_overhead", "blade_thrust", "blade_heavy",
                "blade_running", "blade_rolling", "pike_thrust", "pike_thrust_low", "pike_heavy", "pike_running",
                "maul_overhead", "maul_swing", "maul_heavy", "roll", "backstep", "flinch", "stagger",
                "hollowed_swipe", "hollowed_backhand", "hollowed_lunge", "brute_slam", "brute_sweep")) {
            assertTrue(PoseLibrary.get(name).isPresent(), "missing pose " + name);
        }
    }

    @Test
    void easeEndpoints() {
        for (Ease e : Ease.values()) {
            if (e == Ease.CONSTANT) continue;
            assertEquals(0f, e.apply(0f), 1e-4, e.name());
            assertEquals(1f, e.apply(1f), 1e-4, e.name());
        }
    }
}
