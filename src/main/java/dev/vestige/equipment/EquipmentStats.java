package dev.vestige.equipment;

import dev.vestige.stats.StatSheet;
import dev.vestige.weapon.ShieldProfiles;
import dev.vestige.weapon.WeaponProfiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Gathers what a player wears and computes their {@link StatSheet}, cached until something changes
 * (equipment, attributes or a data reload). Runs identically on both sides from synced data, so the
 * client's UI and prediction see exactly what the server uses.
 */
public final class EquipmentStats {
    private record Cached(int fingerprint, StatSheet sheet) {}

    private static final Map<Player, Cached> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private EquipmentStats() {}

    public static StatSheet of(Player player) {
        int fp = fingerprint(player);
        Cached c = CACHE.get(player);
        if (c != null && c.fingerprint == fp) {
            return c.sheet;
        }
        StatSheet sheet = compute(player);
        CACHE.put(player, new Cached(fp, sheet));
        return sheet;
    }

    public static void invalidate(Player player) {
        CACHE.remove(player);
    }

    /** Everything that can change the sheet, hashed cheaply. */
    private static int fingerprint(Player player) {
        int h = EquipmentData.generation;
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        for (EquipSlot s : EquipSlot.values()) {
            h = h * 31 + ItemStack.hashItemAndComponents(inv.get(s));
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            h = h * 31 + ItemStack.hashItemAndComponents(player.getItemBySlot(s));
        }
        h = h * 31 + player.getData(EquipmentModule.ATTRIBUTES).asMap().hashCode();
        h = h * 31 + (inv.inStance() ? 1 : 0);
        return h;
    }

    private static StatSheet compute(Player player) {
        boolean client = player.level().isClientSide();
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        List<StatMods> sources = new ArrayList<>();
        Map<String, Integer> sets = new HashMap<>();
        float load = 0f;
        // Armor in the vanilla armor slots.
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var profile = EquipmentData.armor(player.getItemBySlot(slot), client);
            if (profile.isPresent()) {
                sources.add(profile.get().mods());
                load += profile.get().weight();
                profile.get().set().ifPresent(id -> sets.merge(id, 1, Integer::sum));
            }
        }
        sets.forEach((id, n) -> EquipmentData.set(id, client).ifPresent(set -> {
            if (n >= 2) {
                sources.add(set.twoPiece());
            }
            if (n >= 4) {
                sources.add(set.fourPiece());
            }
        }));
        // Accessories.
        for (EquipSlot slot : EquipSlot.values()) {
            if (slot.kind == EquipSlot.Kind.RING || slot.kind == EquipSlot.Kind.TALISMAN || slot.kind == EquipSlot.Kind.RELIC) {
                var profile = EquipmentData.accessory(inv.get(slot), client);
                if (profile.isPresent()) {
                    sources.add(profile.get().mods());
                    load += profile.get().weight();
                }
            }
        }
        // Weapons and shields: the RPG slots (or, in stance, the hands holding them).
        load += weight(Stance.main(player, inv), client) + weight(Stance.secondary(player, inv), client);
        return StatCalculator.compute(player.getData(EquipmentModule.ATTRIBUTES).asMap(), sources, load, sets);
    }

    static float weight(ItemStack stack, boolean client) {
        if (stack.isEmpty()) {
            return 0f;
        }
        var w = WeaponProfiles.of(stack, client);
        if (w.isPresent()) {
            return w.get().weight();
        }
        return ShieldProfiles.of(stack, client).map(dev.vestige.weapon.ShieldProfile::weight).orElse(0f);
    }
}
