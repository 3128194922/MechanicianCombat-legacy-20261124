package com.mechaniciancombat.client;

/** Plain-JDK regression checks for the independent r396 shoulder-camera angles. */
public final class ShoulderCameraAnglesContract {
    public static void main(String[] args) {
        ShoulderCameraAngles camera = new ShoulderCameraAngles(179.0F, 0.0F);
        camera.turn(20.0D, 1000.0D);
        close(camera.yaw(), -178.0F, "mouse yaw must wrap without taking the body yaw");
        close(camera.pitch(), 90.0F, "pitch must clamp at +90");
        camera.turn(0, -2000);
        close(camera.pitch(), -90.0F, "pitch must clamp at -90");
        close(ShoulderCameraAngles.steerYaw(0, 90, 1), 90, "running follows the camera immediately");
        close(ShoulderCameraAngles.steerYaw(0, -90, -1), -90, "strafe does not offset the camera heading");
        close(ShoulderCameraAngles.steerYaw(179, -179, 0), -179, "steering takes shortest wraparound path");
        camera.reset(40, 15);
        close(camera.yaw(), 40, "new session resets yaw");
        close(camera.pitch(), 15, "new session resets pitch");
        try {
            var begin = ShoulderCameraAngles.class.getMethod("beginTick");
            var steer = ShoulderCameraAngles.class.getMethod("steerTo", float.class);
            var render = ShoulderCameraAngles.class.getMethod("renderYaw", float.class);
            camera.reset(179, 0);
            begin.invoke(camera);
            steer.invoke(camera, -177f);
            close((float) render.invoke(camera, 0f), 179, "tick starts at previous yaw");
            close((float) render.invoke(camera, .5f), -179, "camera takes shortest path across yaw wrap");
            close((float) render.invoke(camera, 1f), -177, "tick ends at current yaw");
            camera.turn(20, 0);
            close((float) render.invoke(camera, .5f), -176, "mouse movement remains immediate during steering");
            begin.invoke(camera);
            close((float) render.invoke(camera, .5f), -174, "released steering must not replay the last turn");
            camera.reset(40, 15);
            close((float) render.invoke(camera, .5f), 40, "camera reset must not interpolate from an old session");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("camera interpolation contract", e);
        }
        for (var state : new com.mechaniciancombat.TkGun.State[] {
                com.mechaniciancombat.TkGun.State.AIM,
                com.mechaniciancombat.TkGun.State.AIM_HOLD,
                com.mechaniciancombat.TkGun.State.FIRE,
                com.mechaniciancombat.TkGun.State.RELOAD}) {
            if (!ShoulderCouplingState.focusFollowsCamera(true, state))
                throw new AssertionError("focus must track camera in " + state);
        }
        for (var state : new com.mechaniciancombat.TkGun.State[] {
                com.mechaniciancombat.TkGun.State.ROLL,
                com.mechaniciancombat.TkGun.State.ROLL_TO_IDLE,
                com.mechaniciancombat.TkGun.State.SLIDE,
                com.mechaniciancombat.TkGun.State.SLIDE_READY}) {
            if (ShoulderCouplingState.focusFollowsCamera(true, state))
                throw new AssertionError("dodge must not track camera in " + state);
        }
        if (ShoulderCouplingState.focusFollowsCamera(false, com.mechaniciancombat.TkGun.State.IDLE))
            throw new AssertionError("idle must remain decoupled");
        System.out.println("ShoulderCameraAnglesContract: 8 checks passed");
    }

    private static void close(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > 0.0001F) throw new AssertionError(message + ": " + actual);
    }
}
