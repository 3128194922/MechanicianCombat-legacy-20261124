package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.SprintDirectionCorrection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FallbackShoulderRunMixin {
	@Inject(method = "tick(ZF)V", at = @At("HEAD"))
	private void tkgun$beginCameraInputTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		com.mechaniciancombat.client.FallbackShoulderCamera.beginInputTick(Minecraft.getInstance());
	}

	@Inject(method = "tick(ZF)V", at = @At("TAIL"))
	private void tkgun$steerDecoupledRunning(boolean slowDown, float slowDownFactor,
											 CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		KeyboardInput input = (KeyboardInput) (Object) this;
		SprintDirectionCorrection.correct(client, input);
	}
}
