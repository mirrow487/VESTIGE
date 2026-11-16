package dev.vestige.catalog;

import dev.vestige.VestigeIds;
import dev.vestige.core.VestigeModule;
import dev.vestige.entity.EntityModule;
import dev.vestige.weapon.WeaponModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative tab and spawn eggs: the place where content from several modules is listed together. */
public final class CatalogModule implements VestigeModule {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VestigeIds.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VestigeIds.MOD_ID);

    public static final DeferredItem<Item> HOLLOWED_SPAWN_EGG = ITEMS.register("hollowed_spawn_egg",
            () -> new DeferredSpawnEggItem(EntityModule.HOLLOWED, 0x8f8a7d, 0x3a3f4a, new Item.Properties()));
    public static final DeferredItem<Item> HOLLOWED_BRUTE_SPAWN_EGG = ITEMS.register("hollowed_brute_spawn_egg",
            () -> new DeferredSpawnEggItem(EntityModule.HOLLOWED_BRUTE, 0x6e695e, 0x8a2f24, new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.vestige"))
            .icon(() -> new ItemStack(WeaponModule.KEEPSAKE_BLADE.get()))
            .displayItems((params, out) -> {
                out.accept(WeaponModule.KEEPSAKE_BLADE.get());
                out.accept(WeaponModule.ASH_PIKE.get());
                out.accept(WeaponModule.HAND_MAUL.get());
                out.accept(HOLLOWED_SPAWN_EGG.get());
                out.accept(HOLLOWED_BRUTE_SPAWN_EGG.get());
            })
            .build());

    @Override
    public String id() {
        return "catalog";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
