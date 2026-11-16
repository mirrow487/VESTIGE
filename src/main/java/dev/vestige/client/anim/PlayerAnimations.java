package dev.vestige.client.anim;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.anim.Channel;
import dev.vestige.combat.anim.Ease;
import dev.vestige.combat.anim.PoseKey;
import dev.vestige.combat.anim.PoseLibrary;
import dev.vestige.combat.anim.PoseTimeline;
import dev.vestige.combat.machine.RunningAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Plays combat actions on player models via playerAnimator. Animations are generated from the
 * action's tick timing and its {@link PoseLibrary} sequence (ADR-007), so the swing on screen and
 * the server's hit window are the same ticks. First person shows the third-person arms and item.
 */
public final class PlayerAnimations {
    public static final ResourceLocation LAYER = VestigeIds.id("combat");
    private static final Map<String, KeyframeAnimation> CACHE = new HashMap<>();
    private static final Map<AbstractClientPlayer, Playing> PLAYING = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, SpeedModifier> SPEED = new WeakHashMap<>();
    /**
     * playerAnimator rotates the torso and the whole body the opposite way to vanilla model parts.
     * Measured in-game with the calibration driver (docs/systems/combat.md): positive torso/body yaw
     * and roll turn the model to its left there, while arms and head follow vanilla.
     */
    private static final java.util.Set<Channel> MIRRORED = java.util.EnumSet.of(
            Channel.TORSO_YAW, Channel.TORSO_ROLL, Channel.BODY_YAW, Channel.BODY_ROLL);
    private static final int FADE_IN = 2;
    private static final int FADE_OUT = 3;

    private record Playing(RunningAction action, SpeedModifier speed) {}

