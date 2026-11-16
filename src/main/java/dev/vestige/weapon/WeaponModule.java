package dev.vestige.weapon;

import dev.vestige.VestigeIds;
import dev.vestige.core.VestigeModule;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Weapon items and weapon stat profiles (GDD §10). */
public final class WeaponModule implements VestigeModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VestigeIds.MOD_ID);

    // Tier 0 "Wanderer" weapons (GDD §10.2). Stats live in data/vestige/vestige/weapons/*.json.
    public static final DeferredItem<Item> KEEPSAKE_BLADE = ITEMS.registerSimpleItem("keepsake_blade", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> ASH_PIKE = ITEMS.registerSimpleItem("ash_pike", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> HAND_MAUL = ITEMS.registerSimpleItem("hand_maul", new Item.Properties().stacksTo(1));
    /** Tier 0 parrying shield (GDD §10.2). */
    public static final DeferredItem<Item> PLANK_BUCKLER = ITEMS.registerSimpleItem("plank_buckler", new Item.Properties().stacksTo(1));
    /** Tier 0 bow (GDD §10.2); fires arrows from the inventory. */
    public static final DeferredItem<Item> HUNTING_BOW = ITEMS.registerSimpleItem("hunting_bow", new Item.Properties().stacksTo(1));
    /** Tier 0 block shields (kite and tower), craftable. */
    public static final DeferredItem<Item> IRON_KITE_SHIELD = ITEMS.registerSimpleItem("iron_kite_shield", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> TIMBER_BULWARK = ITEMS.registerSimpleItem("timber_bulwark", new Item.Properties().stacksTo(1));

    // Later-tier class weapons (GDD §10.2). Their recipes and drops arrive with their gates (Bellwether,
    // Mother Tallow: Milestone 6); until then they come from the creative tab or commands.
    public static final DeferredItem<Item> TAPER_FANG = ITEMS.registerSimpleItem("taper_fang", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> WICKIRON_GREATBLADE = ITEMS.registerSimpleItem("wickiron_greatblade", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> CHANDLERS_CENSER = ITEMS.registerSimpleItem("chandlers_censer", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> HANDBELL = ITEMS.registerSimpleItem("handbell", new Item.Properties().stacksTo(1));

    @Override
    public String id() {
        return "weapon";
    }

    @Override
    public void init(IEventBus modBus, IEventBus gameBus) {
        ITEMS.register(modBus);
        WeaponProfiles.TABLE.register(modBus, gameBus);
        ShieldProfiles.TABLE.register(modBus, gameBus);
    }
}
