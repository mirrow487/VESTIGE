package dev.vestige.combat.net;

import dev.vestige.VestigeIds;
import dev.vestige.combat.player.PlayerCombat;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers combat payloads and routes them. */
public final class CombatNetwork {
    private static volatile CombatClientSink sink = CombatClientSink.NONE;

    private CombatNetwork() {}

    public static void installClientSink(CombatClientSink clientSink) {
        sink = clientSink;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VestigeIds.MOD_ID).versioned("1");
        r.playToServer(CombatPayloads.Input.TYPE, CombatPayloads.Input.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerCombat.handleInput(player, p.seq(), p.input(), p.dirYaw());
            }
        });
        r.playToClient(CombatPayloads.ActionStart.TYPE, CombatPayloads.ActionStart.CODEC, (p, ctx) -> sink.actionStart(p));
        r.playToClient(CombatPayloads.ActionEnd.TYPE, CombatPayloads.ActionEnd.CODEC, (p, ctx) -> sink.actionEnd(p));
        r.playToClient(CombatPayloads.ActionHold.TYPE, CombatPayloads.ActionHold.CODEC, (p, ctx) -> sink.actionHold(p));
        r.playToClient(CombatPayloads.HitFeedback.TYPE, CombatPayloads.HitFeedback.CODEC, (p, ctx) -> sink.hitFeedback(p));
        r.playToClient(CombatPayloads.Event.TYPE, CombatPayloads.Event.CODEC, (p, ctx) -> sink.event(p));
    }
}
