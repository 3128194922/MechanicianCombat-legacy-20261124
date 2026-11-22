package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import net.minecraft.world.phys.Vec3;

/** Predicts the firing-pose muzzle when the last rendered sample is a sprint pose. */
final class HipFireOrigin {
    static Vec3 select(boolean running, Vec3 player, float yaw, Vec3 sample) {
        if (!running && sample != null) return sample;
        if (!running) return player;
        double angle = Math.toRadians(yaw);
        double forwardX = -Math.sin(angle), forwardZ = Math.cos(angle);
        double sideX = -Math.cos(angle), sideZ = -Math.sin(angle);
        return player.add(forwardX * TkGun.MUZZLE_FORWARD + sideX * TkGun.MUZZLE_SIDE,
                TkGun.MUZZLE_HEIGHT,
                forwardZ * TkGun.MUZZLE_FORWARD + sideZ * TkGun.MUZZLE_SIDE);
    }
    private HipFireOrigin() { }
}
