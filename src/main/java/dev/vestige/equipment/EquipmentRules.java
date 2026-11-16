package dev.vestige.equipment;

import dev.vestige.weapon.ShieldProfiles;
import dev.vestige.weapon.WeaponProfiles;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What may go into which RPG slot (GDD §9.1): kind, locks, no duplicates, accessory families. */
public final class EquipmentRules {
    public enum Refusal { NONE, WRONG_KIND, LOCKED, DUPLICATE, FAMILY_CONFLICT }

    private EquipmentRules() {}

    public static boolean unlocked(Player player, EquipSlot slot) {
        return slot.unlockedByDefault();
    }

    public static Refusal check(Player player, RpgInventory inv, EquipSlot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return Refusal.NONE;
        }
        if (!unlocked(player, slot)) {
            return Refusal.LOCKED;
        }
        boolean client = player.level().isClientSide();
        boolean fits = switch (slot.kind) {
            case WEAPON -> WeaponProfiles.of(stack, client).isPresent();
            case SECONDARY -> WeaponProfiles.of(stack, client).isPresent() || ShieldProfiles.of(stack, client).isPresent();
            default -> EquipmentData.accessory(stack, client).map(p -> p.kind() == slot.kind).orElse(false);
        };
        if (!fits) {
            return Refusal.WRONG_KIND;
        }
        if (slot.kind == EquipSlot.Kind.RING || slot.kind == EquipSlot.Kind.TALISMAN || slot.kind == EquipSlot.Kind.RELIC) {
            Optional<String> family = EquipmentData.accessory(stack, client).flatMap(AccessoryProfile::family);
            for (EquipSlot other : EquipSlot.values()) {
                if (other == slot || !isAccessory(other)) {
                    continue;
                }
                ItemStack o = inv.get(other);
                if (o.isEmpty()) {
                    continue;
                }
                if (o.getItem() == stack.getItem()) {
                    return Refusal.DUPLICATE;
                }
                if (family.isPresent() && EquipmentData.accessory(o, client).flatMap(AccessoryProfile::family).equals(family)) {
                    return Refusal.FAMILY_CONFLICT;
                }
            }
        }
        return Refusal.NONE;
    }

    static boolean isAccessory(EquipSlot s) {
        return s.kind == EquipSlot.Kind.RING || s.kind == EquipSlot.Kind.TALISMAN || s.kind == EquipSlot.Kind.RELIC;
    }
}
