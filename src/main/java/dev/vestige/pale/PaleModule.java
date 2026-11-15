package dev.vestige.pale;

import dev.vestige.VestigeIds;
import dev.vestige.config.ServerConfig;
import dev.vestige.core.VestigeModule;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The Pale (GDD §6.1). Milestone 2 contains the cross-dimension border scaling; progression-driven
 * expansion, rings and Pale-touched spawns arrive with world generation (Milestone 6).
 */
public final class PaleModule implements VestigeModule {
    private static volatile DimensionBorderScaler installed;

    @Override
    public String id() {
        return "pale";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(PaleModule::onServerStarted);
        gameBus.addListener(PaleModule::onServerStopped);
        modBus.addListener(PaleModule::onConfigReloaded);
    }

    /** The scaler attached to the running server's Overworld border, if enabled. */
    public static Optional<DimensionBorderScaler> scaler() {
        return Optional.ofNullable(installed);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (!ServerConfig.SCALE_DIMENSION_BORDERS.get()) {
            VestigeIds.LOG.info("Dimension border scaling disabled by config");
            return;
        }
        install(event.getServer());
    }

    private static void install(MinecraftServer server) {
        WorldBorder overworld = server.overworld().getWorldBorder();
        DimensionBorderScaler scaler = new DimensionBorderScaler(server);
        // Added after vanilla's delegate listeners (registered during level creation), so it runs after them.
        overworld.addListener(scaler);
        installed = scaler;
        scaler.applyNow();
        VestigeIds.LOG.info("Dimension border scaling active (nether /{}, end /{})",
                ServerConfig.NETHER_BORDER_DIVISOR.get(), ServerConfig.END_BORDER_DIVISOR.get());
    }

    private static void uninstall(MinecraftServer server) {
        DimensionBorderScaler scaler = installed;
        if (scaler != null) {
            server.overworld().getWorldBorder().removeListener(scaler);
            installed = null;
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        installed = null;
    }

    private static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC || event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // Config reload arrives off the server thread; apply on it.
        server.execute(() -> {
            boolean enabled = ServerConfig.SCALE_DIMENSION_BORDERS.get();
            if (enabled && installed == null) {
                install(server);
            } else if (!enabled && installed != null) {
                uninstall(server);
            } else if (installed != null) {
                installed.applyNow();
            }
        });
    }
}
