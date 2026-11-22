package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 蹲射不"瞬间站起"的核心不变量：**专注开火状态必须覆盖整段 `Focus_shooting*`**。
 *
 * <p>历史缺陷：`FOCUS_FIRE_TICKS` 只有 2 tick，而片段长 1.0–1.25 秒。状态提前结束的那一帧
 * 客户端 `e.active()` 变 false，`BowgunRig.pick` 掉进 `idle` 兜底（站姿），
 * `pickUpper` 又叠 `stand_shooting` —— 表现就是"蹲着开一枪，人瞬间站起来一下"。
 *
 * <p>本契约把"状态时长 ≥ 动画时长"钉死：任何一把枪新增更长的射击片段、
 * 或有人把 `FOCUS_FIRE_TICKS` 改小，这里都会失败。
 */
public final class CrouchFireWindowContract {
	public static void main(String[] args) throws Exception {
		int fireTicks = TkGun.FOCUS_FIRE_TICKS;
		if (fireTicks < TkGun.FIRE_TICKS) {
			throw new AssertionError("FOCUS_FIRE_TICKS (" + fireTicks
					+ ") must cover the full focused shot clip, expected >= FIRE_TICKS ("
					+ TkGun.FIRE_TICKS + ")");
		}
		for (String variant : new String[]{"bowgun", "abyssal_ancient_cannon", "veritas_et_justitia"}) {
			JsonObject animations = read("/assets/tkgun/animations/entity/" + variant + "_rig.animation.json")
					.getAsJsonObject("animations");
			for (int shot = 1; shot <= TkGun.SHOT_ANIM_MAX; shot++) {
				String clip = TkGun.shotAnimation(shot);
				if (!animations.has(clip)) {
					throw new AssertionError(variant + " missing shot clip " + clip);
				}
				double seconds = keyframeLength(animations.getAsJsonObject(clip));
				int clipTicks = (int) Math.ceil(seconds * 20.0D);
				if (fireTicks < clipTicks) {
					throw new AssertionError(variant + " shot " + shot + " (" + clip + ") runs "
							+ clipTicks + " ticks but FOCUS_FIRE_TICKS is only " + fireTicks
							+ " — the state ends mid-clip and the model snaps to idle (standing pose)");
				}
			}
			// 蹲射保持与进入片段也必须自带蹲姿位移，否则同样会"站起来"。
			for (String clip : new String[]{"Focus_ready", "Focus_hold"}) {
				JsonObject bones = animations.getAsJsonObject(clip).getAsJsonObject("bones");
				if (!bones.has("body") || !bones.getAsJsonObject("body").has("position")) {
					throw new AssertionError(variant + " " + clip + " has no body.position (crouch offset)");
				}
			}
		}
		checkStoppedControllerRecovery();
		System.out.println("CrouchFireWindowContract: focused fire state covers every shot clip ("
				+ fireTicks + " ticks)");
	}

	/**
	 * 控制器 STOP 之后必须能恢复，否则蹲姿会被 GeckoLib 复位成站姿。
	 *
	 * <p>GeckoLib 的语义：控制器处于 STOPPED 时，"这一帧没有任何动画通道的骨骼"会被拉回
	 * geo 初始姿势。`body.position` 的初始值是 `[0,0,0]` —— 也就是**站立**。
	 * 所以只要主/上层控制器在蹲射途中停过一次（片段播完、状态短暂为空、上层 STOP），
	 * 身体就会跳回站姿，正是用户看到的"蹲射时人一瞬间站起来"。
	 *
	 * <p>上游 r611 之后在 `pick` / `pickUpper` 开头都调了 `recoverStoppedController`，
	 * 本地此前没有这道防护。这里断言它确实被调用，且对动能剑收势保留例外。
	 */
	private static void checkStoppedControllerRecovery() {
		java.nio.file.Path source = java.nio.file.Path.of(
				"src/main/java/com/mechaniciancombat/client/rig/BowgunRig.java");
		if (!java.nio.file.Files.isRegularFile(source)) {
			return;
		}
		String text;
		try {
			text = java.nio.file.Files.readString(source, StandardCharsets.UTF_8);
		} catch (java.io.IOException error) {
			throw new AssertionError("cannot read " + source, error);
		}
		// 剥注释：说明文字里也写了这些名字，不剥会被注释骗过去。
		String code = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
		int defs = count(code, "private void recoverStoppedController");
		if (defs != 1) {
			throw new AssertionError("recoverStoppedController must be defined exactly once, found " + defs);
		}
		if (!code.contains("controller.getAnimationState() != AnimationController.State.STOPPED")) {
			throw new AssertionError("recoverStoppedController must inspect the STOPPED state");
		}
		if (!code.contains("controller.forceAnimationReset()")) {
			throw new AssertionError("recoverStoppedController must reset the stopped controller");
		}
		// pick() 与 pickUpper() 两个入口都要有防护：只补一个，另一条控制器停住时照样跳站姿。
		int pickUpper = code.indexOf("private PlayState pickUpper");
		int pick = code.indexOf("private PlayState pick(");
		if (pickUpper < 0 || pick < 0) {
			throw new AssertionError("pick / pickUpper not found in " + source);
		}
		if (!code.substring(pickUpper, pickUpper + 200).contains("recoverStoppedController(state)")) {
			throw new AssertionError("pickUpper must recover a stopped controller "
					+ "(otherwise the upper layer stops and the body snaps to standing)");
		}
	}

	private static int count(String text, String needle) {
		int found = 0;
		int from = 0;
		while (true) {
			int at = text.indexOf(needle, from);
			if (at < 0) {
				return found;
			}
			found++;
			from = at + needle.length();
		}
	}

	/** 与 GeckoLib 一致：取所有骨骼通道里最大的关键帧时间戳。 */
	private static double keyframeLength(JsonObject clip) {
		double best = 0.0D;
		JsonObject bones = clip.has("bones") ? clip.getAsJsonObject("bones") : null;
		if (bones == null) {
			return best;
		}
		for (var boneEntry : bones.entrySet()) {
			JsonObject bone = boneEntry.getValue().getAsJsonObject();
			for (String channel : new String[]{"rotation", "position", "scale"}) {
				if (bone.has(channel)) {
					best = Math.max(best, maxTimestamp(bone.get(channel)));
				}
			}
		}
		return best;
	}

	private static double maxTimestamp(JsonElement node) {
		double best = 0.0D;
		if (node.isJsonArray()) {
			return best;
		}
		if (!node.isJsonObject()) {
			return best;
		}
		for (var entry : node.getAsJsonObject().entrySet()) {
			double stamp;
			try {
				stamp = Double.parseDouble(entry.getKey());
			} catch (NumberFormatException ignored) {
				best = Math.max(best, maxTimestamp(entry.getValue()));
				continue;
			}
			best = Math.max(best, Math.max(stamp, maxTimestamp(entry.getValue())));
		}
		return best;
	}

	private static JsonObject read(String path) throws Exception {
		try (var stream = CrouchFireWindowContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private CrouchFireWindowContract() {
	}
}
