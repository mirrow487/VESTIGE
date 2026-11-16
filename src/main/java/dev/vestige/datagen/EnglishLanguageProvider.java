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
        add("item.vestige.wanderer_hood", "Wanderer's Hood");
        add("item.vestige.wanderer_coat", "Wanderer's Coat");
        add("item.vestige.wanderer_trousers", "Wanderer's Trousers");
        add("item.vestige.wanderer_boots", "Wanderer's Boots");
        add("item.vestige.bellbronze_helm", "Bellbronze Helm");
        add("item.vestige.bellbronze_mail", "Bellbronze Mail");
        add("item.vestige.bellbronze_tassets", "Bellbronze Tassets");
        add("item.vestige.bellbronze_sabatons", "Bellbronze Sabatons");
        add("item.vestige.wickiron_helm", "Wickiron Helm");
        add("item.vestige.wickiron_plate", "Wickiron Plate");
        add("item.vestige.wickiron_cuisses", "Wickiron Cuisses");
        add("item.vestige.wickiron_greaves", "Wickiron Greaves");
        add("item.vestige.ring_of_quick_breath", "Ring of Quick Breath");
        add("item.vestige.bellbronze_band", "Bellbronze Band");
        add("item.vestige.waxen_ring", "Waxen Ring");
        add("item.vestige.hollow_ring", "Hollow Ring");
        add("item.vestige.tithe_ring", "Tithe Ring");
        add("item.vestige.ring_of_the_unsung", "Ring of the Unsung");
        add("item.vestige.harts_ring", "Hart's Ring");
        add("item.vestige.ember_signet", "Ember Signet");
        add("item.vestige.lightfoot_feather", "Lightfoot Feather");
        add("item.vestige.talisman_of_the_grove", "Talisman of the Grove");
        add("item.vestige.ledger_of_debts", "Ledger of Debts");
        add("item.vestige.talisman_of_second_breath", "Talisman of Second Breath");
        add("item.vestige.bell_of_warding", "Bell of Warding");
        add("item.vestige.windsail_cape", "Windsail Cape");
        add("item.vestige.bellbronze_spurs", "Bellbronze Spurs");
        add("item.vestige.divers_bell", "Diver's Bell");
        add("item.vestige.climbers_hooks", "Climber's Hooks");
        add("item.vestige.pale_hook", "Pale Hook");
        add("item.vestige.kindling_ember", "Kindling Ember");
        add("screen.vestige.equipment", "Equipment");
        add("screen.vestige.attune", "Attunement");
        add("key.vestige.equipment", "Equipment");
        add("key.vestige.stance", "Combat Stance");
        add("key.vestige.relic_use", "Use Relic");
        add("key.vestige.relic_cycle", "Next Relic");
        add("gui.vestige.waylight.attune", "Attune");
        add("gui.vestige.waylight.kindle", "Kindle Flask");
        add("message.vestige.attuned", "Attuned: level %s (%s Glim)");
        add("message.vestige.attune_refused", "Not enough Glim");
        add("message.vestige.kindled", "The Wick Flask burns brighter: Kindling +%s");
        add("message.vestige.kindle_refused", "Kindling needs a Kindling Ember and Glim");
        add("death.attack.vestige.status", "%1$s succumbed");
        add("death.attack.vestige.status.player", "%1$s succumbed to %2$s");
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
