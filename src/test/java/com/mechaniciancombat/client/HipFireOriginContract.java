package com.mechaniciancombat.client;

import net.minecraft.world.phys.Vec3;

public final class HipFireOriginContract {
    public static void main(String[] args) {
        Vec3 player = new Vec3(10, 64, 20);
        Vec3 runningSample = new Vec3(6, 65, 20);
        Vec3 forward = HipFireOrigin.select(true, player, 0, runningSample);
        if (forward.z <= player.z || Math.abs(forward.x - player.x) > 0.5)
            throw new AssertionError("running shot must use the corrected forward pose, not the running sample");
        Vec3 turned = HipFireOrigin.select(true, player, 90, runningSample);
        if (turned.x >= player.x || Math.abs(turned.z - player.z) > 0.5)
            throw new AssertionError("running shot must use current camera yaw");
        if (!HipFireOrigin.select(false, player, 90, runningSample).equals(runningSample))
            throw new AssertionError("normal hip fire keeps the rendered muzzle sample");
    }
}
