package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.GunJumpRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress ordinary jumping while a gun is held; the key is used by gun skills. */
@Mixin(KeyboardInput.class)
public abstract class GunJumpLockMixin {
	@Inject(method = "tick(ZF)V", at = @At("TAIL"))
	private void tkgun$lockJump(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		boolean jumpPressed = client.player != null && client.screen == null
				&& client.options.keyJump.isDown();
		if (!jumpPressed || GunJumpRules.canJump(client)) {
			return;
		}
		KeyboardInput self = (KeyboardInput) (Object) this;
		self.jumping = false;

	}
}
