package com.mechaniciancombat.client;

/**
 * **开火路径与枪口指示器必须共用同一个方向分支判据**。
 *
 * <p>为什么单独抽成纯函数：这条判据曾经两边各写一份（开火 `focusish || aiming`、
 * HUD 只有 `aiming`）。蹲射时玩家不按右键，于是 HUD 画"枪口 → 准星"、子弹走被 ±25°
 * 扇区钳制的 `aimDir` —— 两边方向不同，屏幕上就是"指示器和实际弹道对不上"。
 *
 * <p>把它收敛到一处后，契约可以**穷举** `{专注, 右键, 界面}` 的全部组合，
 * 而不是靠字符串匹配源码来间接推断。
 */
public final class ShotBranchRules {
	/**
	 * 这一枪走"瞄准方向"（`aimDir`，受扇区钳制）还是"枪口 → 准星"（腰射）。
	 *
	 * @param focusish 专注镜像位（服务端下发）**或** 状态在 AIM* 里
	 * @param aimKeyHeld 右键是否按住（且无界面遮挡、瞄准允许）
	 * @return true = 瞄准分支
	 */
	public static boolean usesAimDirection(boolean focusish, boolean aimKeyHeld) {
		return focusish || aimKeyHeld;
	}

	private ShotBranchRules() {
	}
}
