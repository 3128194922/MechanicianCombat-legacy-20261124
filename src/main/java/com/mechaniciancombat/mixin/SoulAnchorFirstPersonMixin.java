package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.SoulAnchorFirstPersonRenderer;
import com.mechaniciancombat.client.SoulAnchorSkillClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 灵魂锚 ready / throw 期间用相机空间模型替换原版第一人称手。 */
@Mixin(ItemInHandRenderer.class)
public abstract class SoulAnchorFirstPersonMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void tkgun$renderSoulAnchorFpa(float partialTicks, PoseStack poseStack,
										   MultiBufferSource.BufferSource buffers,
										   LocalPlayer player, int combinedLight, CallbackInfo ci) {
		if (player == null || !SoulAnchorSkillClientState.firstPersonActive(player.getId())) {
			return;
		}
		SoulAnchorFirstPersonRenderer.render(player, partialTicks, poseStack, buffers, combinedLight);
		ci.cancel();
	}
}
