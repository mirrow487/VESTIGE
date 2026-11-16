package dev.vestige.equipment;

import dev.vestige.core.SyncedDataTable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Armor, accessory and set tables (synced to clients), indexed by item per logical side. */
public final class EquipmentData {
    public static final SyncedDataTable<ArmorProfile> ARMOR = new SyncedDataTable<>("armor", "vestige/armor", ArmorProfile.CODEC);
    public static final SyncedDataTable<AccessoryProfile> ACCESSORIES = new SyncedDataTable<>("accessories", "vestige/accessories",
            AccessoryProfile.CODEC);
    public static final SyncedDataTable<ArmorSet> SETS = new SyncedDataTable<>("armor_sets", "vestige/armor_sets", ArmorSet.CODEC);

    private static volatile Map<Item, ArmorProfile> armorServer = Map.of(), armorClient = Map.of();
    private static volatile Map<Item, AccessoryProfile> accServer = Map.of(), accClient = Map.of();
    /** Bumped whenever a table reloads, so cached stat sheets recompute. */
    public static volatile int generation;

    static {
        ARMOR.onServerUpdate(() -> {
            armorServer = index(ARMOR.side(false), ArmorProfile::items);
            generation++;
        });
        ARMOR.onClientUpdate(() -> {
            armorClient = index(ARMOR.side(true), ArmorProfile::items);
            generation++;
        });
        ACCESSORIES.onServerUpdate(() -> {
            accServer = index(ACCESSORIES.side(false), AccessoryProfile::items);
            generation++;
        });
        ACCESSORIES.onClientUpdate(() -> {
            accClient = index(ACCESSORIES.side(true), AccessoryProfile::items);
            generation++;
        });
        SETS.onServerUpdate(() -> generation++);
        SETS.onClientUpdate(() -> generation++);
    }

    private EquipmentData() {}

    private static <T> Map<Item, T> index(Map<ResourceLocation, T> table, java.util.function.Function<T, java.util.List<ResourceLocation>> items) {
        Map<Item, T> map = new HashMap<>();
        table.values().forEach(p -> items.apply(p).forEach(id -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> map.put(item, p))));
        return Map.copyOf(map);
    }

    public static Optional<ArmorProfile> armor(ItemStack stack, boolean client) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable((client ? armorClient : armorServer).get(stack.getItem()));
    }

    public static Optional<AccessoryProfile> accessory(ItemStack stack, boolean client) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable((client ? accClient : accServer).get(stack.getItem()));
    }

    public static Optional<ArmorSet> set(String id, boolean client) {
        return SETS.get(client, ResourceLocation.parse(id));
    }
}
