package dev.vestige.client.combat;

import dev.vestige.client.fx.TelegraphFx;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bridges events into TelegraphFx's package-private handlers. */
final class TelegraphFxHooks {
    private TelegraphFxHooks() {}

    static void tick(ClientTickEvent.Post e) {
        TelegraphFx.tick(e);
    }

    static void render(RenderLevelStageEvent e) {
        TelegraphFx.render(e);
    }
}
