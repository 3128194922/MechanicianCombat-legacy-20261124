package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.TkGun;

/**
 * `gun_pose` 控制器的**纯判定规则**（无 GeckoLib / MC 依赖，可被契约穷举）。
 *
 * <p>为什么单独拆出来：这段规则是"蹲射时人像站起来一下"的真正修复点，只有这两把枪会走到
 * （`bowgun` 在 `BowgunRig#pickGunPose` 入口就被挡掉）。它原先埋在
 * {@code AnimationState} 回调里，只能靠读源码或实机目视来验证；抽成纯函数后，
 * 契约可以把"枪种 × 状态 × 专注 × 举枪"的**全部组合**跑一遍。
 */
public final class GunPoseRules {
	/** 需要 `gun_pose` 覆盖的枪（作者为它们单独写了 `open` / `close`）。 */
	public static boolean hasGunPoseClips(String variant) {
		return "abyssal_ancient_cannon".equals(variant) || "veritas_et_justitia".equals(variant);
	}

	/** 翻滚/滑铲及其收势。 */
	public static boolean isDodge(TkGun.State state) {
		return state == TkGun.State.ROLL || state == TkGun.State.ROLL_TO_IDLE
				|| state == TkGun.State.SLIDE || state == TkGun.State.SLIDE_READY;
	}

	/**
	 * 这一帧 `gun_pose` 该播哪一段。
	 *
	 * @param variant   {@code GunVisual} 判出来的枪种（`bowgun` / `abyssal_ancient_cannon` / `veritas_et_justitia`）
	 * @param state     客户端镜像状态
	 * @param active    该状态是否还没走完（{@code GunClientState.Entry#active()}）
	 * @param focus     服务端下发的专注位
	 * @param focusish  专注位 **或** 状态在 AIM* 里（{@code GunClientState#focusish}）
	 * @param hipAiming 举着枪（右键按住 / 别人看 isUsingItem）
	 * @return {@code "open"} / {@code "close"} / {@code null}（= STOP，交给下层动画）
	 */
	public static String select(String variant, TkGun.State state, boolean active, boolean focus,
						 boolean focusish, boolean hipAiming) {
		if (!hasGunPoseClips(variant)) {
			return null;                       // bowgun：动画自带完整枪件通道
		}
		// 射击段自带完整枪件子树（后坐 / 枪口焰 / 抛壳），静态姿态会把它们盖掉。
		if (active && state == TkGun.State.FIRE) {
			return null;
		}
		boolean silentStorm = "abyssal_ancient_cannon".equals(variant);
		if (silentStorm && state == TkGun.State.AIM_HOLD) {
			// 它的 Focus_hold 已经是标准架枪姿态，再套 open 会把装配好的枪挪位。
			return null;
		}
		if (!silentStorm && state == TkGun.State.AIM_HOLD && focus) {
			return "open";
		}
		boolean dodge = isDodge(state);
		if ("veritas_et_justitia".equals(variant) && dodge) {
			// Veritas 在翻滚/滑铲期间保持刀刃展开的枪身几何。
			return "open";
		}
		if (!focusish && !hipAiming && (state == TkGun.State.IDLE || dodge)) {
			// 非专注的待机与闪避收势：枪保持作者给的折叠姿态。
			return "close";
		}
		return null;
	}

	private GunPoseRules() {
	}
}
