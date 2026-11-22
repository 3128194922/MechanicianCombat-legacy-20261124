package com.mechaniciancombat.client;

import com.mechaniciancombat.client.rig.KineticSwordEnergyItemLayer;
import com.mechaniciancombat.client.rig.KineticSwordEnergyEffect;
import com.mechaniciancombat.item.KineticSwordFpaItem;
import com.mechaniciancombat.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Set;

/** 动能剑第一人称相机空间播放。 */
public final class KineticSwordFirstPersonRenderer extends GeoItemRenderer<KineticSwordFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static final Set<String> SWORD_BONES = Set.of(
			"Great_sword", "B1", "Blade", "C1", "C2");
	private static KineticSwordFirstPersonRenderer instance;
	private static String lastAnimation;
	private static long lastRevision = Long.MIN_VALUE;

	private KineticSwordFirstPersonRenderer(KineticSwordFpaItem item) {
		super(new KineticSwordFpaGeoModel());
		this.animatable = item;
		addRenderLayer(new KineticSwordEnergyItemLayer(this));
	}

	public static void render(LocalPlayer player, float tickDelta, PoseStack poseStack,
							  MultiBufferSource.BufferSource buffers, int light) {
		if (!(ModRegistries.KINETIC_SWORD_FPA_MODEL.get() instanceof KineticSwordFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new KineticSwordFirstPersonRenderer(item);
		}
		KineticSwordFirstPersonState.Snapshot snapshot = KineticSwordFirstPersonState.resolve(player);
		KineticSwordFirstPersonState.update(snapshot);
		ItemStack stack = new ItemStack(item);
		instance.currentItemStack = stack;
		if (!snapshot.animation().equals(lastAnimation) || snapshot.revision() != lastRevision) {
			lastAnimation = snapshot.animation();
			lastRevision = snapshot.revision();
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("sword_fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		ItemDisplayContext context = player.getMainArm() == HumanoidArm.LEFT
				? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				: ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		instance.renderByItem(stack, context, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
		buffers.endBatch();
	}

	@Override
	public void preRender(PoseStack poseStack, KineticSwordFpaItem animatable, BakedGeoModel model,
						  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
						  float partialTick, int packedLight, int packedOverlay,
						  float red, float green, float blue, float alpha) {
		itemRenderTranslations = new Matrix4f(poseStack.last().pose());
		scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model,
				isReRender, partialTick, packedLight, packedOverlay);
		poseStack.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay,
								  float red, float green, float blue, float alpha) {
		if (SWORD_BONES.contains(bone.getName())) {
			super.renderCubesOfBone(poseStack, bone, buffer, LightTexture.FULL_BRIGHT,
					packedOverlay, red, green, blue, alpha);
		}
	}

	@Override
	public void renderRecursively(PoseStack poseStack, KineticSwordFpaItem animatable, GeoBone bone,
								  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
								  boolean isReRender, float partialTick, int packedLight,
								  int packedOverlay, float red, float green, float blue, float alpha) {
		if (KineticSwordEnergyEffect.isEnergyBone(bone.getName())
				&& Minecraft.getInstance().player != null) {
			KineticSwordEnergyEffect.applyVisibility(bone, Minecraft.getInstance().player);
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
				isReRender, partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
				red, green, blue, alpha);
	}
}
