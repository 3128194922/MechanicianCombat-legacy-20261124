package com.mechaniciancombat.client;

public final class ShoulderFramingContract {
    public static void main(String[] args) {
        check(3.2, 70, 16.0 / 9.0);
        check(2.35, 43.4, 16.0 / 9.0);
        check(4, 70, 4.0 / 3.0);
        check(3.2, 90, 21.0 / 9.0);
        if (ShoulderFraming.sideOffset(3, Double.NaN, 1.0) != 0
                || ShoulderFraming.sideOffset(3, 70, 0) != 0) {
            throw new AssertionError("invalid projection inputs must not move the camera");
        }
        System.out.println("ShoulderFramingContract: player projects to 25% of width");
    }
    private static void check(double depth, double fov, double aspect) {
        double offset = ShoulderFraming.sideOffset(depth, fov, aspect);
        double halfWidth = depth * Math.tan(Math.toRadians(fov / 2)) * aspect;
        double screenX = 0.5 - offset / halfWidth / 2;
        if (Math.abs(screenX - 0.25) > 1e-6) throw new AssertionError("screen x=" + screenX);
    }
}
