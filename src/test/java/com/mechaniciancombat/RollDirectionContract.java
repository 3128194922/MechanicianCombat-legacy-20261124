package com.mechaniciancombat;

public final class RollDirectionContract {
    public static void main(String[] args) {
        check(0, 0, 0, 0, 1, "no input defaults forward");
        check(1, 0, 0, 0, 1, "W");
        check(-1, 0, 0, 0, -1, "S");
        check(0, 1, 0, 1, 0, "A");
        check(0, -1, 0, -1, 0, "D");
        check(1, 1, 0, Math.sqrt(0.5), Math.sqrt(0.5), "W+A normalized");
        check(1, -1, 0, -Math.sqrt(0.5), Math.sqrt(0.5), "W+D normalized");
        check(-1, 1, 0, Math.sqrt(0.5), -Math.sqrt(0.5), "S+A normalized");
        check(-1, -1, 0, -Math.sqrt(0.5), -Math.sqrt(0.5), "S+D normalized");
        check(0, 0, 90, -1, 0, "default forward respects facing");
        check(0, 1, 90, 0, 1, "A respects facing");
        System.out.println("RollDirectionContract: eleven direction scenarios passed");
    }
    private static void check(int forward, int left, float yaw, double x, double z, String label) {
        var v = RollDirection.fromInput(forward, left, yaw);
        if (Math.abs(v.x() - x) > 1e-6 || Math.abs(v.z() - z) > 1e-6) {
            throw new AssertionError(label + ": " + v);
        }
    }
}
