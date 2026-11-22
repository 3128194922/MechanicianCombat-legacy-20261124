package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.WarBannerFirstPersonRenderer;
import com.mechaniciancombat.client.WarBannerSkillClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 战旗施法期间用作者导出的相机空间旗子替换原版第一人称手。 */
@Mixin(ItemInHandRenderer.class)
public abstract class WarBannerFirstPersonMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void tkgun$renderWarBannerFpa(float partialTicks, PoseStack poseStack,
										  MultiBufferSource.BufferSource buffers,
										  LocalPlayer player, int combinedLight, CallbackInfo ci) {
		if (player == null || !WarBannerSkillClientState.isActive(player.getId())) {
			return;
		}
		WarBannerFirstPersonRenderer.render(player, partialTicks, poseStack, buffers, combinedLight);
		ci.cancel();
	}
}
