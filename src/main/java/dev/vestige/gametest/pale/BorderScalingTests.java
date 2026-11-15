package dev.vestige.gametest.pale;

import dev.vestige.VestigeIds;
import dev.vestige.config.ServerConfig;
import dev.vestige.pale.BorderScaling;
import dev.vestige.pale.DimensionBorderScaler;
import dev.vestige.pale.PaleModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cross-dimension border behaviour (GDD §6.1, ARCHITECTURE §2 #9). Moved from the Milestone 1
 * verification prototype; now exercises the real {@link DimensionBorderScaler}.
 *
 * <p>World borders are global state: every test restores the Overworld border in {@code finally}.
 */
@GameTestHolder(VestigeIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BorderScalingTests {
    private static final String TEMPLATE = "empty";
    private static final String BATCH = "pale_border";

    private BorderScalingTests() {}

    private static WorldBorder overworld(GameTestHelper h) {
        return h.getLevel().getServer().overworld().getWorldBorder();
    }

    private static WorldBorder border(GameTestHelper h, net.minecraft.resources.ResourceKey<Level> dim) {
        return h.getLevel().getServer().getLevel(dim).getWorldBorder();
    }

    private static DimensionBorderScaler requireScaler(GameTestHelper h) {
        return PaleModule.scaler().orElseThrow(() -> new IllegalStateException(
                "DimensionBorderScaler is not installed (border.scaleDimensions=" + ServerConfig.SCALE_DIMENSION_BORDERS.get() + ")"));
    }

    private static double expected(double size, double divisor) {
        return BorderScaling.scaledSize(size, divisor, ServerConfig.MIN_SCALED_BORDER_SIZE.get());
    }

    private static void assertSize(GameTestHelper h, String what, double expected, double actual) {
        h.assertTrue(Math.abs(expected - actual) < 1.0E-6, what + ": expected " + expected + ", got " + actual);
    }

    /** Documents why the scaler exists: with it detached, vanilla gives the Nether the Overworld size. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void vanillaDelegateCopiesSizeUnscaled(GameTestHelper h) {
        DimensionBorderScaler scaler = requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        ow.removeListener(scaler);
        try {
            ow.setSize(1500);
            assertSize(h, "vanilla nether size", 1500, border(h, Level.NETHER).getSize());
        } finally {
            ow.addListener(scaler);
            ow.setSize(old);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void netherScaledOnSetSize(GameTestHelper h) {
        requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        try {
            ow.setSize(2000);
            assertSize(h, "nether size", expected(2000, ServerConfig.NETHER_BORDER_DIVISOR.get()), border(h, Level.NETHER).getSize());
        } finally {
            ow.setSize(old);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void endScaledOnSetSize(GameTestHelper h) {
        requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        try {
            ow.setSize(2500);
            assertSize(h, "end size", expected(2500, ServerConfig.END_BORDER_DIVISOR.get()), border(h, Level.END).getSize());
        } finally {
            ow.setSize(old);
        }
        h.succeed();
    }

    /** The Pale expands with an animated lerp; dependent borders must lerp to the scaled target. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void netherScaledDuringLerp(GameTestHelper h) {
        requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        try {
            ow.setSize(2000);
            ow.lerpSizeBetween(2000, 4000, 60_000L);
            WorldBorder nether = border(h, Level.NETHER);
            double d = ServerConfig.NETHER_BORDER_DIVISOR.get();
            assertSize(h, "nether lerp target", expected(4000, d), nether.getLerpTarget());
            h.assertTrue(nether.getLerpRemainingTime() > 0L, "nether border should be lerping");
        } finally {
            ow.setSize(old);
        }
        h.succeed();
    }

    /** Simulates world load: vanilla re-applies the saved size unscaled; applyNow() must repair it. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void applyNowRepairsUnscaledLoad(GameTestHelper h) {
        DimensionBorderScaler scaler = requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        ow.removeListener(scaler);
        try {
            ow.setSize(3000);
            assertSize(h, "precondition: unscaled nether", 3000, border(h, Level.NETHER).getSize());
        } finally {
            ow.addListener(scaler);
        }
        try {
            scaler.applyNow();
            assertSize(h, "repaired nether size", expected(3000, ServerConfig.NETHER_BORDER_DIVISOR.get()), border(h, Level.NETHER).getSize());
        } finally {
            ow.setSize(old);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void scaledSizeRespectsMinimum(GameTestHelper h) {
        requireScaler(h);
        WorldBorder ow = overworld(h);
        double old = ow.getSize();
        try {
            ow.setSize(8);
            assertSize(h, "clamped nether size", ServerConfig.MIN_SCALED_BORDER_SIZE.get(), border(h, Level.NETHER).getSize());
        } finally {
            ow.setSize(old);
        }
        h.succeed();
    }

    /** The scaler is a server-lifetime singleton attached to this server's Overworld border. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void scalerInstalledOnServerStart(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(PaleModule.scaler().isPresent(), "scaler should be installed after server start");
        h.assertTrue(server.getLevel(Level.NETHER) != null && server.getLevel(Level.END) != null, "nether and end must exist");
        h.succeed();
    }
}
