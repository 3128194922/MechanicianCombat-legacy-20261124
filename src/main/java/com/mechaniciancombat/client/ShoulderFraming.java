package com.mechaniciancombat.client;

final class ShoulderFraming {
    static double sideOffset(double distance, double verticalFovDegrees, double aspect) {
        if (!Double.isFinite(distance) || !Double.isFinite(verticalFovDegrees) || !Double.isFinite(aspect)
                || distance <= 0 || aspect <= 0 || verticalFovDegrees <= 0 || verticalFovDegrees >= 180) return 0;
        return 0.5D * distance * Math.tan(Math.toRadians(verticalFovDegrees * 0.5D)) * aspect;
    }

    private ShoulderFraming() { }
}
