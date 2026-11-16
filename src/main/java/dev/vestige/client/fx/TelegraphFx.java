package dev.vestige.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.combat.CombatModule;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.HitShape;
import dev.vestige.combat.action.Telegraph;
import dev.vestige.combat.machine.RunningAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Enemy telegraphs (GDD §7.12): the glint flashes {@code lead} ticks before the active frames at
 * the attacking hand, with a cue sound per glint type; ground decals fill up over the windup at
 * the spot the attack will land.
 */
public final class TelegraphFx {
    private static final ResourceLocation RING = VestigeIds.id("textures/misc/telegraph_ring.png");
    private static final Set<RunningAction> GLINTED = Collections.newSetFromMap(new WeakHashMap<>());
    /** For tests/driver: number of glints shown since start. */
    public static volatile int glintsShown;

    private TelegraphFx() {}

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        for (Map.Entry<Integer, RunningAction> e : ClientActions.all().entrySet()) {
            RunningAction a = e.getValue();
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null || a.def.telegraph().isEmpty() || GLINTED.contains(a)) {
                continue;
            }
            Telegraph t = a.def.telegraph().get();
            if (t.glint() != Telegraph.Glint.NONE && a.phase() == ActionPhase.WINDUP && a.elapsed() >= a.def.timing().windup() - t.lead()) {
                GLINTED.add(a);
                glint(entity, t.glint());
            }
        }
    }

    private static void glint(Entity entity, Telegraph.Glint glint) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 hand = handPosition(entity);
        var type = glint == Telegraph.Glint.WHITE ? CombatModule.GLINT_WHITE.get() : CombatModule.GLINT_RED.get();
        mc.level.addParticle(type, hand.x, hand.y, hand.z, 0, 0, 0);
        glintsShown++;
        switch (glint) {
            case WHITE -> mc.level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.4f, 1.6f, false);
            case RED -> mc.level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.2f, 0.6f, false);
            case GRAB -> {
                // Rising three-note chime: unmistakably "grab, roll now".
                mc.level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.2f, 0.8f, false);
                mc.level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.2f, 1.06f, false);
                mc.level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.2f, 1.41f, false);
            }
            default -> {
            }
        }
    }

    /** Approximate raised right hand at the windup peak, from the entity's facing. */
    static Vec3 handPosition(Entity e) {
        float yaw = e.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0f, yaw);
        Vec3 right = Vec3.directionFromRotation(0f, yaw + 90f);
        return e.position().add(forward.scale(0.35 * e.getBbWidth() / 0.6)).add(right.scale(0.45 * e.getBbWidth() / 0.6))
                .add(0, e.getBbHeight() * 0.95, 0);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(RING);
        boolean any = false;
        for (Map.Entry<Integer, RunningAction> e : ClientActions.all().entrySet()) {
            RunningAction a = e.getValue();
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null || a.def.telegraph().flatMap(Telegraph::decal).isEmpty()) {
                continue;
            }
            if (a.phase() != ActionPhase.WINDUP && a.phase() != ActionPhase.ACTIVE) {
                continue;
            }
            Telegraph.Decal d = a.def.telegraph().get().decal().get();
            float progress = Mth.clamp(ClientActions.animTick(a, partial) / Math.max(1f, a.def.timing().windup()), 0f, 1f);
            Vec3 forward = Vec3.directionFromRotation(0f, entity.getViewYRot(partial));
            Vec3 base = entity.getPosition(partial);
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(base.x - cam.x, base.y - cam.y + 0.04, base.z - cam.z);
            VertexConsumer vc = buffers.getBuffer(type);
            if (d.kind() == HitShape.Kind.CIRCLE) {
                Vec3 c = forward.scale(d.offset());
                ps.translate(c.x, 0, c.z);
                // Outer ring at full size; inner fill grows toward it as the attack approaches.
                flat(vc, ps.last(), d.radius(), 255, 70, 50, (int) (110 + 120 * progress));
                flat(vc, ps.last(), d.radius() * progress, 255, 40, 30, (int) (60 + 120 * progress));
            } else {
                float yawRad = (float) Math.toRadians(-entity.getViewYRot(partial));
                ps.mulPose(com.mojang.math.Axis.YP.rotation(yawRad));
                rect(vc, ps.last(), d.width() / 2f, d.length(), 255, 60, 40, (int) (80 + 140 * progress));
            }
            ps.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(type);
        }
    }

    private static void flat(VertexConsumer vc, PoseStack.Pose pose, float r, int red, int g, int b, int a) {
        vertex(vc, pose, -r, -r, 0, 0, red, g, b, a);
        vertex(vc, pose, -r, r, 0, 1, red, g, b, a);
        vertex(vc, pose, r, r, 1, 1, red, g, b, a);
        vertex(vc, pose, r, -r, 1, 0, red, g, b, a);
    }

    /** A flat strip; samples the ring texture's bright rim (its centre is nearly transparent). */
    private static void rect(VertexConsumer vc, PoseStack.Pose pose, float halfWidth, float length, int red, int g, int b, int a) {
        vertex(vc, pose, -halfWidth, 0, 0.93f, 0.49f, red, g, b, a);
        vertex(vc, pose, -halfWidth, length, 0.93f, 0.51f, red, g, b, a);
        vertex(vc, pose, halfWidth, length, 0.95f, 0.51f, red, g, b, a);
        vertex(vc, pose, halfWidth, 0, 0.95f, 0.49f, red, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose, x, 0, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
