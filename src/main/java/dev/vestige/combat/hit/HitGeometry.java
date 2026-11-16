package dev.vestige.combat.hit;

/**
 * Pure 2.5D hit tests in the attacker's local frame (no game classes; unit-tested).
 *
 * <p>Local frame: origin at the attacker's feet, +forward along its facing, +right to its right,
 * +y up. A target is an axis-aligned box described by its centre offset (already rotated into the
 * local frame), its horizontal half-extent and its vertical span.
 */
public final class HitGeometry {
    private HitGeometry() {}

    /** A target box in the attacker's local frame. */
    public record Target(double forward, double right, double halfWidth, double yMin, double yMax) {
        double distance() {
            return Math.sqrt(forward * forward + right * right);
        }

        /** Angle of the box centre, degrees, 0 = ahead, positive = right. */
        double angleDeg() {
            return Math.toDegrees(Math.atan2(right, forward));
        }
    }

    static boolean heightOverlaps(Target t, double yMin, double yMax) {
        return t.yMax() >= yMin && t.yMin() <= yMax;
    }

    /**
     * Arc sweep: does the blade, sweeping horizontally from {@code fromDeg} to {@code toDeg}, pass
     * over the target within {@code reach}? The box's angular half-size is added so wide targets
     * are hit at their edges.
     */
    public static boolean arc(Target t, double reach, double fromDeg, double toDeg, double yMin, double yMax) {
        if (!heightOverlaps(t, yMin, yMax)) {
            return false;
        }
        double d = t.distance();
        if (d - t.halfWidth() > reach) {
            return false;
        }
        if (d <= t.halfWidth()) {
            return true; // attacker is inside the box
        }
        double halfAngle = Math.toDegrees(Math.asin(Math.min(1.0, t.halfWidth() / d)));
        double lo = Math.min(fromDeg, toDeg) - halfAngle;
        double hi = Math.max(fromDeg, toDeg) + halfAngle;
        double a = t.angleDeg();
        return a >= lo && a <= hi;
    }

    /** Thrust: capsule from the attacker forward {@code length} blocks with {@code radius}. */
    public static boolean thrust(Target t, double length, double radius, double yMin, double yMax) {
        if (!heightOverlaps(t, yMin, yMax)) {
            return false;
        }
        double f = Math.max(0.0, Math.min(length, t.forward()));
        double df = t.forward() - f;
        double dr = t.right();
        double dist = Math.sqrt(df * df + dr * dr);
        return dist <= radius + t.halfWidth();
    }

    /** Disc of {@code radius} centred {@code offset} blocks ahead. */
    public static boolean circle(Target t, double radius, double offset, double yMin, double yMax) {
        if (!heightOverlaps(t, yMin, yMax)) {
            return false;
        }
        double df = t.forward() - offset;
        double dist = Math.sqrt(df * df + t.right() * t.right());
        return dist <= radius + t.halfWidth();
    }

    /** Portion of an arc swept during active tick {@code index} of {@code count} (from, to in degrees). */
    public static double[] arcSlice(double fromDeg, double toDeg, int index, int count) {
        if (count <= 0) {
            return new double[] {fromDeg, toDeg};
        }
        double step = (toDeg - fromDeg) / count;
        return new double[] {fromDeg + step * index, fromDeg + step * (index + 1)};
    }

    /** World offset (dx, dz) to local (forward, right) for a facing yaw in Minecraft degrees. */
    public static double[] toLocal(double dx, double dz, double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        // Minecraft: facing vector = (-sin(yaw), cos(yaw)); right vector = (-cos(yaw), -sin(yaw)).
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        return new double[] {dx * fx + dz * fz, dx * rx + dz * rz};
    }
}
