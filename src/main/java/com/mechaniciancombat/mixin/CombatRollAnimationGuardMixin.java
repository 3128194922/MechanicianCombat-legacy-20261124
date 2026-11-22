package com.mechaniciancombat.mixin;

import com.mechaniciancombat.TkGun;
import net.combatroll.client.animation.AnimatablePlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 动能剑翻滚时不播放 Combat Roll 自带的翻滚动画层。
 *
 * <p>只拦“播放翻滚动画”这一次调用，翻滚音效与粒子仍由 Combat Roll 正常播放；
 * 位移与无敌帧同样不受影响，动作由动能剑自己的 `GS_run` 持剑奔跑片段接管。
 */
@Pseudo
@Mixin(targets = "net.combatroll.client.RollEffect")
public abstract class CombatRollAnimationGuardMixin {
	@Redirect(method = "playVisuals", remap = false, require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/combatroll/client/animation/AnimatablePlayer;playRollAnimation(Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;)V"))
	private static void tkgun$skipKineticSwordRollAnimation(AnimatablePlayer animatable,
														   String animation, Vec3 direction) {
		// 只跳过动画层：翻滚音效、粒子与位移仍由 Combat Roll 负责。
		if (animatable instanceof Player player && TkGun.isKineticSword(player)) {
			return;
		}
		animatable.playRollAnimation(animation, direction);
	}
}
