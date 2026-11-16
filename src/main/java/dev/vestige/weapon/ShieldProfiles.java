package dev.vestige.weapon;

import dev.vestige.core.SyncedDataTable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Lookup from item to {@link ShieldProfile}, per logical side. */
public final class ShieldProfiles {
    public static final SyncedDataTable<ShieldProfile> TABLE = new SyncedDataTable<>("shields", "vestige/shields", ShieldProfile.CODEC);

    private static volatile Map<Item, ShieldProfile> serverByItem = Map.of();
    private static volatile Map<Item, ShieldProfile> clientByItem = Map.of();

    static {
        TABLE.onServerUpdate(() -> serverByItem = index(false));
        TABLE.onClientUpdate(() -> clientByItem = index(true));
    }

    private ShieldProfiles() {}

    private static Map<Item, ShieldProfile> index(boolean client) {
        Map<Item, ShieldProfile> map = new HashMap<>();
        TABLE.side(client).values().forEach(p -> p.items().forEach(id -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(i -> map.put(i, p))));
        return Map.copyOf(map);
    }

    public static Optional<ShieldProfile> of(ItemStack stack, boolean clientSide) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable((clientSide ? clientByItem : serverByItem).get(stack.getItem()));
    }
}
