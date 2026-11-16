package dev.vestige.equipment;

import dev.vestige.VestigeIds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** The armor sets (GDD §11): Wanderer's Garb (light), Bellbronze Mail (medium), Wickiron Plate (heavy). */
public final class EquipmentArmor {
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, VestigeIds.MOD_ID);
    public static final List<DeferredItem<VestigeArmorItem>> ALL = new ArrayList<>();

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WANDERER = material("wanderer", 15, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BELLBRONZE = material("bellbronze", 20, SoundEvents.ARMOR_EQUIP_GOLD);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WICKIRON = material("wickiron", 28, SoundEvents.ARMOR_EQUIP_NETHERITE);

    public static final DeferredItem<VestigeArmorItem> WANDERER_HOOD = piece("wanderer_hood", WANDERER, ArmorItem.Type.HELMET, 12);
    public static final DeferredItem<VestigeArmorItem> WANDERER_COAT = piece("wanderer_coat", WANDERER, ArmorItem.Type.CHESTPLATE, 12);
    public static final DeferredItem<VestigeArmorItem> WANDERER_TROUSERS = piece("wanderer_trousers", WANDERER, ArmorItem.Type.LEGGINGS, 12);
    public static final DeferredItem<VestigeArmorItem> WANDERER_BOOTS = piece("wanderer_boots", WANDERER, ArmorItem.Type.BOOTS, 12);
    public static final DeferredItem<VestigeArmorItem> BELLBRONZE_HELM = piece("bellbronze_helm", BELLBRONZE, ArmorItem.Type.HELMET, 20);
    public static final DeferredItem<VestigeArmorItem> BELLBRONZE_MAIL = piece("bellbronze_mail", BELLBRONZE, ArmorItem.Type.CHESTPLATE, 20);
    public static final DeferredItem<VestigeArmorItem> BELLBRONZE_TASSETS = piece("bellbronze_tassets", BELLBRONZE, ArmorItem.Type.LEGGINGS, 20);
    public static final DeferredItem<VestigeArmorItem> BELLBRONZE_SABATONS = piece("bellbronze_sabatons", BELLBRONZE, ArmorItem.Type.BOOTS, 20);
    public static final DeferredItem<VestigeArmorItem> WICKIRON_HELM = piece("wickiron_helm", WICKIRON, ArmorItem.Type.HELMET, 30);
    public static final DeferredItem<VestigeArmorItem> WICKIRON_PLATE = piece("wickiron_plate", WICKIRON, ArmorItem.Type.CHESTPLATE, 30);
    public static final DeferredItem<VestigeArmorItem> WICKIRON_CUISSES = piece("wickiron_cuisses", WICKIRON, ArmorItem.Type.LEGGINGS, 30);
    public static final DeferredItem<VestigeArmorItem> WICKIRON_GREAVES = piece("wickiron_greaves", WICKIRON, ArmorItem.Type.BOOTS, 30);

    /** Client renderer factory, installed by the client (keeps client classes off the server). */
    public static volatile Function<String, GeoRenderProvider> rendererFactory = s -> GeoRenderProvider.DEFAULT;

    private EquipmentArmor() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name, int enchant, Holder<net.minecraft.sounds.SoundEvent> sound) {
        return MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type t : ArmorItem.Type.values()) {
                defense.put(t, 0); // VESTIGE stats live in data (vestige/armor)
            }
            return new ArmorMaterial(defense, enchant, sound, () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(VestigeIds.id(name))), 0f, 0f);
        });
    }

    private static DeferredItem<VestigeArmorItem> piece(String name, DeferredHolder<ArmorMaterial, ArmorMaterial> material, ArmorItem.Type type,
                                                        int durability) {
        String set = material.getId().getPath();
        DeferredItem<VestigeArmorItem> item = EquipmentItems.ITEMS.register(name,
                () -> new VestigeArmorItem(material, type, new Item.Properties().durability(type.getDurability(durability)), set));
        ALL.add(item);
        return item;
    }

    static GeoRenderProvider renderProvider(String set) {
        return rendererFactory.apply(set);
    }

    static void init(IEventBus modBus) {
        MATERIALS.register(modBus);
    }
}
