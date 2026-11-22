package com.mechaniciancombat;

/** Converts a bounded movement intent into a horizontal unit vector. */
public final class RollDirection {
    public record Vector(double x, double z) { }
    public static Vector fromInput(int forward, int left, float yaw) {
        int f = Integer.compare(forward, 0);
        int l = Integer.compare(left, 0);
        if (f == 0 && l == 0) f = 1;
        double angle = Math.toRadians(Float.isFinite(yaw) ? yaw : 0.0F);
        double length = Math.hypot(f, l);
        return new Vector((l * Math.cos(angle) - f * Math.sin(angle)) / length,
                (f * Math.cos(angle) + l * Math.sin(angle)) / length);
    }
    private RollDirection() { }
}
