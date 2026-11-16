package dev.vestige.client.rpg;

import dev.vestige.player.PlayerModule;
import dev.vestige.waylight.WaylightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring for Stage 3 RPG systems: HUD, Waylight menu, flask in hand, Ember-Ghost. */
public final class RpgClient {
    private RpgClient() {}

    public static void init(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RpgHud::register);
        modBus.addListener(RpgClient::layers);
        modBus.addListener(RpgClient::renderers);
        WaylightModule.installClientSink(p -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new WaylightScreen(p))));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PlayerModule.EMBER_GHOST.get(), EmberGhostRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void layers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new FlaskLayer(pr, event.getContext().getItemInHandRenderer()));
            }
        }
    }
}
