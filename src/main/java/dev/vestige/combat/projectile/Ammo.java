package dev.vestige.combat.projectile;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Arrows for VESTIGE bows: offhand first, then the inventory; creative players need none. */
public final class Ammo {
    private Ammo() {}

    public static boolean has(Player player) {
        return player.getAbilities().instabuild || !find(player).isEmpty();
    }

    /** The stack an arrow would be taken from, or empty. */
    public static ItemStack find(Player player) {
        if (player.getOffhandItem().getItem() instanceof ArrowItem) {
            return player.getOffhandItem();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof ArrowItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Takes one arrow (creative: a free plain arrow). Returns the single arrow to fire, or empty if
     * there is none.
     */
    public static ItemStack take(Player player) {
        if (player.getAbilities().instabuild) {
            ItemStack s = find(player);
            return s.isEmpty() ? new ItemStack(Items.ARROW) : s.copyWithCount(1);
        }
        ItemStack s = find(player);
        return s.isEmpty() ? ItemStack.EMPTY : s.split(1);
    }
}
