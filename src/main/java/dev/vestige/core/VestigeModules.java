package dev.vestige.core;

import dev.vestige.datagen.DataGenModule;
import dev.vestige.dev.DevModule;
import dev.vestige.pale.PaleModule;
import java.util.List;

/**
 * The ordered list of common modules. New systems are added here as they are implemented
 * (combat, equipment, ... per the milestone plan in docs/GDD.md §30).
 */
public final class VestigeModules {
    public static final List<VestigeModule> COMMON = List.of(
            new PaleModule(),
            new DataGenModule(),
            new DevModule());

    private VestigeModules() {}

    public static List<String> ids() {
        return COMMON.stream().map(VestigeModule::id).toList();
    }
}
