package dev.vestige.client.combat;

import dev.vestige.combat.CombatData;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.machine.RunningAction;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client mirror of every entity's current action (enemies and other players), advanced each
 * client tick with the same {@link RunningAction} rules the server uses. The local player's own
 * action lives in {@link ClientCombat}'s predicted machine instead.
 */
public final class ClientActions {
    private static final Map<Integer, RunningAction> ACTIONS = new HashMap<>();

    private ClientActions() {}

    /** Current action of an entity, local player included. */
    public static RunningAction of(Entity entity) {
        if (entity == Minecraft.getInstance().player) {
            return ClientCombat.machine().current();
        }
        return ACTIONS.get(entity.getId());
    }

    public static RunningAction start(int entityId, String action, int seq, float dirYaw, boolean holding) {
        ActionRef ref;
        try {
            ref = ActionRef.parse(action);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return CombatData.resolve(true, ref).map(def -> {
            RunningAction ra = new RunningAction(ref, def, seq, dirYaw, ClientCombat.clientTick(), RunningAction.Kind.OTHER);
            ra.setHolding(holding);
            ACTIONS.put(entityId, ra);
            return ra;
        }).orElse(null);
    }

    public static void end(int entityId) {
        ACTIONS.remove(entityId);
    }

    public static void setHolding(int entityId, boolean holding) {
        RunningAction ra = ACTIONS.get(entityId);
        if (ra != null) {
            ra.setHolding(holding);
        }
    }

    public static void addHitstop(int entityId, int ticks) {
        RunningAction ra = ACTIONS.get(entityId);
        if (ra != null) {
            ra.addHitstop(ticks);
        }
    }

    static void tick() {
        var level = Minecraft.getInstance().level;
        ACTIONS.entrySet().removeIf(e -> level == null || level.getEntity(e.getKey()) == null || e.getValue().tick());
    }

    static void clear() {
        ACTIONS.clear();
    }

    public static Map<Integer, RunningAction> all() {
        return ACTIONS;
    }

    /** Timeline position including partial tick, frozen during hitstop or a held charge. */
    public static float animTick(RunningAction ra, float partialTick) {
        boolean frozen = ra.hitstop() > 0 || (ra.holding() && ra.elapsed() >= ra.def.timing().windup());
        return ra.elapsed() + (frozen ? 0f : partialTick);
    }
}
