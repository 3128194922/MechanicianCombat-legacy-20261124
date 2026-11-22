package com.mechaniciancombat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 三把枪的蹲射片段与动能剑/头盔动画名契约。
 *
 * <p>直接覆盖两类历史问题：蹲射时 rig 缺少 Focus_shooting* 导致短暂站立，
 * 以及头盔控制器与模型动画名不一致时头盔不动。
 */
public final class GunVariantAnimationContract {
	public static void main(String[] args) throws Exception {
		String[] common = {
				"Focus_ready", "Focus_ready_upper", "Focus_shooting", "Focus_shooting_extra",
				"Focus_shooting1", "Focus_shooting2", "Focus_shooting3", "Focus_shooting4",
				"Focus_shooting5", "Focus_shooting6", "Focus_shooting7", "Focus_hold",
				"stand_shooting", "Overheat", "reload"
		};
		for (String variant : new String[]{"bowgun", "abyssal_ancient_cannon", "veritas_et_justitia"}) {
			check("/assets/tkgun/animations/entity/" + variant + "_rig.animation.json", common);
			checkResource("/assets/tkgun/geo/entity/" + variant + "_rig.geo.json");
			checkCrouchPoseIsWholeBody("/assets/tkgun/animations/entity/" + variant + "_rig.animation.json");
		}
		checkGunPoseClips();
		check("/assets/tkgun/animations/entity/kinetic_sword_rig.animation.json",
				new String[]{"idle", "GS_run", "GS_walk_front", "jump", "fall",
						"GS_run_jump", "GS_run_fall",
						"GS_ATK_1", "GS_ATK_2", "GS_ATK_3", "GS_ATK_4", "GS_ATK_5", "GS_ATK_6",
						"GS_ATK_2-3_draft", "GS_ATK_4-5_draft",
						"GS_ATK_1_back-idle", "GS_ATK_6_back-idle",
						"chain_ready_sword", "chain_throw_sword"});
		checkBbmodel("/assets/tkgun/armor_models/helmet.bbmodel",
				new String[]{"\u5c55\u5f00", "\u6536\u8d77"});
		System.out.println("GunVariantAnimationContract: crouch, kinetic and helmet clips aligned");
	}

	/**
	 * `gun_pose` 控制器只对 `abyssal_ancient_cannon` / `veritas_et_justitia` 生效。
	 *
	 * <p>这两把枪的 `Focus_*` 只驱动躯干与四肢，枪身（`gun` / `barrel` / `case` / `blade_*`）
	 * 的展开与折叠由作者的 `open` / `close` 单独给出。缺了这条覆盖，架枪时枪件仍停在
	 * `idle` 的折叠姿态，与蹲下的身体错位，画面上就像"蹲射时人突然站起来一下"。
	 * `bowgun` 的动画资源里没有这两段，它自带完整枪件通道，所以必须**没有**。
	 */
	private static void checkGunPoseClips() throws Exception {
		String base = "/assets/tkgun/animations/entity/";
		for (String variant : new String[]{"abyssal_ancient_cannon", "veritas_et_justitia"}) {
			JsonObject animations = read(base + variant + "_rig.animation.json").getAsJsonObject("animations");
			for (String name : new String[]{"open", "close"}) {
				if (!animations.has(name)) {
					throw new AssertionError("missing gun-pose clip " + name + " in " + variant);
				}
				JsonObject bones = animations.getAsJsonObject(name).getAsJsonObject("bones");
				if (!bones.has("gun")) {
					throw new AssertionError("gun-pose clip " + name + " in " + variant
							+ " must drive the gun subtree");
				}
			}
		}
		JsonObject bowgun = read(base + "bowgun_rig.animation.json").getAsJsonObject("animations");
		for (String name : new String[]{"open", "close"}) {
			if (bowgun.has(name)) {
				throw new AssertionError("bowgun must not define " + name
						+ " (its Focus clips already drive the whole gun subtree)");
			}
		}
		// 代码侧：控制器必须真的注册，且被这两把枪的资源约束住。
		String source = readSource("src/main/java/com/mechaniciancombat/client/rig/BowgunRig.java");
		if (!source.contains("\"gun_pose\"")) {
			throw new AssertionError("BowgunRig must register the gun_pose controller");
		}
		// 门控收敛在 GunPoseRules 里（纯函数），BowgunRig 必须走它而不是自己再写一套判定。
		String rules = readSource("src/main/java/com/mechaniciancombat/client/rig/GunPoseRules.java");
		if (!rules.contains("hasGunPoseClips")) {
			throw new AssertionError("GunPoseRules must gate on hasGunPoseClips");
		}
		if (!source.contains("GunPoseRules.select(")) {
			throw new AssertionError("BowgunRig#pickGunPose must delegate to GunPoseRules.select");
		}
		checkGunPoseSelectionExhaustive();
		checkVeritasSkillSparkWired();
		checkEveryParticleKeyframeHandled();
	}

