package dev.vestige.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Combat stance (GDD §7.3): the Main Weapon slot's stack is physically moved into the selected
 * hotbar slot and the Secondary into the off-hand; what was there is stashed. Leaving swaps back.
 * Real stacks move (never copies), so nothing can be duplicated. Leaving is forced before death
 * drops, logout, dimension changes and opening any container.
 */
public final class Stance {
    private Stance() {}

    public static ItemStack main(Player player, RpgInventory inv) {
        return inv.stance ? player.getInventory().getItem(inv.stanceSlot) : inv.get(EquipSlot.MAIN);
    }

    public static ItemStack secondary(Player player, RpgInventory inv) {
        return inv.stance ? player.getOffhandItem() : inv.get(EquipSlot.SECONDARY);
    }

    public static boolean in(Player player) {
        return player.getData(EquipmentModule.RPG_INVENTORY).stance;
    }

    /** Enters combat stance; returns false if there is nothing to wield. */
    public static boolean enter(ServerPlayer player) {
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        if (inv.stance || (inv.get(EquipSlot.MAIN).isEmpty() && inv.get(EquipSlot.SECONDARY).isEmpty())) {
            return false;
        }
        int slot = player.getInventory().selected;
        inv.stanceSlot = slot;
        inv.stashMain = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, inv.get(EquipSlot.MAIN));
        inv.set(EquipSlot.MAIN, ItemStack.EMPTY);
        inv.stashOff = player.getOffhandItem();
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, inv.get(EquipSlot.SECONDARY));
        inv.set(EquipSlot.SECONDARY, ItemStack.EMPTY);
        inv.stance = true;
        sync(player);
        return true;
    }

    /** Leaves combat stance: whatever is in the stance hand and off-hand goes back to the slots. */
    public static void exit(ServerPlayer player) {
        RpgInventory inv = player.getData(EquipmentModule.RPG_INVENTORY);
        if (!inv.stance) {
            return;
        }
        inv.set(EquipSlot.MAIN, player.getInventory().getItem(inv.stanceSlot));
        player.getInventory().setItem(inv.stanceSlot, inv.stashMain);
        inv.set(EquipSlot.SECONDARY, player.getOffhandItem());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, inv.stashOff);
        inv.stashMain = ItemStack.EMPTY;
        inv.stashOff = ItemStack.EMPTY;
        inv.stance = false;
        sync(player);
    }

    public static void toggle(ServerPlayer player) {
        if (in(player)) {
            exit(player);
        } else {
            enter(player);
        }
    }

    static void sync(ServerPlayer player) {
        player.syncData(EquipmentModule.RPG_INVENTORY);
        player.inventoryMenu.broadcastChanges();
    }
}
