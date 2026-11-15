package dev.vestige.pale;

import dev.vestige.config.ServerConfig;
import java.util.OptionalDouble;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Keeps the Nether and End borders at a configured fraction of the Overworld border.
 *
 * <p>Vanilla's {@code BorderChangeListener.DelegateBorderChangeListener} copies the Overworld
 * size to every dimension <em>unscaled</em> (only the centre is scaled). This listener is
 * added to the Overworld border after the vanilla delegates, so it runs after them and
 * overwrites their size with the scaled value. Centre, warning and damage settings are left to
 * vanilla. Verified in Milestone 1; see {@code docs/ARCHITECTURE.md §2 #9}.
 */
public final class DimensionBorderScaler implements BorderChangeListener {
    private final MinecraftServer server;

    public DimensionBorderScaler(MinecraftServer server) {
        this.server = server;
    }

    /** Divisor for a dimension, or empty if this scaler leaves it to vanilla. */
    public static OptionalDouble divisorFor(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.NETHER)) {
            return OptionalDouble.of(ServerConfig.NETHER_BORDER_DIVISOR.get());
        }
        if (dimension.equals(Level.END)) {
            return OptionalDouble.of(ServerConfig.END_BORDER_DIVISOR.get());
        }
        return OptionalDouble.empty();
    }

    private static double scaled(double size, double divisor) {
        return BorderScaling.scaledSize(size, divisor, ServerConfig.MIN_SCALED_BORDER_SIZE.get());
    }

    /**
     * Re-derives every dependent border from the Overworld border's current state, including an
     * in-progress lerp. Needed at startup because vanilla re-applies the saved Overworld border
     * through the unscaled delegates when the world loads.
     */
    public void applyNow() {
        WorldBorder source = server.overworld().getWorldBorder();
        long remaining = source.getLerpRemainingTime();
        for (ServerLevel level : server.getAllLevels()) {
            OptionalDouble divisor = divisorFor(level.dimension());
            if (divisor.isEmpty()) {
                continue;
            }
            WorldBorder target = level.getWorldBorder();
            double d = divisor.getAsDouble();
            if (remaining > 0L) {
                target.lerpSizeBetween(scaled(source.getSize(), d), scaled(source.getLerpTarget(), d), remaining);
            } else {
                target.setSize(scaled(source.getSize(), d));
            }
        }
    }

    @Override
    public void onBorderSizeSet(WorldBorder border, double size) {
        for (ServerLevel level : server.getAllLevels()) {
            OptionalDouble divisor = divisorFor(level.dimension());
            if (divisor.isPresent()) {
                level.getWorldBorder().setSize(scaled(size, divisor.getAsDouble()));
            }
        }
    }

    @Override
    public void onBorderSizeLerping(WorldBorder border, double from, double to, long durationMillis) {
        for (ServerLevel level : server.getAllLevels()) {
            OptionalDouble divisor = divisorFor(level.dimension());
            if (divisor.isPresent()) {
                double d = divisor.getAsDouble();
                level.getWorldBorder().lerpSizeBetween(scaled(from, d), scaled(to, d), durationMillis);
            }
        }
    }

    @Override
    public void onBorderCenterSet(WorldBorder border, double x, double z) {}

    @Override
    public void onBorderSetWarningTime(WorldBorder border, int warningTime) {}

    @Override
    public void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks) {}

    @Override
    public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

    @Override
    public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZoneSize) {}
}
