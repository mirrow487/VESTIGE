package dev.vestige.core;

import dev.vestige.VestigeIds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.bus.api.IEventBus;

/** Initializes modules in declaration order and rejects duplicate ids. */
public final class ModuleLoader {
    private ModuleLoader() {}

    public static void initCommon(List<? extends VestigeModule> modules, IEventBus modBus, IEventBus gameBus) {
        Set<String> seen = new HashSet<>();
        for (VestigeModule module : modules) {
            if (!seen.add(module.id())) {
                throw new IllegalStateException("Duplicate VESTIGE module id: " + module.id());
            }
            module.init(modBus, gameBus);
            VestigeIds.LOG.info("VESTIGE module initialized: {}", module.id());
        }
    }
}
