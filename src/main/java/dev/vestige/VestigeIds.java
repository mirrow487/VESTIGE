package dev.vestige;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** Mod-wide identifiers and logger. Kept free of game-state so any module may depend on it. */
public final class VestigeIds {
    public static final String MOD_ID = "vestige";
    public static final Logger LOG = LogUtils.getLogger();

    private VestigeIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
