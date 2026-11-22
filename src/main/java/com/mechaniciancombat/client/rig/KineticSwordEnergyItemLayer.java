package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.item.KineticSwordFpaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** 第一人称动能剑能量帧（自发光半透明）。 */
public final class KineticSwordEnergyItemLayer extends GeoRenderLayer<KineticSwordFpaItem> {
	public KineticSwordEnergyItemLayer(GeoItemRenderer<KineticSwordFpaItem> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(PoseStack poseStack, KineticSwordFpaItem animatable, GeoBone bone,
							  RenderType type, MultiBufferSource bufferSource, VertexConsumer buffer,
							  float partialTick, int packedLight, int packedOverlay) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!KineticSwordEnergyEffect.isEnergyBone(bone.getName())
				|| bone.getCubes().isEmpty() || player == null) {
			return;
		}
		int colour = KineticSwordEnergyEffect.colorFor(player);
		if ((colour >>> 24) == 0) {
			return;
		}
		int frame = KineticSwordEnergyEffect.frameFor(player);
		VertexConsumer energy = bufferSource.getBuffer(
				RenderType.entityTranslucentEmissive(KineticSwordEnergyEffect.TEXTURE));
		for (GeoCube cube : bone.getCubes()) {
			poseStack.pushPose();
			renderer.renderCube(poseStack, KineticSwordEnergyEffect.remapUv(cube, frame), energy,
					LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
			poseStack.popPose();
		}
	}
}
