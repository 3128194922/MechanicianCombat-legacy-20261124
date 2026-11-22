package com.mechaniciancombat.mixin;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.CombatBackpackClientState;
import com.mechaniciancombat.client.HunterSpearSkillClientState;
import com.mechaniciancombat.client.KineticSwordFirstPersonRenderer;
import com.mechaniciancombat.client.SoulAnchorSkillClientState;
import com.mechaniciancombat.client.WarBannerSkillClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅替换动能剑的第一人称手持渲染。 */
@Mixin(ItemInHandRenderer.class)
public abstract class KineticSwordFirstPersonMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void tkgun$renderKineticSwordFpa(float partialTicks, PoseStack poseStack,
											 MultiBufferSource.BufferSource buffers,
											 LocalPlayer player, int combinedLight, CallbackInfo ci) {
		if (player == null || !TkGun.isKineticSword(player)) {
			return;
		}
		int id = player.getId();
		if (SoulAnchorSkillClientState.firstPersonActive(id)
				|| WarBannerSkillClientState.isActive(id)
				|| HunterSpearSkillClientState.isActive(id)) {
			return;
		}
		KineticSwordFirstPersonRenderer.render(player, partialTicks, poseStack, buffers, combinedLight);
		ci.cancel();
	}
}
