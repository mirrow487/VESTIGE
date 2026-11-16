package dev.vestige.core;

import net.neoforged.bus.api.IEventBus;

/**
 * A self-contained gameplay system (combat, equipment, bosses, NPCs, world generation, ...).
 *
 * <p>Each module owns its registrations (its own {@code DeferredRegister}s), event listeners and
 * state. Modules talk to each other only through public service classes, never through each
 * other's internals. Dependency rules are enforced by {@code ArchitectureRulesTest}.
 */
public interface VestigeModule {
    /** Stable, lowercase identifier, equal to the module's package name under {@code dev.vestige}. */
    String id();

    /**
     * Registers {@code DeferredRegister}s and listeners.
     *
     * @param modBus  the mod event bus (registration, setup, config events)
     * @param gameBus the NeoForge game event bus (ticks, server lifecycle, entity events)
     */
    void init(IEventBus modBus, IEventBus gameBus);
}
