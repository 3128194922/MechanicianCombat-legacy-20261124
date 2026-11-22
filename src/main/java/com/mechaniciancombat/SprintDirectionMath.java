package com.mechaniciancombat;
import net.minecraft.util.Mth;
/** Pure camera-relative direction calculations shared with regression tests. */
public final class SprintDirectionMath {
    public static float movementYaw(float cameraYaw,float forward,float side) {
        return Mth.wrapDegrees((float)(cameraYaw-Math.toDegrees(Math.atan2(side,forward))));
    }
    public static float steerDashYaw(float current,float target) {
        return Mth.wrapDegrees(current+Mth.clamp(Mth.wrapDegrees(target-current),-4,4));
    }
    private SprintDirectionMath() { }
}
