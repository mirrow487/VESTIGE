package dev.vestige.combat.player;

import dev.vestige.combat.action.ActionDef;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.stats.AttributeRules;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.DamageType;
import dev.vestige.stats.Perks;
import dev.vestige.stats.StatSheet;
import dev.vestige.stats.Stats;
import dev.vestige.stats.StatusType;
import dev.vestige.weapon.WeaponProfile;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;

/** What a player's swing is worth: weapon damage per type, attribute scaling and equipment bonuses (GDD §7.10, §8.6). */
public final class PlayerAttack {
    public static final float HARTS_RING_BONUS = 0.12f;
    public static final float LEDGER_BONUS = 0.25f;
    public static final float EMBER_SIGNET_SCORCH = 12f;

    private PlayerAttack() {}

    /** Attack rating per type for this action: the action may turn the weapon's physical damage into another physical type. */
    public static DamageSpread spread(Player player, WeaponProfile weapon, ActionDef def) {
        DamageSpread base = weapon.baseDamage();
        if (def != null && def.power().physical().isPresent()) {
            DamageType to = def.power().physical().get();
            EnumMap<DamageType, Float> m = new EnumMap<>(DamageType.class);
            base.forEach((t, v) -> m.merge(t.physical ? to : t, v, Float::sum));
            base = DamageSpread.of(m);
        }
        return AttributeRules.attackRating(base, weapon.scaling(), weapon.requirements(), Stats.of(player).attributes);
    }

    /** Additive damage bonus from equipment and the action (Crescendo, arts, Hart's Ring, Ledger of Debts). */
    public static float bonus(Player player, RunningAction action) {
        StatSheet s = Stats.of(player);
        float b = s.damageBonus + action.damageBonus;
        if (action.kind == RunningAction.Kind.ART || action.def.art().isPresent()) {
            b += s.artBonus;
        }
        if (s.has(Perks.HARTS_RING) && player.getHealth() >= player.getMaxHealth() * 0.8f) {
            b += HARTS_RING_BONUS;
        }
        if (s.has(Perks.LEDGER_OF_DEBTS)) {
            b += LEDGER_BONUS;
        }
        return b;
    }

    /** Status buildup per hit: the weapon's own plus the Ember Signet's Scorch. */
    public static Map<StatusType, Float> status(Player player, WeaponProfile weapon) {
        EnumMap<StatusType, Float> m = new EnumMap<>(StatusType.class);
        m.putAll(weapon.status());
        if (Stats.of(player).has(Perks.EMBER_SIGNET)) {
            m.merge(StatusType.SCORCH, EMBER_SIGNET_SCORCH, Float::sum);
        }
        return m;
    }
}
