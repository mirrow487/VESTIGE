package dev.vestige.client.combat;

import dev.vestige.client.anim.PlayerAnimations;
import dev.vestige.client.entity.HollowedGeoModel;
import dev.vestige.client.fx.HitFx;
import dev.vestige.client.fx.TelegraphFx;
import dev.vestige.client.hud.CombatHud;
import dev.vestige.combat.CombatData;
import dev.vestige.combat.net.CombatNetwork;
import net.neoforged.bus.api.IEventBus;

/** Client side of the combat system: input, prediction, animation, lock-on, HUD and effects. */
public final class CombatClient {
    private CombatClient() {}

    public static void init(IEventBus modBus, IEventBus gameBus) {
        CombatNetwork.installClientSink(new CombatClientSinkImpl());
        PlayerAnimations.init();
        CombatData.MOVESETS.onClientUpdate(() -> {
            PlayerAnimations.clearCache();
            HollowedGeoModel.clearCache();
        });
        modBus.addListener(CombatKeys::register);
        modBus.addListener(CombatHud::register);
        gameBus.addListener(ClientCombat::onClientTickPre);
        gameBus.addListener(ClientCombat::onClientTickPost);
        gameBus.addListener(ClientCombat::onInteraction);
        gameBus.addListener(ClientCombat::onMovementInput);
        gameBus.addListener(LockOn::onClientTick);
        gameBus.addListener(LockOn::onScroll);
        gameBus.addListener(LockOn::onPlayerTurn);
        gameBus.addListener(LockOn::onRenderFrame);
        gameBus.addListener(LockOn::onRenderLevel);
        gameBus.addListener(CombatHud::hideVanilla);
        gameBus.addListener(TelegraphFxHooks::tick);
        gameBus.addListener(TelegraphFxHooks::render);
        gameBus.addListener(HitFx::onCameraAngles);
    }
}
