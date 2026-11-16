package dev.vestige.datagen;

import dev.vestige.VestigeIds;
import dev.vestige.config.ServerConfig;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us translations. Each module adds its keys here as it gains user-facing text. */
public final class EnglishLanguageProvider extends LanguageProvider {
    public EnglishLanguageProvider(PackOutput output) {
        super(output, VestigeIds.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("vestige.configuration.title", "VESTIGE Configuration");
        add("vestige.configuration.section.vestige.server.toml", "World Settings");
        add("vestige.configuration.section.vestige.server.toml.title", "VESTIGE World Settings");
        add("itemGroup.vestige", "VESTIGE");
        add("item.vestige.keepsake_blade", "Keepsake Blade");
        add("item.vestige.ash_pike", "Ash Pike");
        add("item.vestige.hand_maul", "Hand Maul");
        add("item.vestige.hollowed_spawn_egg", "Hollowed Spawn Egg");
        add("item.vestige.hollowed_brute_spawn_egg", "Hollowed Brute Spawn Egg");
        add("entity.vestige.hollowed", "Hollowed");
        add("entity.vestige.hollowed_brute", "Hollowed Brute");
        add("key.categories.vestige", "VESTIGE Combat");
        add("key.vestige.heavy", "Heavy Attack (hold to charge)");
        add("key.vestige.dodge", "Dodge Roll");
        add("key.vestige.lock_on", "Lock On");
        add("key.vestige.art", "Weapon Art");
        add("item.vestige.plank_buckler", "Plank Buckler");
        add("item.vestige.hunting_bow", "Hunting Bow");
        add("item.vestige.iron_kite_shield", "Iron Kite Shield");
        add("item.vestige.timber_bulwark", "Timber Bulwark");
        add("item.vestige.taper_fang", "Taper Fang");
        add("item.vestige.wickiron_greatblade", "Wickiron Greatblade");
        add("item.vestige.chandlers_censer", "Chandler's Censer");
        add("item.vestige.handbell", "Handbell");
        add("entity.vestige.combat_arrow", "Arrow");
        add("entity.vestige.tolling_ram", "Tolling Ram");
        add("item.vestige.tolling_ram_spawn_egg", "Tolling Ram Spawn Egg");
        add("entity.vestige.incense_cloud", "Incense Cloud");
        add("tooltip.vestige.weapon_class.blade", "Blade");
        add("tooltip.vestige.weapon_class.pike", "Pike");
        add("tooltip.vestige.weapon_class.maul", "Maul");
        add("tooltip.vestige.weapon_class.fang", "Fang");
        add("tooltip.vestige.weapon_class.greatblade", "Greatblade");
        add("tooltip.vestige.weapon_class.censer", "Censer");
        add("tooltip.vestige.weapon_class.longbow", "Longbow");
        add("tooltip.vestige.weapon_class.bell", "Bell");
        add("tooltip.vestige.weapon_stats", "Attack %s · Weight %s");
        add("death.attack.vestige.weapon", "%1$s was cut down by %2$s");
        add("death.attack.vestige.weapon.item", "%1$s was cut down by %2$s using %3$s");
        add("death.attack.vestige.enemy", "%1$s was torn apart by %2$s");
        add("death.attack.vestige.enemy.item", "%1$s was torn apart by %2$s using %3$s");
        for (ServerConfig.Entry entry : ServerConfig.ENTRIES) {
            add(entry.key(), entry.name());
            add(entry.key() + ".tooltip", entry.tooltip());
        }
    }
}
