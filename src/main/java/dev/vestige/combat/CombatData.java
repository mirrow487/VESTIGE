package dev.vestige.combat;

import dev.vestige.VestigeIds;
import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.action.ActionRef;
import dev.vestige.combat.action.MoveSet;
import dev.vestige.combat.machine.PlayerCombatMachine.MoveSetHandle;
import dev.vestige.core.SyncedDataTable;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Movesets from {@code data/<ns>/vestige/movesets/*.json}, synced to clients. */
public final class CombatData {
    public static final SyncedDataTable<MoveSet> MOVESETS = new SyncedDataTable<>("movesets", "vestige/movesets", MoveSet.CODEC);

    /** Shared player actions: roll, backstep, flinch, stagger. */
    public static final ResourceLocation PLAYER_MOVESET = VestigeIds.id("player");

    private CombatData() {}

    public static Optional<MoveSetHandle> handle(boolean clientSide, ResourceLocation id) {
        return MOVESETS.get(clientSide, id).map(set -> new MoveSetHandle(id, set));
    }

    public static Optional<ActionDef> resolve(boolean clientSide, ActionRef ref) {
        return MOVESETS.get(clientSide, ref.moveset()).flatMap(set -> set.action(ref.action()));
    }
}
