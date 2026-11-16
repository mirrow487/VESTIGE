package dev.vestige.equipment;

import dev.vestige.VestigeIds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Rings, talismans, relics and upgrade materials (GDD §12). Stats live in data (vestige/accessories). */
public final class EquipmentItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VestigeIds.MOD_ID);
    public static final List<DeferredItem<Item>> ACCESSORIES = new ArrayList<>();

    // Rings: numbers (GDD §12.1)
    public static final DeferredItem<Item> RING_OF_QUICK_BREATH = accessory("ring_of_quick_breath");
    public static final DeferredItem<Item> BELLBRONZE_BAND = accessory("bellbronze_band");
    public static final DeferredItem<Item> WAXEN_RING = accessory("waxen_ring");
    public static final DeferredItem<Item> HOLLOW_RING = accessory("hollow_ring");
    public static final DeferredItem<Item> TITHE_RING = accessory("tithe_ring");
    public static final DeferredItem<Item> RING_OF_THE_UNSUNG = accessory("ring_of_the_unsung");
    public static final DeferredItem<Item> HARTS_RING = accessory("harts_ring");
    public static final DeferredItem<Item> EMBER_SIGNET = accessory("ember_signet");
    // Talismans: rules (GDD §12.2)
    public static final DeferredItem<Item> LIGHTFOOT_FEATHER = accessory("lightfoot_feather");
    public static final DeferredItem<Item> TALISMAN_OF_THE_GROVE = accessory("talisman_of_the_grove");
    public static final DeferredItem<Item> LEDGER_OF_DEBTS = accessory("ledger_of_debts");
    public static final DeferredItem<Item> TALISMAN_OF_SECOND_BREATH = accessory("talisman_of_second_breath");
    public static final DeferredItem<Item> BELL_OF_WARDING = accessory("bell_of_warding");
    // Relics: verbs (GDD §12.3)
    public static final DeferredItem<Item> WINDSAIL_CAPE = accessory("windsail_cape");
    public static final DeferredItem<Item> BELLBRONZE_SPURS = accessory("bellbronze_spurs");
    public static final DeferredItem<Item> DIVERS_BELL = accessory("divers_bell");
    public static final DeferredItem<Item> CLIMBERS_HOOKS = accessory("climbers_hooks");
    public static final DeferredItem<Item> PALE_HOOK = accessory("pale_hook");
    /** Flask Kindling material (GDD §8.4): a coal of the Lamplighters' fire. */
    public static final DeferredItem<Item> KINDLING_EMBER = ITEMS.registerSimpleItem("kindling_ember", new Item.Properties().stacksTo(16));

    private EquipmentItems() {}

    private static DeferredItem<Item> accessory(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1));
        ACCESSORIES.add(item);
        return item;
    }

    static void init(IEventBus modBus) {
        EquipmentArmor.init(modBus);
    }
}
