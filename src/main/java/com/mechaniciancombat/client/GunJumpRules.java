package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import net.minecraft.client.Minecraft;

/** Shared client-side rule for jumping while carrying the gun. */
public final class GunJumpRules {
	public static boolean canJump(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null
				|| !TkGun.holdingGun(client.player)) {
			return true;
		}
		return false;
	}

	private GunJumpRules() {
	}
}
