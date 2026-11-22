package com.mechaniciancombat.client;

public final class ReticleLayoutContract {
    public static void main(String[] args) {
        var center = ReticleLayout.position(0, 0, 1, 400, 200);
        check(center.x() == 200 && center.y() == 100, "overlap retains both centers");
        var visible = ReticleLayout.position(0.5, 0.25, 1, 400, 200);
        check(visible.x() == 300 && visible.y() == 75, "visible point projects accurately");
        var outside = ReticleLayout.position(4, 0, 1, 400, 200);
        check(outside.x() == 400 - ReticleLayout.RADIUS - 1 && outside.y() == 100,
                "off-screen point remains visible at the correct edge");
        var behind = ReticleLayout.position(-1, 0, -1, 400, 200);
        check(behind.x() == ReticleLayout.RADIUS + 1, "behind-camera point keeps its lateral direction");
        var missing = ReticleLayout.position(Double.NaN, 0, 1, 400, 200);
        check(missing.x() == 200 && missing.y() == 100, "unavailable projection falls back to center");
        check(ReticleLayout.RADIUS > Math.sqrt(2) * 8, "ring remains distinct from center X when overlapping");
        try {
            var precise = ReticleLayout.class.getDeclaredMethod("precisePosition", double.class, double.class,
                    double.class, int.class, int.class);
            Object point = precise.invoke(null, 0.00125, -0.0025, 1.0, 400, 200);
            double x = ((Number) point.getClass().getDeclaredMethod("x").invoke(point)).doubleValue();
            double y = ((Number) point.getClass().getDeclaredMethod("y").invoke(point)).doubleValue();
            check(Math.abs(x - 200.25) < 1e-7 && Math.abs(y - 100.25) < 1e-7,
                    "projection preserves subpixel movement");
        } catch (ReflectiveOperationException e) { throw new AssertionError("fractional reticle projection", e); }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
