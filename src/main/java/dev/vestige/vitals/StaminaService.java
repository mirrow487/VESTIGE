package dev.vestige.vitals;

import dev.vestige.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The only way other modules read or change stamina. Server-authoritative. */
public final class StaminaService {
    private StaminaService() {}

    public static Stamina get(Player player) {
        return player.getData(VitalsModule.STAMINA);
    }

    public static boolean canAct(Player player) {
        return player.isCreative() || StaminaRules.canStart(get(player).current());
    }

    /** Spends stamina (creative players spend nothing) and restarts the regeneration delay. */
    public static void spend(ServerPlayer player, float cost) {
        Stamina s = get(player);
        s.markSpent(player.level().getGameTime());
        if (!player.isCreative() && cost > 0f) {
            s.setCurrent(StaminaRules.afterSpend(s.current(), cost));
            player.syncData(VitalsModule.STAMINA);
        }
    }

    /** Called every server tick for each player. */
    static void tick(ServerPlayer player, double regenFactor) {
        Stamina s = get(player);
        float max = ServerConfig.STAMINA_BASE.get().floatValue();
        boolean changed = false;
        if (s.max() != max) {
            s.setMax(max);
            changed = true;
        }
        if (s.current() < s.max()) {
            long since = player.level().getGameTime() - s.lastSpendTick();
            float regen = StaminaRules.regenThisTick(since, ServerConfig.STAMINA_REGEN_DELAY_TICKS.get(),
                    ServerConfig.STAMINA_REGEN_PER_SECOND.get() * regenFactor, regenFactor <= 0.0, player.getFoodData().getFoodLevel());
            if (regen > 0f) {
                s.setCurrent(s.current() + regen);
                changed = true;
            }
        }
        if (changed) {
            player.syncData(VitalsModule.STAMINA);
        }
    }

    /** For tests and commands. */
    public static void set(ServerPlayer player, float value) {
        get(player).setCurrent(value);
        player.syncData(VitalsModule.STAMINA);
    }
}
