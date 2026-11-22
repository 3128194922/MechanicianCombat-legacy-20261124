package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import net.minecraft.util.Mth;

/**
 * 枢德之裁近战期间的**移动输入曲线**（上游 `MeleeMovementSlowdown`）。
 *
 * <p>缩放的是**输入**而不是移动速度属性 —— 原版的加速度与惯性保持不变，
 * 所以收招后不会突然"弹射"出去，手感更接近 Better Combat 那类近战模组。
 *
 * <p>曲线：进度 0 → 1（出招 → 收招），输入倍率从 1 降到 {@link #MIN_MULTIPLIER} 再回升，
 * 用 `easeOutCubic` 让"最低点"更集中在中段，出招起手与收招末尾都接近满速。
 */
public final class MeleeMovementSlowdown {
	/** 近战动作中的最低输入倍率：削减 70%。 */
	public static final float MIN_MULTIPLIER = 0.3F;
	/** 超过这个进度就不再减速（收招尾巴），避免"动作播完了还走不动"。 */
	private static final float EASING_END = 0.98F;

	public static float inputMultiplier(int playerId) {
		GunClientState.Entry entry = GunClientState.get(playerId);
		if (entry == null || entry.state != TkGun.State.MELEE
				|| entry.meleeReturning || entry.total <= 0 || entry.elapsed >= entry.total) {
			return 1.0F;
		}
		// 空格发动的快速六连斩：锁定移动（由 FocusMovementLockMixin 清零输入）。
		if (entry.kineticRollCombo || GunClientState.kineticComboActive(playerId)) {
			return 0.0F;
		}
		float progress = Mth.clamp(entry.elapsed / (float) entry.total, 0.0F, 1.0F);
		if (progress >= EASING_END) {
			return 1.0F;
		}
		// 0 → 1 → 0 的三角波，再套 easeOutCubic 压在中段。
		double curveInput = progress <= 0.5F
				? progress * 2.0D
				: 1.0D - (progress - 0.5D) * 2.0D;
		double eased = easeOutCubic(curveInput);
		return (float) (1.0D - (1.0D - MIN_MULTIPLIER) * eased);
	}

	private static double easeOutCubic(double value) {
		double inverse = 1.0D - Mth.clamp(value, 0.0D, 1.0D);
		return 1.0D - inverse * inverse * inverse;
	}

	private MeleeMovementSlowdown() {
	}
}
