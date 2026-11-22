package com.mechaniciancombat.client;

import com.mechaniciancombat.item.SoulAnchorFpaItem;
import com.mechaniciancombat.item.SoulAnchorFpaRenderState;
import com.mechaniciancombat.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** 灵魂锚 ready / throw 阶段的相机空间第一人称模型。 */
public final class SoulAnchorFirstPersonRenderer extends GeoItemRenderer<SoulAnchorFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static SoulAnchorFirstPersonRenderer instance;
	private static SoulAnchorFpaRenderState.Mode lastMode = SoulAnchorFpaRenderState.Mode.NONE;
	private static LocalPlayer activePlayer;
	private static double seconds;
	private static boolean throwing;

	private SoulAnchorFirstPersonRenderer(SoulAnchorFpaItem item) {
		super(new SoulAnchorFpaGeoModel());
		this.animatable = item;
	}

	public static void render(LocalPlayer player, float tickDelta, PoseStack poseStack,
							  MultiBufferSource.BufferSource buffers, int light) {
		if (!(ModRegistries.SOUL_ANCHOR_FPA_MODEL.get() instanceof SoulAnchorFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new SoulAnchorFirstPersonRenderer(item);
		}
		ItemStack stack = new ItemStack(item);
		instance.currentItemStack = stack;
		SoulAnchorFpaRenderState.Mode mode = SoulAnchorSkillClientState.ready(player.getId())
				? SoulAnchorFpaRenderState.Mode.READY : SoulAnchorFpaRenderState.Mode.THROW;
		SoulAnchorFpaRenderState.set(mode);
		if (mode != lastMode) {
			lastMode = mode;
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("anchor");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		activePlayer = player;
		seconds = SoulAnchorSkillClientState.firstPersonSeconds(player.getId(), tickDelta);
		throwing = SoulAnchorSkillClientState.firstPersonThrowing(player.getId());
		try {
			instance.renderByItem(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
					poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			buffers.endBatch();
		} finally {
			activePlayer = null;
			SoulAnchorFpaRenderState.set(SoulAnchorFpaRenderState.Mode.NONE);
		}
	}

	@Override
	public void preRender(PoseStack poseStack, SoulAnchorFpaItem animatable, BakedGeoModel model,
						  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
						  float partialTick, int packedLight, int packedOverlay,
						  float red, float green, float blue, float alpha) {
		itemRenderTranslations = new Matrix4f(poseStack.last().pose());
		scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model,
				isReRender, partialTick, packedLight, packedOverlay);
		poseStack.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderRecursively(PoseStack poseStack, SoulAnchorFpaItem animatable, GeoBone bone,
								  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
								  boolean isReRender, float partialTick, int packedLight,
								  int packedOverlay, float red, float green, float blue, float alpha) {
		if (activePlayer != null) {
			WarBannerHeldPose.applyAnchorFirstPerson(bone, seconds, throwing);
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
				isReRender, partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
				red, green, blue, alpha);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay,
								  float red, float green, float blue, float alpha) {
		if ("anchor".equals(bone.getName())) {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
					red, green, blue, alpha);
		}
	}
}
