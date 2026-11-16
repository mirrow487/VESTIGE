package dev.vestige.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** All Glim changes go through here (ARCHITECTURE §5.1). */
public final class GlimService {
    private GlimService() {}

    public static long get(Player player) {
        return player.getData(PlayerModule.GLIM).amount();
    }

    public static void set(ServerPlayer player, long amount) {
        player.getData(PlayerModule.GLIM).set(amount);
        player.syncData(PlayerModule.GLIM);
    }

    public static void add(ServerPlayer player, long amount) {
        if (amount != 0) {
            set(player, get(player) + amount);
        }
    }

    /** Spends Glim if the purse holds enough; returns whether it did. */
    public static boolean spend(ServerPlayer player, long amount) {
        if (get(player) < amount) {
            return false;
        }
        set(player, get(player) - amount);
        return true;
    }
}