    private PlayerAnimations() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<>());
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
    }

    /** Diagnostic summary for the dev driver. */
    public static String debugState(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer == null) {
            return "no layer";
        }
        IAnimation anim = layer.getAnimation();
        return "layerActive=" + layer.isActive() + " anim=" + (anim == null ? "null" : anim.getClass().getSimpleName() + " active=" + anim.isActive());
    }

    public static void play(AbstractClientPlayer player, RunningAction action) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer == null) {
            VestigeIds.LOG.warn("No VESTIGE animation layer on player {}", player.getName().getString());
            return;
        }
        KeyframeAnimation anim = animationFor(action);
        if (anim == null) {
            stop(player);
            return;
        }
        // One speed modifier per player layer, linked when the layer is set up. (A modifier passed to
        // a ModifierLayer constructor before its animation exists is never linked and reads inactive.)
        SpeedModifier speed = SPEED.computeIfAbsent(player, p -> {
            SpeedModifier m = new SpeedModifier(1f);
            layer.addModifierLast(m);
            return m;
        });
        speed.speed = 1f;
        KeyframeAnimationPlayer kap = new KeyframeAnimationPlayer(anim)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_IN, dev.kosmx.playerAnim.core.util.Ease.INOUTSINE), kap);
        PLAYING.put(player, new Playing(action, speed));
    }

    /** Dev/calibration: plays an arbitrary pose sequence on a player. */
    public static void playDebug(AbstractClientPlayer player, List<PoseKey> keys, int windup, int active, int recovery) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer == null) {
            return;
        }
        KeyframeAnimation anim = toKeyframes(PoseTimeline.build(keys, windup, active, recovery));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, dev.kosmx.playerAnim.core.util.Ease.INOUTSINE),
                new KeyframeAnimationPlayer(anim).setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                        .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true)));
        PLAYING.remove(player);
    }

    public static void stop(AbstractClientPlayer player) {
        if (player == null) {
            return;
        }
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer != null && layer.getAnimation() != null) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_OUT, dev.kosmx.playerAnim.core.util.Ease.INOUTSINE), null);
        }
        PLAYING.remove(player);
    }

    /** Called each client tick: start/stop animations for remote players and keep speed in sync. */
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            PLAYING.clear();
            return;
        }
        for (AbstractClientPlayer player : level.players()) {
            RunningAction action = ClientActions.of(player);
            Playing playing = PLAYING.get(player);
            if (action == null) {
                if (playing != null) {
                    stop(player);
                }
                continue;
            }
            if (playing == null || playing.action() != action) {
                play(player, action);
                playing = PLAYING.get(player);
                if (playing == null) {
                    continue;
                }
            }
            boolean frozen = action.hitstop() > 0 || (action.holding() && action.elapsed() >= action.def.timing().windup());
            playing.speed().speed = frozen ? 0f : 1f;
            if (action.elapsed() >= action.def.total() - FADE_OUT) {
                stop(player);
            }
        }
    }

    private static KeyframeAnimation animationFor(RunningAction action) {
        int dirBucket = Float.isNaN(action.dirYaw) ? -1 : Math.floorMod(Math.round(action.dirYaw / 45f), 8);
        String key = action.ref + "|" + dirBucket + "|" + action.def.total();
        return CACHE.computeIfAbsent(key, k -> build(action.def, dirBucket));
    }

    private static KeyframeAnimation build(ActionDef def, int dirBucket) {
        List<PoseKey> keys = PoseLibrary.get(def.pose()).orElse(null);
        if (keys == null) {
            VestigeIds.LOG.warn("No pose sequence named '{}'", def.pose());
            return null;
        }
        if (dirBucket >= 0) {
            // Roll toward the input direction: turn the whole model for the duration of the dodge.
            float yaw = dirBucket * 45f;
            List<PoseKey> turned = new ArrayList<>();
            for (PoseKey k : keys) {
                Map<Channel, Float> v = new java.util.EnumMap<>(Channel.class);
                v.putAll(k.values());
                v.put(Channel.BODY_YAW, yaw);
                turned.add(new PoseKey(k.anchor(), k.fraction(), Map.copyOf(v), k.ease()));
            }
            keys = turned;
        }
        PoseTimeline timeline = PoseTimeline.build(keys, def.timing().windup(), def.timing().active(), def.timing().recovery());
        return toKeyframes(timeline);
    }

    /**
     * Converts a shared timeline into a playerAnimator animation (rotations in radians).
     * {@code AnimationFormat} is deprecated in playerAnimator 2.0.4 but the builder has no other constructor.
     */
    @SuppressWarnings("deprecation")
    public static KeyframeAnimation toKeyframes(PoseTimeline timeline) {
        KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        b.beginTick = 0;
        b.endTick = timeline.length();
        b.stopTick = timeline.length() + 1;
        b.isLooped = false;
        b.isEasingBefore = true;
        b.name = "vestige";
        for (Channel c : timeline.channels()) {
            KeyframeAnimation.StateCollection part = b.getPart(c.part);
            if (part == null) {
                continue;
            }
            KeyframeAnimation.StateCollection.State state = switch (c.axis) {
                case "pitch" -> part.pitch;
                case "yaw" -> part.yaw;
                case "roll" -> part.roll;
                case "bend" -> part.bend;
                case "x" -> part.x;
                case "y" -> part.y;
                default -> part.z;
            };
            if (state == null) {
                continue;
            }
            state.setEnabled(true);
            int lastTick = -1;
            for (PoseTimeline.Frame f : timeline.frames()) {
                int tick = Math.round(f.tick());
                if (tick == lastTick) {
                    continue;
                }
                float value = f.values().getOrDefault(c, 0f);
                if (MIRRORED.contains(c)) {
                    value = -value;
                }
                if (c.isRotation()) {
                    value = (float) Math.toRadians(value);
                }
                state.addKeyFrame(tick, value, ease(f.ease()));
                lastTick = tick;
            }
        }
        return b.build();
    }

    private static dev.kosmx.playerAnim.core.util.Ease ease(Ease e) {
        try {
            return dev.kosmx.playerAnim.core.util.Ease.valueOf(e.name());
        } catch (IllegalArgumentException ex) {
            return dev.kosmx.playerAnim.core.util.Ease.LINEAR;
        }
    }
}
