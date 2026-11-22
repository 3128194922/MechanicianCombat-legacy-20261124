package com.mechaniciancombat.client;

/** Checks post-dodge forward gating and slow, bounded A/D steering. */
public final class DodgeSprintControlContract {
    public static void main(String[] args) throws Exception {
        var type = Class.forName("com.mechaniciancombat.client.DodgeSprintControl");
        var forward = type.getMethod("forwardHeld", boolean.class, boolean.class);
        check((boolean) forward.invoke(null, true, false), "W must allow sprint");
        check(!(boolean) forward.invoke(null, false, false), "A, D or no key must not allow sprint");
        check(!(boolean) forward.invoke(null, false, true), "S must not allow sprint");
        check(!(boolean) forward.invoke(null, true, true), "opposing W and S must not force sprint");
        var turn = type.getMethod("steerYaw", float.class, int.class);
        close((float) turn.invoke(null, 0f, 1), -4f, "A turns left gradually");
        close((float) turn.invoke(null, 0f, -1), 4f, "D turns right gradually");
        close((float) turn.invoke(null, 30f, 0), 30f, "no sideways input does not turn");
        close((float) turn.invoke(null, 179f, -1), -177f, "turn wraps at 180 degrees");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.0001f, message + ": " + actual);
    }
}
