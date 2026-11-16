package dev.vestige.client.equipment;

import dev.vestige.combat.player.PlayerAttack;
import dev.vestige.equipment.AccessoryProfile;
import dev.vestige.equipment.ArmorProfile;
import dev.vestige.equipment.EquipSlot;
import dev.vestige.equipment.EquipmentData;
import dev.vestige.equipment.EquipmentModule;
import dev.vestige.stats.Attribute;
import dev.vestige.stats.AttributeRules;
import dev.vestige.stats.DamageSpread;
import dev.vestige.stats.DamageType;
import dev.vestige.stats.Rarity;
import dev.vestige.stats.Stats;
import dev.vestige.weapon.ShieldProfiles;
import dev.vestige.weapon.WeaponProfile;
import dev.vestige.weapon.WeaponProfiles;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Item tooltips (GDD §9.5): rarity-coloured name, stats per damage type, scaling grades,
 * requirements (red when unmet), weight, set bonuses, and green/red deltas against what is equipped.
 */
public final class EquipmentTooltips {
    private static final int PARCH = 0xD9CFB8, MUTED = 0x8C8475, EMBER = 0xE08A3C, GOOD = 0x7FBF6A, BAD = 0xD86A5A;

    private EquipmentTooltips() {}

    static MutableComponent text(String s, int color) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    static String name(DamageType t) {
        String n = t.getSerializedName();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static void rarityName(List<Component> lines, Rarity rarity) {
        if (!lines.isEmpty()) {
            lines.set(0, lines.get(0).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rarity.color & 0xFFFFFF))));
        }
    }

    private static String delta(float d, boolean percent) {
        if (Math.abs(d) < 0.005f) {
            return "";
        }
        return percent ? String.format(Locale.ROOT, " (%+d%%)", Math.round(d * 100)) : String.format(Locale.ROOT, " (%+.0f)", d);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        if (stack.getItem() instanceof dev.vestige.equipment.VestigeArmorItem) {
            // VESTIGE armor has no vanilla armor points: drop vanilla's empty "When on Body:" block.
            lines.removeIf(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().startsWith("item.modifiers."));
            lines.removeIf(c -> c.getString().isEmpty());
        }
        var weapon = WeaponProfiles.of(stack, true);
        if (weapon.isPresent()) {
            weapon(lines, player, stack, weapon.get());
            return;
        }
        var armor = EquipmentData.armor(stack, true);
        if (armor.isPresent()) {
            armor(lines, player, stack, armor.get());
            return;
        }
        var acc = EquipmentData.accessory(stack, true);
        if (acc.isPresent()) {
            accessory(lines, acc.get());
            return;
        }
        ShieldProfiles.of(stack, true).ifPresent(s -> {
            lines.add(text("Shield · " + s.kind().name().charAt(0) + s.kind().name().substring(1).toLowerCase(Locale.ROOT), EMBER));
            lines.add(text(String.format(Locale.ROOT, "Guard absorption %d%% · Stability %.0f · Weight %.1f", Math.round(s.absorption() * 100),
                    s.stability(), s.weight()), PARCH));
        });
    }

    private static void weapon(List<Component> lines, Player player, ItemStack stack, WeaponProfile w) {
        rarityName(lines, w.rarity());
        lines.add(Component.translatable("tooltip.vestige.weapon_class." + w.weaponClass()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(EMBER))));
        DamageSpread ar = PlayerAttack.spread(player, w, null);
        // Compare against the equipped Main weapon (or what is in hand).
        var inv = player.getData(EquipmentModule.RPG_INVENTORY);
        ItemStack equipped = inv.get(EquipSlot.MAIN).isEmpty() ? player.getMainHandItem() : inv.get(EquipSlot.MAIN);
        DamageSpread other = equipped != stack && !equipped.isEmpty() && !ItemStack.isSameItem(equipped, stack)
                ? WeaponProfiles.of(equipped, true).map(e -> PlayerAttack.spread(player, e, null)).orElse(null) : null;
        lines.add(text("Attack", MUTED));
        for (DamageType t : DamageType.all()) {
            float v = ar.get(t);
            if (v > 0 || (other != null && other.get(t) > 0)) {
                float d = other == null ? 0 : v - other.get(t);
                lines.add(text(String.format(Locale.ROOT, "  %-10s %.0f", name(t), v), t.color & 0xFFFFFF)
                        .append(text(delta(d, false), d >= 0 ? GOOD : BAD)));
            }
        }
        if (other != null) {
            float d = ar.total() - other.total();
            lines.add(text(String.format(Locale.ROOT, "  Total %.0f", ar.total()), PARCH).append(text(delta(d, false), d >= 0 ? GOOD : BAD)));
        }
        if (!w.scaling().isEmpty()) {
            StringBuilder sb = new StringBuilder("Scaling ");
            for (Attribute a : Attribute.values()) {
                var g = w.scaling().get(a);
                if (g != null) {
                    sb.append(a.abbreviation).append(' ').append(g.name()).append("  ");
                }
            }
            lines.add(text(sb.toString().trim(), PARCH));
        }
        if (!w.requirements().isEmpty()) {
            MutableComponent req = text("Requires ", MUTED);
            var attrs = player.getData(EquipmentModule.ATTRIBUTES);
            w.requirements().forEach((a, v) -> req.append(text(a.abbreviation + " " + v + "  ", attrs.get(a) >= v ? PARCH : BAD)));
            lines.add(req);
            if (!AttributeRules.requirementsMet(w.requirements(), attrs.asMap())) {
                lines.add(text("Requirements unmet: attack x0.6", BAD));
            }
        }
        if (!w.status().isEmpty()) {
            w.status().forEach((st, v) -> lines.add(text(String.format(Locale.ROOT, "%s buildup %.0f per hit",
                    st.name().charAt(0) + st.name().substring(1).toLowerCase(Locale.ROOT), v), st.color & 0xFFFFFF)));
        }
        lines.add(text(String.format(Locale.ROOT, "Weight %.1f", w.weight()), MUTED));
    }

    private static void armor(List<Component> lines, Player player, ItemStack stack, ArmorProfile a) {
        rarityName(lines, a.rarity());
        EquipmentSlot slot = switch (a.slot()) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
        ItemStack worn = player.getItemBySlot(slot);
        ArmorProfile other = worn == stack || ItemStack.isSameItem(worn, stack) ? null : EquipmentData.armor(worn, true).orElse(null);
        lines.add(text("Armor · " + Character.toUpperCase(a.slot().charAt(0)) + a.slot().substring(1), EMBER));
        lines.add(text("Absorption", MUTED));
        for (DamageType t : DamageType.all()) {
            float v = a.mods().absorption().get(t);
            float o = other == null ? v : other.mods().absorption().get(t);
            if (v > 0 || o > 0) {
                lines.add(text(String.format(Locale.ROOT, "  %-10s %d%%", name(t), Math.round(v * 100)), t.color & 0xFFFFFF)
                        .append(text(other == null ? "" : delta(v - o, true), v >= o ? GOOD : BAD)));
            }
        }
        float pd = other == null ? 0 : a.mods().poise() - other.mods().poise();
        lines.add(text(String.format(Locale.ROOT, "Poise %.0f", a.mods().poise()), PARCH).append(text(delta(pd, false), pd >= 0 ? GOOD : BAD)));
        float wd = other == null ? 0 : a.weight() - other.weight();
        lines.add(text(String.format(Locale.ROOT, "Weight %.1f", a.weight()), MUTED).append(text(delta(wd, false), wd <= 0 ? GOOD : BAD)));
        a.set().flatMap(id -> EquipmentData.set(id, true)).ifPresent(set -> {
            int worn2 = Stats.of(player).sets.getOrDefault(a.set().get(), 0);
            lines.add(text(set.name() + " (" + worn2 + "/4)", EMBER));
            lines.add(text("  2: " + set.twoText(), worn2 >= 2 ? GOOD : MUTED));
            lines.add(text("  4: " + set.fourText(), worn2 >= 4 ? GOOD : MUTED));
        });
    }

    private static void accessory(List<Component> lines, AccessoryProfile a) {
        rarityName(lines, a.rarity());
        String kind = a.kind().name().charAt(0) + a.kind().name().substring(1).toLowerCase(Locale.ROOT);
        lines.add(text(kind, EMBER));
        if (!a.description().isEmpty()) {
            lines.add(text(a.description(), PARCH));
        }
        for (String s : a.mods().describe()) {
            lines.add(text("  " + s, GOOD));
        }
        a.family().ifPresent(f -> lines.add(text("Cannot be worn with another " + f.replace('_', ' '), MUTED)));
        lines.add(text(String.format(Locale.ROOT, "Weight %.1f", a.weight()), MUTED));
        lines.add(Component.empty().withStyle(ChatFormatting.RESET));
    }
}
