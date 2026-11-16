package dev.vestige.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vestige.VestigeIds;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.client.combat.ClientCombat;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.HitShape;
import dev.vestige.combat.hit.HitGeometry;
import dev.vestige.combat.machine.RunningAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Visuals for what an attack does on its active frames, for players and enemies alike: expanding
 * sound rings (RING hitboxes: Bell tolls, the Tolling Ram's Toll Ring) drawn exactly where the
 * hitbox is, and ground shockwaves running along a thrust line ({@code fx: "wake"}: Cleaving Wake).
 */
public final class ActionFx {
    private static final ResourceLocation RING = VestigeIds.id("textures/misc/telegraph_ring.png");
    private static final Random RANDOM = new Random();
    /** Ticks of each action already turned into particles (one wave per active tick). */
    private static final Map<RunningAction, Integer> DONE = new java.util.WeakHashMap<>();
    /** For tests/driver: rings and wakes shown. */
    public static volatile int ringsShown;
    public static volatile int wakesShown;

    private ActionFx() {}

    /** Every running action in view: mirrored ones plus the local player's predicted one. */
    private static void forEachAction(BiConsumer<Entity, RunningAction> f) {
        Minecraft mc = Minecraft.getInstance();
        Map<Integer, RunningAction> all = new HashMap<>(ClientActions.all());
        if (mc.player != null && ClientCombat.machine().current() != null) {
            all.put(mc.player.getId(), ClientCombat.machine().current());
        }
        all.forEach((id, a) -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) {
                f.accept(e, a);
            }
        });
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        forEachAction((e, a) -> {
            if (a.phase() != ActionPhase.ACTIVE || a.def.hit().isEmpty()) {
                return;
            }
            int index = a.elapsed() - a.def.timing().windup();
            Integer done = DONE.get(a);
            if (done != null && done >= index) {
                return;
            }
            DONE.put(a, index);
            HitShape shape = a.def.hit().get();
            boolean wake = a.def.effects().fx().map("wake"::equals).orElse(false);
            if (shape.kind() == HitShape.Kind.RING) {
                ringParticles(mc, e, a, shape, index);
            } else if (wake && shape.kind() == HitShape.Kind.THRUST) {
                wakeParticles(mc, e, shape, index, a.def.timing().active());
            }
        });
    }

    private static void ringParticles(Minecraft mc, Entity e, RunningAction a, HitShape shape, int index) {
        double[] band = HitGeometry.ringBand(shape.reach(), shape.radius(), index, a.def.timing().active());
        Vec3 c = center(e, shape, 1f);
        int n = 10 + (int) (band[1] * 6);
        boolean player = e instanceof Player;
        for (int i = 0; i < n; i++) {
            double ang = (i + RANDOM.nextDouble()) / n * Math.PI * 2;
            double x = c.x + Math.cos(ang) * band[1], z = c.z + Math.sin(ang) * band[1];
            mc.level.addParticle(player ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, x, c.y + 0.15, z, 0, 0.05, 0);
        }
        if (index == 0) {
            ringsShown++;
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f,
                    player ? 1.25f : 0.8f, false);
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.0f, false);
        }
    }

    private static void wakeParticles(Minecraft mc, Entity e, HitShape shape, int index, int count) {
        Vec3 fwd = Vec3.directionFromRotation(0f, e.getYRot());
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        double from = shape.reach() * index / Math.max(1, count);
        double to = shape.reach() * (index + 1) / Math.max(1, count);
        for (double d = from; d <= to; d += 0.45) {
            Vec3 p = e.position().add(fwd.scale(d));
            BlockState ground = mc.level.getBlockState(BlockPos.containing(p.x, p.y - 0.2, p.z));
            for (int i = 0; i < 4; i++) {
                double s = (RANDOM.nextDouble() - 0.5) * shape.radius() * 2;
                Vec3 q = p.add(side.scale(s));
                if (!ground.isAir()) {
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), q.x, q.y + 0.1, q.z,
                            fwd.x * 0.1, 0.25 + RANDOM.nextDouble() * 0.2, fwd.z * 0.1);
                }
            }
            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 0.3, p.z, 0, 0, 0);
            mc.level.addParticle(ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, fwd.x * 0.05, 0.03, fwd.z * 0.05);
        }
        if (index == 0) {
            wakesShown++;
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f, false);
        }
    }

    private static Vec3 center(Entity e, HitShape shape, float partial) {
        Vec3 fwd = Vec3.directionFromRotation(0f, e.getViewYRot(partial));
        return e.getPosition(partial).add(fwd.scale(shape.offset()));
    }

    /** Draws the expanding band of every active RING hitbox on the ground, smoothly between ticks. */
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
        boolean[] any = {false};
        forEachAction((e, a) -> {
            if (a.phase() != ActionPhase.ACTIVE || a.def.hit().isEmpty() || a.def.hit().get().kind() != HitShape.Kind.RING) {
                return;
            }
            HitShape shape = a.def.hit().get();
            float t = ClientActions.animTick(a, partial) - a.def.timing().windup();
            float r = Mth.clamp((t + 1f) / a.def.timing().active(), 0f, 1f) * shape.reach();
            int alpha = (int) (230 * (1f - 0.5f * r / shape.reach()));
            boolean player = e instanceof Player;
            Vec3 c = center(e, shape, partial);
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(c.x - cam.x, c.y - cam.y + 0.06, c.z - cam.z);
            VertexConsumer vc = buffers.getBuffer(type);
            int red = player ? 250 : 255, green = player ? 214 : 80, blue = player ? 120 : 50;
            quad(vc, ps.last(), r, red, green, blue, alpha);
            ps.popPose();
            any[0] = true;
        });
        if (any[0]) {
            buffers.endBatch(type);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, int red, int g, int b, int a) {
        vertex(vc, pose, -r, -r, 0, 0, red, g, b, a);
        vertex(vc, pose, -r, r, 0, 1, red, g, b, a);
        vertex(vc, pose, r, r, 1, 1, red, g, b, a);
        vertex(vc, pose, r, -r, 1, 0, red, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose, x, 0, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
