package com.mechaniciancombat.client;

/** Maps clip-space impact positions to an on-screen marker. */
final class ReticleLayout {
    static final int RADIUS = 12;
    record Point(int x, int y) { }
    record PrecisePoint(double x, double y) { }

    static Point position(double x, double y, double w, int width, int height) {
        PrecisePoint point = precisePosition(x, y, w, width, height);
        return new Point((int) Math.round(point.x()), (int) Math.round(point.y()));
    }

    static PrecisePoint precisePosition(double x, double y, double w, int width, int height) {
        int cx = width / 2, cy = height / 2;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w)) return new PrecisePoint(cx, cy);
        double divisor = Math.max(Math.abs(w), 1.0E-5D);
        double dx = x / divisor * width / 2.0D;
        double dy = -y / divisor * height / 2.0D;
        // A point behind the camera cannot be projected normally: indicate its direction at the edge.
        if (w <= 1.0E-5D && Math.abs(dx) + Math.abs(dy) < 1.0E-5D) dy = -1.0D;
        double maxX = Math.max(0, cx - RADIUS - 1);
        double maxY = Math.max(0, cy - RADIUS - 1);
        double scale = Math.max(1.0D, Math.max(Math.abs(dx) / Math.max(1, maxX), Math.abs(dy) / Math.max(1, maxY)));
        if (w <= 1.0E-5D) scale = Math.max(Math.abs(dx) / Math.max(1, maxX), Math.abs(dy) / Math.max(1, maxY));
        return new PrecisePoint(cx + dx / scale, cy + dy / scale);
    }
    private ReticleLayout() { }
}
