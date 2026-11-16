package dev.vestige.waylight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds a Waylight's name (given by worldgen, an anvil-named item, or its biome when first lit). */
public class WaylightBlockEntity extends BlockEntity {
    private String name = "";

    public WaylightBlockEntity(BlockPos pos, BlockState state) {
        super(WaylightModule.WAYLIGHT_BE.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", name);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("name");
    }
}