	/**
	 * **所有** rig 动画里的粒子关键帧都必须有处理器分支。
	 *
	 * <p>GeckoLib 对没有 handler 的粒子关键帧只打一行 warn，静默丢弃 ——
	 * 这类缺陷不会崩、不会报错，只会"特效莫名不出现"，靠肉眼很难发现。
	 * 这条契约把全部 rig 的 `particle_effects` 收集起来，逐个要求在
	 * `BowgunRig#onParticleKeyframe` 里找得到对应分支。
	 */
	private static void checkEveryParticleKeyframeHandled() throws Exception {
		java.util.Set<String> effects = new java.util.TreeSet<>();
		String[] rigs = {
				"/assets/tkgun/animations/entity/bowgun_rig.animation.json",
				"/assets/tkgun/animations/entity/abyssal_ancient_cannon_rig.animation.json",
				"/assets/tkgun/animations/entity/veritas_et_justitia_rig.animation.json"
		};
		for (String rig : rigs) {
			JsonObject animations = read(rig).getAsJsonObject("animations");
			for (var anim : animations.entrySet()) {
				com.google.gson.JsonElement clip = anim.getValue();
				if (!clip.isJsonObject() || !clip.getAsJsonObject().has("particle_effects")) {
					continue;
				}
				com.google.gson.JsonElement node = clip.getAsJsonObject().get("particle_effects");
				java.util.List<com.google.gson.JsonElement> items = new java.util.ArrayList<>();
				if (node.isJsonArray()) {
					node.getAsJsonArray().forEach(items::add);
				} else if (node.isJsonObject()) {
					node.getAsJsonObject().entrySet().forEach(e -> items.add(e.getValue()));
				}
				for (com.google.gson.JsonElement item : items) {
					if (!item.isJsonObject()) {
						continue;
					}
					com.google.gson.JsonElement effect = item.getAsJsonObject().get("effect");
					if (effect != null) {
						effects.add(effect.getAsString());
					}
				}
			}
		}
		if (effects.isEmpty()) {
			return;
		}
		String rig = readSource("src/main/java/com/mechaniciancombat/client/rig/BowgunRig.java");
		java.util.List<String> missing = new java.util.ArrayList<>();
		for (String effect : effects) {
			// 作者用 `particles` 这个通用名表示"散热/排汽"，代码里按枪种分流。
			String needle = "\"" + effect + "\".equals(effect)";
			if (!rig.contains(needle)) {
				missing.add(effect);
			}
		}
		if (!missing.isEmpty()) {
			throw new AssertionError("particle keyframes without a handler (GeckoLib silently drops them): "
					+ String.join(", ", missing));
		}
	}

	/**
	 * `veritas` 动画里埋的 `skill_spark` 粒子关键帧必须有**接收端**。
	 *
	 * <p>GeckoLib 对没有 handler 的粒子关键帧只打一行 warn，什么都不放 ——
	 * 表现就是"重劈/蓄力没有火花"。这条契约把"资源里的关键帧 ↔ 代码里的分支 ↔
	 * 粒子类型注册 ↔ 贴图"四段全串起来，任何一段断了都会失败。
	 */
	private static void checkVeritasSkillSparkWired() throws Exception {
		String path = "/assets/tkgun/animations/entity/veritas_et_justitia_rig.animation.json";
		JsonObject animations = read(path).getAsJsonObject("animations");
		int keyframes = 0;
		for (var anim : animations.entrySet()) {
			com.google.gson.JsonElement clip = anim.getValue();
			if (!clip.isJsonObject() || !clip.getAsJsonObject().has("particle_effects")) {
				continue;
			}
			// 作者的导出格式是**对象**：`{"0.5": {"effect":"...","locator":"..."}}`（键 = 秒）。
			// 兼容数组写法，避免以后换导出脚本时这条契约假失败。
			com.google.gson.JsonElement effects = clip.getAsJsonObject().get("particle_effects");
			java.util.List<com.google.gson.JsonElement> items = new java.util.ArrayList<>();
			if (effects.isJsonArray()) {
				effects.getAsJsonArray().forEach(items::add);
			} else if (effects.isJsonObject()) {
				effects.getAsJsonObject().entrySet().forEach(e -> items.add(e.getValue()));
			}
			for (com.google.gson.JsonElement item : items) {
				if (!item.isJsonObject()) {
					continue;
				}
				com.google.gson.JsonElement effect = item.getAsJsonObject().get("effect");
				if (effect != null && "skill_spark".equals(effect.getAsString())) {
					keyframes++;
				}
			}
		}
		if (keyframes == 0) {
			throw new AssertionError("veritas no longer authors skill_spark keyframes; "
					+ "drop the handler and this contract together");
		}
		// ① 处理器分支存在
		String rig = readSource("src/main/java/com/mechaniciancombat/client/rig/BowgunRig.java");
		if (!rig.contains("\"skill_spark\".equals(effect)")) {
			throw new AssertionError("BowgunRig has no skill_spark keyframe branch: "
					+ keyframes + " authored keyframes would emit nothing");
		}
		// ② 粒子类型已注册
		String registries = readSource("src/main/java/com/mechaniciancombat/registry/ModRegistries.java");
		if (!registries.contains("particle(\"skill_spark\")")) {
			throw new AssertionError("skill_spark particle type is not registered");
		}
		// ③ 客户端已注册贴图集
		String client = readSource("src/main/java/com/mechaniciancombat/client/TkGunClient.java");
		if (!client.contains("TkGunParticles.SKILL_SPARK")) {
			throw new AssertionError("skill_spark sprite set is not registered on the client");
		}
		// ④ 贴图与粒子定义齐备（5 帧）
		checkResource("/assets/tkgun/particles/skill_spark.json");
		for (int frame = 0; frame < 5; frame++) {
			checkResource("/assets/tkgun/textures/particle/skill_spark_" + frame + ".png");
		}
	}

