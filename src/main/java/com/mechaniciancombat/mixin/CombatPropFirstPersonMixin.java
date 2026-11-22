package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.CombatPropFirstPersonRenderer;
import com.mechaniciancombat.client.HunterSpearSkillClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 猎矛部署期间用作者导出的相机空间猎矛替换原版第一人称手。 */
@Mixin(ItemInHandRenderer.class)
public abstract class CombatPropFirstPersonMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void tkgun$renderHunterAnchorFpa(float partialTicks, PoseStack poseStack,
											 MultiBufferSource.BufferSource buffers,
											 LocalPlayer player, int combinedLight, CallbackInfo ci) {
		if (player == null) {
			return;
		}
		if (HunterSpearSkillClientState.isActive(player.getId())
				&& CombatPropFirstPersonRenderer.renderHunterAnchor(
						player, partialTicks, poseStack, buffers, combinedLight)) {
			ci.cancel();
			return;
		}
		// 背包炮台（便携 / 自动）同样用相机空间道具替换第一人称手。
		if (CombatPropFirstPersonRenderer.renderTurret(
				player, partialTicks, poseStack, buffers, combinedLight)) {
			ci.cancel();
		}
	}
}
