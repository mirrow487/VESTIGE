package dev.vestige.waylight;

import com.mojang.serialization.Codec;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.GlobalPos;

/** The Waylights a player has lit (GDD §26: per-player). Saved and kept across death. */
public final class WaylightDiscovery {
    public static final Codec<WaylightDiscovery> CODEC = GlobalPos.CODEC.listOf().xmap(WaylightDiscovery::new, d -> List.copyOf(d.lit));

    private final Set<GlobalPos> lit = new LinkedHashSet<>();

    public WaylightDiscovery() {}

    private WaylightDiscovery(List<GlobalPos> list) {
        lit.addAll(list);
    }

    public boolean has(GlobalPos pos) {
        return lit.contains(pos);
    }

    /** Returns true if this was a new discovery. */
    boolean add(GlobalPos pos) {
        return lit.add(pos);
    }

    void remove(GlobalPos pos) {
        lit.remove(pos);
    }

    public Set<GlobalPos> all() {
        return Set.copyOf(lit);
    }
}