	/**
	 * **穷举** `GunPoseRules.select` 的判定矩阵。
	 *
	 * <p>只有静态断言不够：它能证明"控制器注册了"，却证明不了"每种状态下选对了片段"。
	 * 这里把 `{3 把枪} × {全部状态} × {专注/非专注} × {举枪/不举枪} × {active/已结束}`
	 * 全部跑一遍，逐格断言期望值。任何一格选错（尤其是蹲射相关的那几格）都会失败。
	 */
	private static void checkGunPoseSelectionExhaustive() {
		String bowgun = "bowgun";
		String abyssal = "abyssal_ancient_cannon";
		String veritas = "veritas_et_justitia";
		String[] variants = {bowgun, abyssal, veritas};

		for (String variant : variants) {
			for (com.mechaniciancombat.TkGun.State state : com.mechaniciancombat.TkGun.State.values()) {
				for (boolean active : new boolean[]{true, false}) {
					for (boolean focus : new boolean[]{true, false}) {
						for (boolean hip : new boolean[]{true, false}) {
							// focusish 由状态与专注位共同决定（与服务端镜像一致）。
							boolean focusish = focus || isAimState(state);
							String actual = com.mechaniciancombat.client.rig.GunPoseRules
									.select(variant, state, active, focus, focusish, hip);
							String expected = expectedGunPose(variant, state, active, focus, focusish, hip);
							if (!java.util.Objects.equals(expected, actual)) {
								throw new AssertionError("gun_pose selection mismatch: variant=" + variant
										+ " state=" + state + " active=" + active + " focus=" + focus
										+ " focusish=" + focusish + " hipAiming=" + hip
										+ " expected=" + expected + " actual=" + actual);
							}
						}
					}
				}
			}
		}
		// bowgun 必须在任何组合下都不接管（它的 Focus_* 自带完整枪件通道）。
		for (com.mechaniciancombat.TkGun.State state : com.mechaniciancombat.TkGun.State.values()) {
			for (boolean active : new boolean[]{true, false}) {
				for (boolean focus : new boolean[]{true, false}) {
					for (boolean hip : new boolean[]{true, false}) {
						String actual = com.mechaniciancombat.client.rig.GunPoseRules
								.select(bowgun, state, active, focus, focus || isAimState(state), hip);
						if (actual != null) {
							throw new AssertionError("bowgun must never be overridden by gun_pose, but got "
									+ actual + " at state=" + state);
						}
					}
				}
			}
		}
	}

