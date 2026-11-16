package dev.vestige.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** All Wick Flask changes go through here (GDD §8.4). */
public final class FlaskService {
    private FlaskService() {}

    public static Flask get(Player player) {
        return player.getData(PlayerModule.FLASK);
    }

    public static boolean hasCharge(Player player) {
        return player.getAbilities().instabuild || get(player).charges() > 0;
    }

    /** Uses one charge (creative: free). Returns false if the flask was empty. */
    public static boolean consume(ServerPlayer player) {
        Flask f = get(player);
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (f.charges() <= 0) {
            return false;
        }
        f.setCharges(f.charges() - 1);
        player.syncData(PlayerModule.FLASK);
        return true;
    }

    /** Waylight rest and respawn: back to full. */
    public static void refill(ServerPlayer player) {
        Flask f = get(player);
        f.setCharges(f.max());
        player.syncData(PlayerModule.FLASK);
    }

    /** A Flask Wick: one more charge (max 10), granted filled. Returns false at the cap. */
    public static boolean addWick(ServerPlayer player) {
        Flask f = get(player);
        if (f.max() >= FlaskRules.MAX_CHARGES) {
            return false;
        }
        f.setMax(FlaskRules.afterWick(f.max()));
        f.setCharges(f.charges() + 1);
        player.syncData(PlayerModule.FLASK);
        return true;
    }

    public static void set(ServerPlayer player, int charges, int max, int kindling) {
        Flask f = get(player);
        f.setMax(max);
        f.setCharges(charges);
        f.setKindling(kindling);
        player.syncData(PlayerModule.FLASK);
    }
}
