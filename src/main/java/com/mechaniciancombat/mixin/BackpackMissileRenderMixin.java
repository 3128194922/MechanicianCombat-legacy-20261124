package com.mechaniciancombat.mixin;

import com.mechaniciancombat.item.BackpackFireworkData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.ShulkerBulletModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FireworkEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 导弹舱冷发射的火箭改用潜影贝弹外观，普通烟花保持原版渲染。 */
@Mixin(FireworkEntityRenderer.class)
public abstract class BackpackMissileRenderMixin {
	@Unique
	private static final ResourceLocation TKgun$MISSILE_TEXTURE =
			new ResourceLocation("textures/entity/shulker/spark.png");

	@Unique
	private ShulkerBulletModel<FireworkRocketEntity> tkgun$missileModel;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void tkgun$createMissileModel(EntityRendererProvider.Context context, CallbackInfo ci) {
		tkgun$missileModel = new ShulkerBulletModel<>(context.bakeLayer(ModelLayers.SHULKER_BULLET));
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void tkgun$renderMissileBoxRocket(FireworkRocketEntity rocket, float yaw, float partialTick,
											  PoseStack poseStack, MultiBufferSource buffers, int light,
											  CallbackInfo ci) {
		if (tkgun$missileModel == null || !BackpackFireworkData.usesMissileVisual(rocket.getItem())) {
			return;
		}
		float age = rocket.tickCount + partialTick;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.15F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(age * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(age * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.15F) * 360.0F));
		poseStack.scale(-0.5F, -0.5F, 0.5F);
		tkgun$missileModel.setupAnim(rocket, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		VertexConsumer body = buffers.getBuffer(tkgun$missileModel.renderType(TKgun$MISSILE_TEXTURE));
		tkgun$missileModel.renderToBuffer(poseStack, body, light, OverlayTexture.NO_OVERLAY,
				1.0F, 1.0F, 1.0F, 1.0F);
		poseStack.scale(1.5F, 1.5F, 1.5F);
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(TKgun$MISSILE_TEXTURE));
		tkgun$missileModel.renderToBuffer(poseStack, glow, light, OverlayTexture.NO_OVERLAY,
				1.0F, 1.0F, 1.0F, 0.15F);
		poseStack.popPose();
		ci.cancel();
	}
}