	/** 独立的期望值实现：故意与生产代码分开写，避免"复制一遍等于没验"。 */
	private static String expectedGunPose(String variant, com.mechaniciancombat.TkGun.State state,
										  boolean active, boolean focus, boolean focusish, boolean hip) {
		if (variant.equals("bowgun")) {
			return null;
		}
		if (active && state == com.mechaniciancombat.TkGun.State.FIRE) {
			return null;
		}
		if (variant.equals("abyssal_ancient_cannon") && state == com.mechaniciancombat.TkGun.State.AIM_HOLD) {
			return null;
		}
		if (variant.equals("veritas_et_justitia") && state == com.mechaniciancombat.TkGun.State.AIM_HOLD
				&& focus) {
			return "open";
		}
		boolean dodge = state == com.mechaniciancombat.TkGun.State.ROLL
				|| state == com.mechaniciancombat.TkGun.State.ROLL_TO_IDLE
				|| state == com.mechaniciancombat.TkGun.State.SLIDE
				|| state == com.mechaniciancombat.TkGun.State.SLIDE_READY;
		if (variant.equals("veritas_et_justitia") && dodge) {
			return "open";
		}
		if (!focusish && !hip && (state == com.mechaniciancombat.TkGun.State.IDLE || dodge)) {
			return "close";
		}
		return null;
	}

	private static boolean isAimState(com.mechaniciancombat.TkGun.State state) {
		return state == com.mechaniciancombat.TkGun.State.AIM
				|| state == com.mechaniciancombat.TkGun.State.AIM_FAST
				|| state == com.mechaniciancombat.TkGun.State.AIM_ROLL
				|| state == com.mechaniciancombat.TkGun.State.AIM_SLIDE
				|| state == com.mechaniciancombat.TkGun.State.AIM_HOLD;
	}

	private static String readSource(String path) throws Exception {
		java.nio.file.Path file = java.nio.file.Path.of(path);
		if (!java.nio.file.Files.exists(file)) {
			file = java.nio.file.Path.of("..").resolve(path);
		}
		return java.nio.file.Files.readString(file, StandardCharsets.UTF_8);
	}

	private static void check(String path, String[] required) throws Exception {
		JsonObject animations = read(path).getAsJsonObject("animations");
		for (String name : required) {
			if (!animations.has(name)) {
				throw new AssertionError("missing animation " + name + " in " + path);
			}
			JsonObject clip = animations.getAsJsonObject(name);
			if (!clip.has("bones") || clip.getAsJsonObject("bones").size() == 0) {
				throw new AssertionError("animation " + name + " has no bones in " + path);
			}
		}
	}

	private static void checkResource(String path) {
		if (GunVariantAnimationContract.class.getResource(path) == null) {
			throw new AssertionError("missing resource: " + path);
		}
	}

	/**
	 * 蹲射片段必须自己带 {@code body} + 双腿通道。
	 *
	 * <p>历史问题：专注射击状态只给 2 tick，动画没播完就掉进 `idle` 兜底；`idle` 的
	 * {@code body.rotation} 是站姿（没有 {@code body.position.y = -8} 的蹲姿），
	 * 于是三把枪里片段较短的 `abyssal_ancient_cannon` / `veritas_et_justitia`
	 * 会"开一枪瞬间站起来一下"。现在状态时长与 {@code FIRE_TICKS} 对齐，
	 * 这里再锁住"蹲射片段本身是全身动画"，两边一起防止回归。
	 */
	private static void checkCrouchPoseIsWholeBody(String path) throws Exception {
		JsonObject animations = read(path).getAsJsonObject("animations");
		for (String name : new String[]{"Focus_ready", "Focus_hold", "Focus_shooting",
				"Focus_shooting1", "Focus_shooting2", "Focus_shooting3", "Focus_shooting4",
				"Focus_shooting5", "Focus_shooting6", "Focus_shooting7", "Focus_shooting_extra"}) {
			if (!animations.has(name)) {
				throw new AssertionError("missing crouch clip " + name + " in " + path);
			}
			JsonObject bones = animations.getAsJsonObject(name).getAsJsonObject("bones");
			for (String bone : new String[]{"body", "leg_left", "leg_right"}) {
				if (!bones.has(bone)) {
					throw new AssertionError("crouch clip " + name + " in " + path
							+ " has no " + bone + " channel (would snap to standing pose)");
				}
			}
			JsonObject body = bones.getAsJsonObject("body");
			if (!body.has("position")) {
				throw new AssertionError("crouch clip " + name + " in " + path
						+ " has no body.position (crouch offset missing)");
			}
		}
	}

	private static void checkBbmodel(String path, String[] required) throws Exception {
		JsonObject document = read(path);
		java.util.Set<String> names = new java.util.HashSet<>();
		for (com.google.gson.JsonElement element : document.getAsJsonArray("animations")) {
			names.add(element.getAsJsonObject().get("name").getAsString());
		}
		for (String name : required) {
			if (!names.contains(name)) {
				throw new AssertionError("missing bbmodel animation " + name + " in " + path);
			}
		}
	}

	private static JsonObject read(String path) throws Exception {
		try (var stream = GunVariantAnimationContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private GunVariantAnimationContract() {
	}
}
