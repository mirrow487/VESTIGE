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
        add("key.vestige.flask", "Wick Flask");
        add("item.vestige.wick_flask", "Wick Flask");
        add("item.vestige.flask_wick", "Flask Wick");
        add("block.vestige.waylight", "Waylight");
        add("entity.vestige.ember_ghost", "Ember-Ghost");
        add("entity.vestige.ember_ghost.named", "%s's Ember-Ghost (%s Glim)");
        add("message.vestige.flask_full", "Your Wick Flask can hold no more wicks");
        add("message.vestige.flask_wick", "The Wick Flask now holds %s charges");
        add("message.vestige.glim_reclaimed", "Reclaimed %s Glim");
        add("message.vestige.waylight_rested", "You rest by the Waylight. Your flask is full.");
        add("message.vestige.waylight_refused.in_combat", "You cannot rest or travel in the thick of combat");
        add("message.vestige.waylight_refused.hostiles_near", "Enemies are too close");
        add("message.vestige.waylight_refused.too_far", "Too far from the Waylight");
        add("message.vestige.waylight_refused.not_lit", "This Waylight is not lit");
        add("message.vestige.waylight_refused.unknown_destination", "You have not lit that Waylight");
        add("message.vestige.waylight_refused.same_place", "You are already here");
        add("message.vestige.waylight_refused.none", "");
        add("title.vestige.waylight_lit", "WAYLIGHT KINDLED");
        add("gui.vestige.waylight.rest", "Rest");
        add("gui.vestige.waylight.travel", "Travel");
        add("gui.vestige.waylight.leave", "Leave");
        add("gui.vestige.waylight.back", "Back");
        add("gui.vestige.waylight.choose", "Choose a lit Waylight");
        add("gui.vestige.waylight.no_destinations", "No other Waylight has been lit");
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
