package dev.vestige.dev;

import dev.vestige.VestigeIds;
import dev.vestige.core.VestigeModule;
import dev.vestige.core.VestigeModules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Developer tooling. Inactive unless enabled by a JVM system property, so it has no effect in
 * normal play.
 *
 * <ul>
 *   <li>{@code -Dvestige.dev.autostop=true}: after server start, log a smoke-check summary and
 *       stop. Used by {@code ./gradlew runServer -Psmoke} and the production-server smoke script.</li>
 * </ul>
 */
public final class DevModule implements VestigeModule {
    public static final String AUTOSTOP_PROPERTY = "vestige.dev.autostop";

    @Override
    public String id() {
        return "dev";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        if (Boolean.getBoolean(AUTOSTOP_PROPERTY)) {
            gameBus.addListener(DevModule::smokeCheckAndStop);
        }
    }

    private static void smokeCheckAndStop(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        double overworld = server.overworld().getWorldBorder().getSize();
        var nether = server.getLevel(Level.NETHER);
        var end = server.getLevel(Level.END);
        VestigeIds.LOG.info("VESTIGE SMOKE modules={} overworldBorder={} netherBorder={} endBorder={}",
                VestigeModules.ids(), overworld,
                nether == null ? "n/a" : nether.getWorldBorder().getSize(),
                end == null ? "n/a" : end.getWorldBorder().getSize());
        VestigeIds.LOG.info("VESTIGE SMOKE OK: stopping server ({}=true)", AUTOSTOP_PROPERTY);
        server.halt(false);
    }
}
