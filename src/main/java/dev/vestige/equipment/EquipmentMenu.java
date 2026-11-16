package dev.vestige.equipment;

import dev.vestige.VestigeIds;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The equipment screen's menu (GDD §9.5): the RPG slots (Main, Secondary, Rings, Talismans, Relics),
 * the four armor slots (vanilla's, so armor renders natively) and the player inventory. Server-
 * authoritative like any container; placement follows {@link EquipmentRules}.
 */
public class EquipmentMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, VestigeIds.MOD_ID);
    public static final Supplier<MenuType<EquipmentMenu>> TYPE = MENUS.register("equipment",
            () -> new MenuType<>(EquipmentMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** Screen layout (shared with the client screen). Tile = 20 px, slot = tile + 2. */
    public static final int WIDTH = 384, HEIGHT = 224;
    public static final int[][] RPG_POS = {
            {170, 67}, {74, 67},             // MAIN (right of the doll), SECONDARY (left): large tiles
            {214, 26}, {236, 26},            // rings
            {214, 60}, {236, 60},            // talismans
            {214, 94}, {236, 94}, {214, 116}, {236, 116} // relics (2x2)
    };
    public static final int[][] ARMOR_POS = {{12, 26}, {12, 48}, {12, 70}, {12, 92}};
    public static final int INV_X = 111, INV_Y = 144, HOTBAR_Y = 202;
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final int RPG_START = 0, ARMOR_START = RpgInventory.SIZE, INV_START = ARMOR_START + 4, INV_END = INV_START + 36;

    private final Player player;

    public EquipmentMenu(int id, Inventory playerInv) {
        super(TYPE.get(), id);
        this.player = playerInv.player;
        Container rpg = new RpgContainer(player);
        for (EquipSlot s : EquipSlot.values()) {
            int[] p = RPG_POS[s.ordinal()];
            addSlot(new RpgSlot(rpg, s, p[0] + 2, p[1] + 2));
        }
        for (int i = 0; i < 4; i++) {
            EquipmentSlot es = ARMOR_SLOTS[i];
            int[] p = ARMOR_POS[i];
            addSlot(new Slot(playerInv, 39 - i, p[0] + 2, p[1] + 2) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.canEquip(es, player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public Player player() {
        return player;
    }

    @Override
    public boolean stillValid(Player p) {
        return p == player && p.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < INV_START && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        EquipmentStats.invalidate(p);
    }

    /** One RPG slot: only what {@link EquipmentRules} allows, one item. */
    public static class RpgSlot extends Slot {
        public final EquipSlot equipSlot;
        private final Player player;

        RpgSlot(Container container, EquipSlot slot, int x, int y) {
            super(container, slot.ordinal(), x, y);
            this.equipSlot = slot;
            this.player = ((RpgContainer) container).player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return EquipmentRules.check(player, player.getData(EquipmentModule.RPG_INVENTORY), equipSlot, stack) == EquipmentRules.Refusal.NONE;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** A Container view of the player's {@link RpgInventory}; changes sync the attachment. */
    static class RpgContainer implements Container {
        final Player player;

        RpgContainer(Player player) {
            this.player = player;
        }

        private RpgInventory inv() {
            return player.getData(EquipmentModule.RPG_INVENTORY);
        }

        @Override
        public int getContainerSize() {
            return RpgInventory.SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (EquipSlot s : EquipSlot.values()) {
                if (!inv().get(s).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return inv().get(EquipSlot.values()[i]);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack s = getItem(i);
            if (s.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack out = s.split(count);
            if (s.isEmpty()) {
                inv().set(EquipSlot.values()[i], ItemStack.EMPTY);
            }
            setChanged();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = getItem(i);
            inv().set(EquipSlot.values()[i], ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            inv().set(EquipSlot.values()[i], stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            EquipmentStats.invalidate(player);
            if (player instanceof ServerPlayer sp) {
                sp.syncData(EquipmentModule.RPG_INVENTORY);
                EquipmentModule.applyDerived(sp);
            }
        }

        @Override
        public boolean stillValid(Player p) {
            return p == player;
        }

        @Override
        public void clearContent() {
            for (EquipSlot s : EquipSlot.values()) {
                inv().set(s, ItemStack.EMPTY);
            }
        }
    }
}
