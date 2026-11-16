package dev.vestige.equipment;

/** The RPG equipment slots (GDD §9.1). Armor uses the vanilla armor slots and is not listed here. */
public enum EquipSlot {
    MAIN(Kind.WEAPON), SECONDARY(Kind.SECONDARY), RING_1(Kind.RING), RING_2(Kind.RING), TALISMAN_1(Kind.TALISMAN),
    TALISMAN_2(Kind.TALISMAN), RELIC_1(Kind.RELIC), RELIC_2(Kind.RELIC), RELIC_3(Kind.RELIC), RELIC_4(Kind.RELIC);

    public enum Kind { WEAPON, SECONDARY, RING, TALISMAN, RELIC }

    public final Kind kind;

    EquipSlot(Kind kind) {
        this.kind = kind;
    }

    /**
     * Slots unlocked from the start (GDD §9.1): the 2nd talisman opens after the Tithe-Engine, the 3rd
     * relic after Mother Tallow and the 4th after the Resounding. Their bosses arrive in later
     * milestones, so for now those slots stay chained.
     */
    public boolean unlockedByDefault() {
        return this != TALISMAN_2 && this != RELIC_3 && this != RELIC_4;
    }

    public String unlockHint() {
        return switch (this) {
            case TALISMAN_2 -> "Unlocks after the Tithe-Engine falls";
            case RELIC_3 -> "Unlocks after Mother Tallow falls";
            case RELIC_4 -> "Unlocks after the Resounding";
            default -> "";
        };
    }
}
