package dev.vestige.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which Ember-Ghost is each player's current one (GDD §8.5). A newer death replaces the older ghost:
 * a ghost that loads and is not its owner's current one fades away, so "die again first and it's
 * gone" holds even for ghosts in unloaded chunks.
 */
public final class EmberGhosts extends SavedData {
    private static final String NAME = "vestige_ember_ghosts";
    private final Map<UUID, UUID> current = new HashMap<>();

    public static EmberGhosts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(EmberGhosts::new, EmberGhosts::load, null), NAME);
    }

    private static EmberGhosts load(CompoundTag tag, HolderLookup.Provider provider) {
        EmberGhosts g = new EmberGhosts();
        CompoundTag map = tag.getCompound("ghosts");
        for (String key : map.getAllKeys()) {
            g.current.put(UUID.fromString(key), map.getUUID(key));
        }
        return g;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag map = new CompoundTag();
        current.forEach((owner, ghost) -> map.putUUID(owner.toString(), ghost));
        tag.put("ghosts", map);
        return tag;
    }

    /** Records a new ghost; returns the previous one (to remove if loaded), or null. */
    public UUID replace(UUID owner, UUID ghost) {
        UUID old = current.put(owner, ghost);
        setDirty();
        return old;
    }

    public boolean isCurrent(UUID owner, UUID ghost) {
        return ghost.equals(current.get(owner));
    }

    public void clear(UUID owner, UUID ghost) {
        if (current.remove(owner, ghost)) {
            setDirty();
        }
    }
}
