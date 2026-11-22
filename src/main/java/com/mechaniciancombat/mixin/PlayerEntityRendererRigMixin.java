package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.rig.BowgunRigClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 保留原版 PlayerRenderer 的玩家身体、皮肤和装备层；持枪时在它渲染前调用 GeckoLib，
 * 绘制枪械并采集同帧身体姿势供 PlayerAnimator 使用。
 * <ol>
 *   <li>把 {@code EntityRendererFactory.Context} 记下来 —— GeckoLib 的渲染器构造要用；</li>
 *   <li>渲染玩家时，如果手里是破垒重炮 → 先让 GeckoLib 绘制枪械，再继续原版玩家与装备层渲染。</li>
 * </ol>
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererRigMixin {
	@org.spongepowered.asm.mixin.Unique private boolean[] tkgun$bodyVisibility;

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void tkgun$hideBodyForUndersuit(AbstractClientPlayer player, CallbackInfo ci) {
        if (com.mechaniciancombat.item.MechanicianUndersuitItem.isWearingFullSet(player)) {
            ((PlayerRenderer) (Object) this).getModel().hat.visible =
                    !com.mechaniciancombat.client.HelmetClientState.shouldHideHair(player);
        }
        if (!com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player)) return;
        var model = ((PlayerRenderer) (Object) this).getModel();
        var parts = new net.minecraft.client.model.geom.ModelPart[]{model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg, model.jacket, model.leftSleeve, model.rightSleeve,
                model.leftPants, model.rightPants};
        tkgun$bodyVisibility = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            tkgun$bodyVisibility[i] = parts[i].visible;
            parts[i].visible = false;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void tkgun$restoreBody(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (tkgun$bodyVisibility == null) return;
        var model = ((PlayerRenderer) (Object) this).getModel();
        var parts = new net.minecraft.client.model.geom.ModelPart[]{model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg, model.jacket, model.leftSleeve, model.rightSleeve,
                model.leftPants, model.rightPants};
        for (int i = 0; i < parts.length; i++) parts[i].visible = tkgun$bodyVisibility[i];
        tkgun$bodyVisibility = null;
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void tkgun$renderUndersuitHand(PoseStack pose, MultiBufferSource buffers, int light,
            AbstractClientPlayer player, net.minecraft.client.model.geom.ModelPart arm,
            net.minecraft.client.model.geom.ModelPart sleeve, CallbackInfo ci) {
        if (!com.mechaniciancombat.item.MechanicianUndersuitItem.isVisiblyEquipped(player)) return;
        var model = ((PlayerRenderer) (Object) this).getModel();
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0, 0, 0, 0, 0);
        arm.xRot = 0.0F;
        var part = arm == model.rightArm ? com.mechaniciancombat.client.MechanicianArmorGeometry.Part.RIGHT_ARM
                : com.mechaniciancombat.client.MechanicianArmorGeometry.Part.LEFT_ARM;
        com.mechaniciancombat.client.MechanicianArmorGeometry.render(
                com.mechaniciancombat.item.MechanicianArmorPiece.UNDERSUIT, pose, buffers, model, light,
                player, 0, 0, 0, part);
        ci.cancel();
    }

	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
	private void tkgun$rememberContext(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
		BowgunRigClient.setContext(ctx);
		com.mechaniciancombat.client.WarBannerHeldRenderer.setContext(ctx);
		com.mechaniciancombat.client.HunterSpearHeldRenderer.setContext(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void tkgun$renderRig(AbstractClientPlayer player, float yaw, float tickDelta,
								 PoseStack matrices, MultiBufferSource consumers, int light, CallbackInfo ci) {
		BowgunRigClient.renderIfHolding(player, yaw, tickDelta, matrices, consumers, light);
		com.mechaniciancombat.client.WarBannerHeldRenderer.renderIfActive(
				player, yaw, tickDelta, matrices, consumers, light);
		com.mechaniciancombat.client.HunterSpearHeldRenderer.renderIfActive(
				player, yaw, tickDelta, matrices, consumers, light);
	}
}
