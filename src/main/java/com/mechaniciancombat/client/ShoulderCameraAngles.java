package com.mechaniciancombat.client;

/** r396 angle rules; independent of the player entity and render interpolation. */
public final class ShoulderCameraAngles {
    private float yaw;
    private float previousYaw;
    private float pitch;

    public ShoulderCameraAngles(float yaw, float pitch) { reset(yaw, pitch); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void reset(float yaw, float pitch) {
        this.yaw = wrap(yaw);
        this.previousYaw = this.yaw;
        this.pitch = Math.max(-90, Math.min(90, pitch));
    }

    public void turn(double deltaX, double deltaY) {
        float change = (float) deltaX * 0.15F;
        yaw = wrap(yaw + change);
        previousYaw = wrap(previousYaw + change);
        pitch = Math.max(-90, Math.min(90, pitch + (float) deltaY * 0.15F));
    }

    public void beginTick() { previousYaw = yaw; }
    public void steerTo(float yaw) { this.yaw = wrap(yaw); }
    public float renderYaw(float partialTick) {
        float fraction = Math.max(0, Math.min(1, partialTick));
        return wrap(previousYaw + wrap(yaw - previousYaw) * fraction);
    }

    public static float steerYaw(float bodyYaw, float cameraYaw, float sideways) {
        return wrap(cameraYaw);
    }

    private static float wrap(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }
}
