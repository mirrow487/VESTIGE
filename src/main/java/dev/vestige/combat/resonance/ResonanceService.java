package dev.vestige.combat.resonance;

import dev.vestige.combat.CombatModule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** All Resonance changes go through here (server-authoritative). */
public final class ResonanceService {
    private ResonanceService() {}

    public static float get(Player player) {
        return player.getData(CombatModule.RESONANCE).value();
    }

    /** Gains scale with Cadence (StatSheet.resonanceGain); a Hushed player gains nothing (GDD §7.10). */
    public static void add(ServerPlayer player, float amount) {
        if (amount > 0f) {
            if (dev.vestige.combat.status.StatusService.active(player, dev.vestige.stats.StatusType.HUSHED)) {
                return;
            }
            amount *= dev.vestige.stats.Stats.of(player).resonanceGain;
        }
        Resonance r = player.getData(CombatModule.RESONANCE);
        float before = r.value();
        r.set(before + amount);
        if (r.value() != before) {
            player.syncData(CombatModule.RESONANCE);
        }
    }

    /** Spends the cost if available. */
    public static boolean spend(ServerPlayer player, float cost) {
        if (get(player) + 1.0e-4f < cost) {
            return false;
        }
        Resonance r = player.getData(CombatModule.RESONANCE);
        r.set(r.value() - cost);
        player.syncData(CombatModule.RESONANCE);
        return true;
    }

    public static void set(ServerPlayer player, float value) {
        player.getData(CombatModule.RESONANCE).set(value);
        player.syncData(CombatModule.RESONANCE);
    }

    /** Fades out of combat. */
    public static void tick(ServerPlayer player, long ticksSinceCombat) {
        if (dev.vestige.stats.Stats.of(player).has(dev.vestige.stats.Perks.UNSUNG_RING)) {
            return; // the Ring of the Unsung: Resonance never fades
        }
        float decay = ResonanceRules.decayThisTick(ticksSinceCombat);
        if (decay > 0f && get(player) > 0f) {
            add(player, -decay);
        }
    }
}
