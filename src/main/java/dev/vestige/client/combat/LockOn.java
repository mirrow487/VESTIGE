package dev.vestige.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vestige.VestigeIds;
import dev.vestige.combat.action.ActionPhase;
import dev.vestige.combat.action.Telegraph;
import dev.vestige.combat.machine.RunningAction;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Lock-on targeting (GDD §7.9): nearest enemy in a 60° view cone within 24 blocks with line of
 * sight; scroll switches left/right; lost beyond 28 blocks or after 2 s without sight. The
 * player's real yaw/pitch track the target with a critically damped follow, so movement becomes
 * strafing and the server sees the same facing.
 */
public final class LockOn {
    public static final double ACQUIRE_RANGE = 24.0;
    public static final double KEEP_RANGE = 28.0;
    private static final double CONE_DEG = 60.0;
    private static final int LOS_GRACE_TICKS = 40;
    private static final float FOLLOW_RATE = 14f;
    private static final ResourceLocation RETICLE = VestigeIds.id("textures/misc/lock_reticle.png");

    private static int targetId = -1;
    private static int noSightTicks;
    private static long lastFrameNanos;

    private LockOn() {}

    public static Optional<LivingEntity> target() {
        var level = Minecraft.getInstance().level;
        if (targetId < 0 || level == null) {
            return Optional.empty();
        }
        return level.getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? Optional.of(le) : Optional.empty();
    }

    public static boolean locked() {
        return target().isPresent();
    }

    public static void toggle() {
        if (locked()) {
            release();
        } else {
            acquire().ifPresent(e -> {
                targetId = e.getId();
                noSightTicks = 0;
            });
        }
    }

    public static void release() {
        targetId = -1;
    }

    private static void release(String reason) {
        if (targetId >= 0) {
            dev.vestige.VestigeIds.LOG.debug("Lock-on released: {}", reason);
            lastReleaseReason = reason;
        }
        release();
    }

    /** Why the last automatic release happened (for diagnostics and the dev driver). */
    public static volatile String lastReleaseReason = "";

    static boolean candidate(LocalPlayer player, Entity e) {
        return e instanceof Mob mob && mob.isAlive() && !mob.isInvisible() && e != player.getVehicle();
    }

