package dev.vestige.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The mod-owned equipment inventory (GDD §9.1): Main, Secondary, Rings, Talismans and Relics, plus
 * the selected (active) relic and the combat-stance stash. Saved, always kept on death, synced to
 * the owner.
 */
public final class RpgInventory {
    public static final int SIZE = EquipSlot.values().length;
    public static final Codec<RpgInventory> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("slots").forGetter(r -> r.slots),
            Codec.INT.optionalFieldOf("active_relic", 0).forGetter(r -> r.activeRelic),
            Codec.BOOL.optionalFieldOf("stance", false).forGetter(r -> r.stance),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("stash_main", ItemStack.EMPTY).forGetter(r -> r.stashMain),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("stash_off", ItemStack.EMPTY).forGetter(r -> r.stashOff),
            Codec.INT.optionalFieldOf("stance_slot", 0).forGetter(r -> r.stanceSlot)
    ).apply(i, RpgInventory::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RpgInventory> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SIZE)), r -> r.slots,
            ByteBufCodecs.VAR_INT, r -> r.activeRelic,
            ByteBufCodecs.BOOL, r -> r.stance,
            ItemStack.OPTIONAL_STREAM_CODEC, r -> r.stashMain,
            ItemStack.OPTIONAL_STREAM_CODEC, r -> r.stashOff,
            ByteBufCodecs.VAR_INT, r -> r.stanceSlot,
            RpgInventory::new);

    private final List<ItemStack> slots;
    int activeRelic;
    boolean stance;
    /** In combat stance: what the hotbar slot and the off-hand held before the swap. */
    ItemStack stashMain = ItemStack.EMPTY;
    ItemStack stashOff = ItemStack.EMPTY;
    int stanceSlot;

    public RpgInventory() {
        slots = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            slots.add(ItemStack.EMPTY);
        }
    }

    private RpgInventory(List<ItemStack> list, int activeRelic, boolean stance, ItemStack stashMain, ItemStack stashOff, int stanceSlot) {
        this();
        for (int i = 0; i < Math.min(SIZE, list.size()); i++) {
            slots.set(i, list.get(i));
        }
        this.activeRelic = activeRelic;
        this.stance = stance;
        this.stashMain = stashMain;
        this.stashOff = stashOff;
        this.stanceSlot = stanceSlot;
    }

    public ItemStack get(EquipSlot slot) {
        return slots.get(slot.ordinal());
    }

    void set(EquipSlot slot, ItemStack stack) {
        slots.set(slot.ordinal(), stack);
    }

    public int activeRelic() {
        return activeRelic;
    }

    public boolean inStance() {
        return stance;
    }

    /** The relic shown on the Readiness Cross (the selected one, or the first equipped). */
    public ItemStack activeRelicStack() {
        EquipSlot[] relics = {EquipSlot.RELIC_1, EquipSlot.RELIC_2, EquipSlot.RELIC_3, EquipSlot.RELIC_4};
        ItemStack s = get(relics[Math.floorMod(activeRelic, 4)]);
        if (!s.isEmpty()) {
            return s;
        }
        for (EquipSlot r : relics) {
            if (!get(r).isEmpty()) {
                return get(r);
            }
        }
        return ItemStack.EMPTY;
    }

    /** The main weapon as the player wields it: in stance it lives in the hand, the slot remembers it. */
    public ItemStack mainWeapon() {
        return get(EquipSlot.MAIN);
    }
}
