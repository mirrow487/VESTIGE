package dev.vestige.combat.net;

/**
 * Receives server→client combat messages. The client module installs the real implementation
 * at startup; on a dedicated server the no-op default is never called. This keeps common code
 * free of client classes (ArchitectureRulesTest).
 */
public interface CombatClientSink {
    CombatClientSink NONE = new CombatClientSink() {};

    default void actionStart(CombatPayloads.ActionStart p) {}

    default void actionEnd(CombatPayloads.ActionEnd p) {}

    default void actionHold(CombatPayloads.ActionHold p) {}

    default void hitFeedback(CombatPayloads.HitFeedback p) {}

    default void event(CombatPayloads.Event p) {}
}
