package dev.vestige.equipment;

import dev.vestige.player.GlimService;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Attunement (GDD §8.6): spending Glim at a Waylight to raise attributes. Each point costs the
 * next level's price; the whole purchase is validated and paid at once.
 */
public final class Attunement {
    private Attunement() {}

    /** Total Glim to buy {@code points} levels starting from {@code level}. */
    public static long cost(int level, int points) {
        long total = 0;
        for (int i = 0; i < points; i++) {
            total += AttributeRules.levelCost(level + i);
        }
        return total;
    }

    /** Applies the purchase if affordable and within caps. Returns the Glim spent, or -1 if refused. */
    public static long attune(ServerPlayer player, Map<Attribute, Integer> add) {
        CharacterAttributes attrs = player.getData(EquipmentModule.ATTRIBUTES);
        int points = 0;
        for (var e : add.entrySet()) {
            if (e.getValue() < 0 || attrs.get(e.getKey()) + e.getValue() > AttributeRules.MAX) {
                return -1;
            }
            points += e.getValue();
        }
        if (points == 0) {
            return 0;
        }
        long cost = cost(attrs.level(), points);
        if (!player.getAbilities().instabuild && !GlimService.spend(player, cost)) {
            return -1;
        }
        add.forEach((a, n) -> attrs.set(a, attrs.get(a) + n));
        player.syncData(EquipmentModule.ATTRIBUTES);
        EquipmentStats.invalidate(player);
        EquipmentModule.applyDerived(player);
        return player.getAbilities().instabuild ? 0 : cost;
    }

    /** For tests and commands. */
    public static void set(ServerPlayer player, Attribute a, int value) {
        player.getData(EquipmentModule.ATTRIBUTES).set(a, value);
        player.syncData(EquipmentModule.ATTRIBUTES);
        EquipmentStats.invalidate(player);
        EquipmentModule.applyDerived(player);
    }
}