    private static Optional<LivingEntity> acquire() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        return player.level().getEntities(player, player.getBoundingBox().inflate(ACQUIRE_RANGE), e -> candidate(player, e)).stream()
                .map(e -> (LivingEntity) e)
                .filter(e -> e.distanceTo(player) <= ACQUIRE_RANGE && player.hasLineOfSight(e))
                .filter(e -> angleTo(eye, look, aimPoint(e, 1f)) <= CONE_DEG)
                .min(Comparator.comparingDouble(e -> angleTo(eye, look, aimPoint(e, 1f)) + e.distanceTo(player) * 1.5));
    }

    private static double angleTo(Vec3 eye, Vec3 look, Vec3 point) {
        Vec3 dir = point.subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(dir.dot(look), -1.0, 1.0)));
    }

    /** Where the camera aims: upper chest. */
    static Vec3 aimPoint(Entity e, float partialTick) {
        return e.getPosition(partialTick).add(0, e.getBbHeight() * 0.7, 0);
    }

    /** Scroll switches to the nearest target on that side (cancels hotbar scrolling while locked). */
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<LivingEntity> current = target();
        if (player == null || current.isEmpty() || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        boolean right = event.getScrollDeltaY() < 0;
        double currentYaw = yawTo(player, current.get());
        player.level().getEntities(player, player.getBoundingBox().inflate(ACQUIRE_RANGE), e -> candidate(player, e) && e != current.get())
                .stream()
                .filter(e -> e.distanceTo(player) <= ACQUIRE_RANGE && player.hasLineOfSight(e))
                .map(e -> new Object[] {e, Mth.wrapDegrees(yawTo(player, e) - currentYaw)})
                .filter(o -> right ? (double) o[1] > 0 : (double) o[1] < 0)
                .min(Comparator.comparingDouble(o -> Math.abs((double) o[1])))
                .ifPresent(o -> targetId = ((Entity) o[0]).getId());
    }

    private static double yawTo(LocalPlayer player, Entity e) {
        return Math.toDegrees(Math.atan2(e.getZ() - player.getZ(), e.getX() - player.getX())) - 90.0;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            targetId = -1;
            return;
        }
        while (CombatKeys.LOCK_ON.consumeClick()) {
            toggle();
        }
        Optional<LivingEntity> t = target();
        if (targetId >= 0 && t.isEmpty()) {
            release("target gone");
            return;
        }
        t.ifPresent(e -> {
            if (e.distanceTo(player) > KEEP_RANGE) {
                release("out of range");
                return;
            }
            if (!ClientCombat.armed()) {
                release("not armed");
                return;
            }
            noSightTicks = player.hasLineOfSight(e) ? 0 : noSightTicks + 1;
            if (noSightTicks > LOS_GRACE_TICKS) {
                release("no line of sight");
            }
        });
    }

    /** Mouse look is disabled while locked; the camera follows the target instead. */
    static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (locked()) {
            event.setMouseSensitivity(-1.0 / 3.0); // makes vanilla's turn factor exactly zero
            event.setCinematicCameraEnabled(false);
        }
    }

    static void onRenderFrame(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        LocalPlayer player = Minecraft.getInstance().player;
        Optional<LivingEntity> t = target();
        if (player == null || t.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 to = aimPoint(t.get(), partial).subtract(eye);
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float wantPitch = Mth.clamp((float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG), -50f, 50f);
        float k = 1f - (float) Math.exp(-dt * FOLLOW_RATE);
        float yaw = player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * k;
        float pitch = player.getXRot() + (wantPitch - player.getXRot()) * k;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    /** Brass diamond on the target; red while it telegraphs an attack you must dodge. */
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Optional<LivingEntity> t = target();
        if (t.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean danger = isDangerous(ClientActions.of(t.get()));
        Camera cam = event.getCamera();
        // Draw in front of the target (toward the camera) so its own model never hides the reticle.
        Vec3 at = aimPoint(t.get(), partial).subtract(cam.getPosition());
        double dist = at.length();
        if (dist > 1.0e-3) {
            at = at.scale(Math.max(0.5, dist - (t.get().getBbWidth() * 0.5 + 0.35)) / dist);
        }
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(cam.rotation());
        float size = 0.45f + (danger ? 0.06f * Mth.sin((Minecraft.getInstance().level.getGameTime() + partial) * 1.5f) : 0f);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(RETICLE);
        VertexConsumer vc = buffers.getBuffer(type);
        int r = 255, g = danger ? 60 : 255, b = danger ? 50 : 255;
        quad(vc, ps.last(), size, r, g, b, 255);
        buffers.endBatch(type);
        ps.popPose();
    }

    static boolean isDangerous(RunningAction action) {
        if (action == null || action.phase() != ActionPhase.WINDUP) {
            return false;
        }
        return action.def.telegraph().map(Telegraph::glint).map(g -> g == Telegraph.Glint.RED || g == Telegraph.Glint.GRAB).orElse(false);
    }

    /** Double-sided billboard quad (the render type culls back faces). */
    static void quad(VertexConsumer vc, PoseStack.Pose pose, float s, int r, int g, int b, int a) {
        float[][] corners = {{-s, -s, 0, 1}, {s, -s, 1, 1}, {s, s, 1, 0}, {-s, s, 0, 0}};
        for (int i = 0; i < 4; i++) {
            vertex(vc, pose, corners[i], r, g, b, a);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(vc, pose, corners[i], r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float[] c, int r, int g, int b, int a) {
        vc.addVertex(pose, c[0], c[1], 0).setColor(r, g, b, a).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
