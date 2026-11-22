package com.mechaniciancombat.client;

/** Input rules for the sprint granted by a dodge. */
public final class DodgeSprintControl {
    public static boolean forwardHeld(boolean forward, boolean backward) {
        return forward && !backward;
    }
    public static float steerYaw(float yaw, int left) {
        float result = (yaw - Integer.signum(left) * 4.0F) % 360.0F;
        if (result >= 180.0F) result -= 360.0F;
        if (result < -180.0F) result += 360.0F;
        return result;
    }
    private DodgeSprintControl() { }
}
