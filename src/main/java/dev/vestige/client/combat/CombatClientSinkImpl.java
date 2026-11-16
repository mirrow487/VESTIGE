package dev.vestige.client.combat;

import dev.vestige.client.fx.HitFx;
import dev.vestige.combat.net.CombatClientSink;
import dev.vestige.combat.net.CombatPayloads;
import net.minecraft.client.Minecraft;

/** Routes server combat messages: the local player reconciles its prediction, others are mirrored. */
public final class CombatClientSinkImpl implements CombatClientSink {
    private static boolean local(int entityId) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getId() == entityId;
    }

    @Override
    public void actionStart(CombatPayloads.ActionStart p) {
        if (local(p.entityId())) {
            ClientCombat.onServerStart(p);
        } else {
            ClientActions.start(p.entityId(), p.action(), p.seq(), p.dirYaw(), p.holding());
        }
    }

    @Override
    public void actionEnd(CombatPayloads.ActionEnd p) {
        if (local(p.entityId())) {
            ClientCombat.onServerEnd(p);
        } else {
            ClientActions.end(p.entityId());
        }
    }

    @Override
    public void actionHold(CombatPayloads.ActionHold p) {
        if (!local(p.entityId())) {
            ClientActions.setHolding(p.entityId(), p.holding());
        }
    }

    @Override
    public void hitFeedback(CombatPayloads.HitFeedback p) {
        HitFx.onHit(p);
    }

    @Override
    public void event(CombatPayloads.Event p) {
        HitFx.onEvent(p);
    }
}
