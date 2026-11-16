package dev.vestige.stats;

/** Status buildups (GDD §7.10): a meter fills to 100 and the status triggers. */
public enum StatusType implements net.minecraft.util.StringRepresentable {
    /** Burst of 12% max HP + 5% of current health (Slash builds it). */
    REND(0xFFB03030),
    /** 6 s burn of 1.5% max HP per second; healing ×0.7 while it lasts (Cinder builds it). */
    SCORCH(0xFFE07030),
    /** 10 s: players gain no Resonance and cannot use arts; enemies stop recovering poise and take +10% damage (Hush builds it). */
    HUSHED(0xFF5FA8A8),
    /** Max HP −20% until the victim rests (players) or for good (enemies) (Void builds it). */
    VOIDBITE(0xFF9A6AD0);

    public static final com.mojang.serialization.Codec<StatusType> CODEC = net.minecraft.util.StringRepresentable.fromEnum(StatusType::values);
    public final int color;

    StatusType(int color) {
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
