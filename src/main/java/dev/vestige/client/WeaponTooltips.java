package dev.vestige.client;

import dev.vestige.weapon.WeaponProfiles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows VESTIGE weapon stats (class, attack rating, weight) on any item with a weapon profile. */
public final class WeaponTooltips {
    private WeaponTooltips() {}

    public static void onTooltip(ItemTooltipEvent event) {
        WeaponProfiles.of(event.getItemStack(), true).ifPresent(w -> {
            event.getToolTip().add(Component.translatable("tooltip.vestige.weapon_class." + w.weaponClass()).withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.vestige.weapon_stats",
                    String.format("%.0f", w.attackRating()), String.format("%.1f", w.weight())).withStyle(ChatFormatting.GRAY));
        });
    }
}
