package dev.vestige.entity.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * At most {@link #MAX_ATTACKERS} enemies may attack the same target at once (GDD §7.13); the rest
 * circle and wait. Tokens expire so a dead or stuck attacker never blocks the others.
 */
public final class AttackTokens {
    public static final int MAX_ATTACKERS = 2;
    private static final int LEASE_TICKS = 80;
    private static final Map<UUID, Map<Integer, Long>> HELD = new HashMap<>();

    private AttackTokens() {}

    public static synchronized boolean acquire(UUID target, int attackerId, long now) {
        Map<Integer, Long> holders = HELD.computeIfAbsent(target, k -> new HashMap<>());
        for (Iterator<Map.Entry<Integer, Long>> it = holders.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
        if (holders.containsKey(attackerId) || holders.size() < MAX_ATTACKERS) {
            holders.put(attackerId, now + LEASE_TICKS);
            return true;
        }
        return false;
    }

    public static synchronized void release(UUID target, int attackerId) {
        Map<Integer, Long> holders = HELD.get(target);
        if (holders != null) {
            holders.remove(attackerId);
            if (holders.isEmpty()) {
                HELD.remove(target);
            }
        }
    }

    public static synchronized void clear() {
        HELD.clear();
    }
}
