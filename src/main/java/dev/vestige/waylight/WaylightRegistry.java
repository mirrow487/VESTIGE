package dev.vestige.waylight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Every Waylight in the world, by position, with its name (ARCHITECTURE §5.2). Kept on the Overworld. */
public final class WaylightRegistry extends SavedData {
    private static final String NAME = "vestige_waylights";

    public record Entry(GlobalPos pos, String name) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)
        ).apply(i, Entry::new));
    }

    private final Map<GlobalPos, Entry> entries = new LinkedHashMap<>();

    public static WaylightRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WaylightRegistry::new, WaylightRegistry::load, null), NAME);
    }

    private static WaylightRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        WaylightRegistry r = new WaylightRegistry();
        Entry.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("waylights")).result()
                .ifPresent(list -> list.forEach(e -> r.entries.put(e.pos(), e)));
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        Entry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, new ArrayList<>(entries.values())).result().ifPresent(t -> tag.put("waylights", t));
        return tag;
    }

    public void put(GlobalPos pos, String name) {
        entries.put(pos, new Entry(pos, name));
        setDirty();
    }

    public void remove(GlobalPos pos) {
        if (entries.remove(pos) != null) {
            setDirty();
        }
    }

    public Optional<Entry> get(GlobalPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public List<Entry> all() {
        return List.copyOf(entries.values());
    }
}
