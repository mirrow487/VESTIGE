package dev.vestige.client;

import dev.vestige.VestigeIds;
import dev.vestige.client.combat.CombatClient;
import dev.vestige.client.devdriver.DevDriver;
import dev.vestige.client.entity.HollowedGeoRenderer;
import dev.vestige.client.fx.GlintParticle;
import dev.vestige.combat.CombatModule;
import dev.vestige.entity.EntityModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point. Everything under {@code dev.vestige.client} is loaded only on the
 * physical client; common code must never reference it ({@code ArchitectureRulesTest}).
 */
@Mod(value = VestigeIds.MOD_ID, dist = Dist.CLIENT)
public final class VestigeClient {
    public VestigeClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        IEventBus gameBus = NeoForge.EVENT_BUS;
        CombatClient.init(modBus, gameBus);
        modBus.addListener(VestigeClient::renderers);
        modBus.addListener(VestigeClient::particles);
        gameBus.addListener(WeaponTooltips::onTooltip);
        DevDriver.initIfEnabled(gameBus);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityModule.HOLLOWED.get(), ctx -> new HollowedGeoRenderer(ctx, 0.5f, 1.0f));
        event.registerEntityRenderer(EntityModule.HOLLOWED_BRUTE.get(), ctx -> new HollowedGeoRenderer(ctx, 0.75f, 1.1f));
    }

    private static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CombatModule.GLINT_WHITE.get(), GlintParticle.Provider::new);
        event.registerSpriteSet(CombatModule.GLINT_RED.get(), GlintParticle.Provider::new);
    }
}
