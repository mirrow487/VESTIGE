package dev.vestige.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/** Registers all VESTIGE config specs. Options are added together with the system that reads them. */
public final class VestigeConfig {
    private VestigeConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }
}
