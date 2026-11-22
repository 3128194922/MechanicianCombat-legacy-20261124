package com.mechaniciancombat.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 第一人称道具必须**有门控**：不能在只是"背包里装着某个模块"时就常驻显示。
 *
 * <p>历史缺陷：炮台第一人称道具最初没有展开判定，玩家只要装了便携/自动炮台模块，
 * 第一人称就会一直举着一门炮 —— 直到核对上游 `turretActivated` 才发现。
 *
 * <p>本契约对源码做结构性断言（剥掉注释后匹配），只在开发树存在源文件时执行。
 */
public final class FirstPersonPropGatingContract {
	public static void main(String[] args) throws Exception {
		requireCall("src/main/java/com/mechaniciancombat/client/CombatPropFirstPersonRenderer.java",
				"renderTurret", "turretActivated",
				"turret first-person prop must be gated by turretActivated()");
		requireCall("src/main/java/com/mechaniciancombat/client/WarBannerFirstPersonMixin.java",
				"tkgun$renderWarBannerFpa", "WarBannerSkillClientState.isActive",
				"war banner first-person must be gated by skill activity");
		requireCall("src/main/java/com/mechaniciancombat/mixin/CombatPropFirstPersonMixin.java",
				"tkgun$renderHunterAnchorFpa", "HunterSpearSkillClientState.isActive",
				"hunter spear first-person must be gated by deploy activity");
		requireCall("src/main/java/com/mechaniciancombat/mixin/SoulAnchorFirstPersonMixin.java",
				"tkgun$renderSoulAnchorFpa", "firstPersonActive",
				"soul anchor first-person must be gated by skill phase");
		requireCall("src/main/java/com/mechaniciancombat/mixin/KineticSwordFirstPersonMixin.java",
				"tkgun$renderKineticSwordFpa", "isKineticSword",
				"kinetic sword first-person must be gated by the held item");
		System.out.println("FirstPersonPropGatingContract: every first-person prop is gated");
	}

	private static void requireCall(String relativePath, String method, String gate, String message)
			throws Exception {
		Path path = Path.of(relativePath);
		if (!Files.isRegularFile(path)) {
			return;      // 发布产物里没有源码，跳过
		}
		String text = stripComments(Files.readString(path, StandardCharsets.UTF_8));
		if (!text.contains(method)) {
			throw new AssertionError("method " + method + " not found in " + relativePath);
		}
		if (!text.contains(gate)) {
			throw new AssertionError(message + " (missing " + gate + " in " + relativePath + ")");
		}
	}

	private static String stripComments(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
	}

	private FirstPersonPropGatingContract() {
	}
}
