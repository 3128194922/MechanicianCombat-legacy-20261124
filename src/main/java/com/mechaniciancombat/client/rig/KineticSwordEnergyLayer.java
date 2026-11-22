package com.mechaniciancombat.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** 第三人称动能剑能量帧（自发光半透明）。 */
public final class KineticSwordEnergyLayer extends GeoRenderLayer<BowgunRig> {
	public KineticSwordEnergyLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(PoseStack poseStack, BowgunRig animatable, GeoBone bone, RenderType type,
							  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
							  int packedLight, int packedOverlay) {
		if (!KineticSwordEnergyEffect.isEnergyBone(bone.getName()) || bone.getCubes().isEmpty()
				|| !(renderer instanceof BowgunRigRenderer rig)) {
			return;
		}
		Entity entity = rig.getCurrentEntity();
		int colour = KineticSwordEnergyEffect.colorFor(entity);
		if ((colour >>> 24) == 0) {
			return;
		}
		int frame = KineticSwordEnergyEffect.frameFor(entity);
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
