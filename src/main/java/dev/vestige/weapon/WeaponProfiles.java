package dev.vestige.weapon;

import dev.vestige.core.SyncedDataTable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Lookup from item to {@link WeaponProfile}, per logical side. */
public final class WeaponProfiles {
    public static final SyncedDataTable<WeaponProfile> TABLE = new SyncedDataTable<>("weapons", "vestige/weapons", WeaponProfile.CODEC);

    private static volatile Map<Item, WeaponProfile> serverByItem = Map.of();
    private static volatile Map<Item, WeaponProfile> clientByItem = Map.of();

    static {
        TABLE.onServerUpdate(() -> serverByItem = index(false));
        TABLE.onClientUpdate(() -> clientByItem = index(true));
    }

    private WeaponProfiles() {}

    private static Map<Item, WeaponProfile> index(boolean client) {
        Map<Item, WeaponProfile> map = new HashMap<>();
        TABLE.side(client).values().forEach(profile -> profile.items().forEach(id ->
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> map.put(item, profile))));
        return Map.copyOf(map);
    }

    public static Optional<WeaponProfile> of(ItemStack stack, boolean clientSide) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((clientSide ? clientByItem : serverByItem).get(stack.getItem()));
    }
}
