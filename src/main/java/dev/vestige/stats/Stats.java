package dev.vestige.stats;

import java.util.function.Function;
import net.minecraft.world.entity.player.Player;

/**
 * The one place every system reads a player's stats from. The equipment module installs the
 * provider; without it (or for non-players) the base sheet applies. Keeps combat, vitals and the
 * player module independent of equipment (ARCHITECTURE §3).
 */
public final class Stats {
    private static volatile Function<Player, StatSheet> provider = p -> StatSheet.BASE;

    private Stats() {}

    public static void install(Function<Player, StatSheet> p) {
        provider = p;
    }

    public static StatSheet of(Player player) {
        StatSheet s = provider.apply(player);
        return s == null ? StatSheet.BASE : s;
    }
}
