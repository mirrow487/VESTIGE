package dev.vestige.combat.player;

import dev.vestige.combat.machine.PlayerCombatMachine;
import net.minecraft.world.item.Item;

/** Transient per-player combat state (not saved; actions never survive a relog). */
public final class PlayerCombatState {
    final PlayerCombatMachine machine = new PlayerCombatMachine();
    /** Item the current action was started with; switching items cancels the action. */
    Item actionItem;
    /** Last game tick the player attacked or was attacked; used for "in combat" checks. */
    long lastCombatTick = Long.MIN_VALUE / 2;

    public PlayerCombatMachine machine() {
        return machine;
    }
}
