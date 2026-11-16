package dev.vestige.equipment;

import net.minecraft.server.level.ServerPlayer;

/** Opens the equipment screen (the menu itself is registered by {@code EquipmentMenu}). */
public final class EquipmentMenus {
    private EquipmentMenus() {}

    public static void open(ServerPlayer player) {
        Stance.exit(player);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new EquipmentMenu(id, inv), net.minecraft.network.chat.Component.translatable("screen.vestige.equipment")));
    }
}
