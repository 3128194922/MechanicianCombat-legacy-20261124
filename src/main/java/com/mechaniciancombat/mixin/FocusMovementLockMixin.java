package com.mechaniciancombat.mixin;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.GunClientState;
import com.mechaniciancombat.client.TkGunClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 专注期间清零移动输入；翻滚按下帧保留方向供 Combat Roll 计算翻滚向量。 */
@Mixin(KeyboardInput.class)
public abstract class FocusMovementLockMixin {
	@Inject(method = "tick(ZF)V", at = @At("TAIL"))
	private void tkgun$lockFocusMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !TkGun.holdingGun(client.player)) {
			return;
		}
		int playerId = client.player.getId();
		GunClientState.Entry entry = GunClientState.get(playerId);
		boolean movementLock = GunClientState.focusish(playerId);
		boolean skillLoad = entry != null && entry.state == TkGun.State.FORTRESS_SKILL_LOAD;
		if (entry != null) {
			movementLock |= entry.active() && entry.meleeDash;
			// 空格快速六连斩期间锁定移动；普通左键斩击允许缓慢移动。
			boolean kineticSword = TkGun.isKineticSword(client.player);
			movementLock |= entry.state == TkGun.State.MELEE && !entry.meleeReturning
					&& ((kineticSword
					&& (entry.kineticRollCombo || GunClientState.kineticComboLocksMovement(playerId)))
					|| (!kineticSword
					&& (entry.meleeHoldOver || (entry.shotIndex == 3 && entry.elapsed < 15))));
			movementLock |= switch (entry.state) {
				case SKILL_JUMP, FALL_ATTACK, FALL_ATTACK_RECOVERY, FORTRESS_SKILL_READY_GATHER,
						FORTRESS_SKILL_GATHER, FORTRESS_SKILL_HIT -> true;
				case FORTRESS_SKILL_LOAD -> entry.fortressSlide;
				default -> false;
			};
		}
		if (!movementLock && !skillLoad) return;
		KeyboardInput self = (KeyboardInput) (Object) this;
		if (movementLock && !TkGunClient.isRollTriggerTick(client)) {
			self.forwardImpulse = 0.0F;
			self.leftImpulse = 0.0F;
		}
		// 空格六连斩期间同样锁定跳跃；连斩输入由 GunJumpPayload 单独处理。
		self.jumping = false;
		self.shiftKeyDown = false;
	}
}
