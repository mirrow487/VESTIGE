package dev.vestige;

import dev.vestige.config.VestigeConfig;
import dev.vestige.core.ModuleLoader;
import dev.vestige.core.VestigeModules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Common (both sides) entry point. Wires configuration and every gameplay module.
 *
 * <p>Gameplay code does not live here: each system is a {@link dev.vestige.core.VestigeModule}
 * listed in {@link VestigeModules}. See {@code docs/ARCHITECTURE.md §4}.
 */
@Mod(VestigeIds.MOD_ID)
public final class Vestige {
    public Vestige(IEventBus modBus, ModContainer container) {
        VestigeConfig.register(container);
        ModuleLoader.initCommon(VestigeModules.COMMON, modBus, NeoForge.EVENT_BUS);
    }
}
