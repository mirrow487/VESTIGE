package dev.vestige.client;

import dev.vestige.VestigeIds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point. Everything under {@code dev.vestige.client} is loaded only on the
 * physical client; common code must never reference it ({@code ArchitectureRulesTest}).
 */
@Mod(value = VestigeIds.MOD_ID, dist = Dist.CLIENT)
public final class VestigeClient {
    public VestigeClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
