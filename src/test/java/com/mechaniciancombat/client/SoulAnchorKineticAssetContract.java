package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 灵魂锚 / 动能剑资源契约：几何、动画、贴图、物品模型、音效必须齐全。
 *
 * <p>缺段不会崩，但会表现为“第一人称不显示 / 动画不动 / 锁链没有贴图”。
 */
public final class SoulAnchorKineticAssetContract {
	public static void main(String[] args) throws Exception {
		checkAnimation("/assets/tkgun/animations/entity/kinetic_sword_rig.animation.json",
				null,
				new String[]{"idle", "GS_run", "GS_walk_front", "GS_walk_back",
						"GS_walk_left", "GS_walk_right", "jump", "fall",
						"roll", "roll_to_idle",
						"GS_ATK_1", "GS_ATK_2", "GS_ATK_3", "GS_ATK_4", "GS_ATK_5", "GS_ATK_6",
						"GS_ATK_1_back-idle", "GS_ATK_2_back-idle", "GS_ATK_3_back-idle",
						"GS_ATK_4_back-idle", "GS_ATK_5_back-idle", "GS_ATK_6_back-idle",
						"chain_ready_sword", "chain_throw_sword", "chain_drag_sword",
						"chain_drag_sword_fall"});
		// GS_walk_* 片段里保留了枪械的 fire/shells 通道（GeckoLib 会忽略不存在的骨骼），
		// 所以这里单独检查动能剑真正要用的骨骼。
		Set<String> kineticBones = bones("/assets/tkgun/geo/entity/kinetic_sword_rig.geo.json");
		for (String bone : new String[]{"body", "chest", "head", "arm_left", "arm_right",
				"leg_left", "leg_right", "Great_sword", "Blade", "energy_effect"}) {
			if (!kineticBones.contains(bone)) {
				throw new AssertionError("kinetic sword geometry missing bone " + bone);
			}
		}
		checkAnimation("/assets/tkgun/animations/entity/soul_anchor_fpa.animation.json",
				"/assets/tkgun/geo/entity/soul_anchor_fpa.geo.json",
				new String[]{"ready", "throw"});
		checkAnimation("/assets/tkgun/animations/entity/soul_anchor_projectile.animation.json",
				"/assets/tkgun/geo/entity/soul_anchor_projectile.geo.json",
				new String[0]);
		// 空手/持枪使用灵魂锚时，身体姿势由当前 rig 的 chain_* 片段驱动。
		checkAnimation("/assets/tkgun/animations/entity/bowgun_rig.animation.json", null,
				new String[]{"chain_ready", "chain_ready_gun_upper", "chain_throw",
						"chain_throw_gun_upper", "chain_drag", "chain_drag_entity",
						"chain_sliding_upper", "chain_sliding_to_idle_upper",
						"chain_sliding_to_run_upper"});

		for (String path : new String[]{
				"/assets/tkgun/textures/entity/kinetic_sword_sword.png",
				"/assets/tkgun/textures/entity/kinetic_sword_energy_effect.png",
				"/assets/tkgun/textures/entity/soul_anchor_fpa.png",
				"/assets/tkgun/textures/entity/connect_chain.png",
				"/assets/tkgun/textures/entity/soul_anchor_flight_0.png",
				"/assets/tkgun/textures/entity/soul_anchor_hit_0.png",
				"/assets/tkgun/textures/entity/soul_anchor_3_0.png",
				"/assets/tkgun/textures/item/kinetic_sword.png",
				"/assets/tkgun/textures/item/soul_anchor_projectile.png",
				"/assets/tkgun/models/item/kinetic_sword.json",
				"/assets/tkgun/models/item/kinetic_sword_fpa_model.json",
				"/assets/tkgun/models/item/soul_anchor_fpa_model.json",
				"/assets/tkgun/models/item/soul_anchor_projectile_model.json",
				"/assets/tkgun/sounds/chain.ogg",
				"/assets/tkgun/sounds/great_sword_slash.ogg"}) {
			if (SoulAnchorKineticAssetContract.class.getResource(path) == null) {
				throw new AssertionError("missing resource: " + path);
			}
		}
		System.out.println("SoulAnchorKineticAssetContract: anchor and kinetic sword assets aligned");
		checkNewEntityVertexFormatComplete();
	}

	/**
	 * **`NEW_ENTITY` 顶点格式必须填满**，否则渲染线程直接崩。
	 *
	 * <p>历史缺陷（2026-11-22 实测崩溃）：`AnchorLockParticleRenderer` 用
	 * `RenderType.entityCutoutNoCull`（= `DefaultVertexFormat.NEW_ENTITY`），
	 * 但顶点只填了 position/color/uv/light，缺 overlay 与 normal，
	 * `BufferBuilder#endVertex` 抛
	 * `IllegalStateException: Not filled all elements of the vertex` ——
	 * 灵魂锚一进 READY 就崩整个客户端。
	 *
	 * <p>这条契约把"用 NEW_ENTITY 的地方必须写全 6 个元素"钉死。
	 */
	private static void checkNewEntityVertexFormatComplete() {
		java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/mechaniciancombat");
		if (!java.nio.file.Files.isDirectory(root)) {
			return;
		}
		// 这些 RenderType 都用 `DefaultVertexFormat.NEW_ENTITY`
		// （POSITION / COLOR / UV0 / UV1(overlay) / UV2(light) / NORMAL）。
		String[] newEntityLayers = {
				"RenderType.entityCutoutNoCull", "RenderType.entitySolid",
				"RenderType.entityTranslucent", "RenderType.entityTranslucentEmissive",
				"RenderType.entityCutout", "RenderType.armorCutoutNoCull"};
		java.util.List<String> problems = new java.util.ArrayList<>();
		try (var stream = java.nio.file.Files.walk(root)) {
			for (java.nio.file.Path path : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
				String text = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
				String code = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
				if (!code.contains("endVertex()")) {
					continue;                 // 不手工建顶点，不关这条
				}
				boolean usesNewEntity = false;
				for (String layer : newEntityLayers) {
					if (code.contains(layer)) {
						usesNewEntity = true;
						break;
					}
				}
				if (!usesNewEntity) {
					continue;                 // 粒子/文本等其它格式不需要 overlay/normal
				}
				for (String element : new String[]{".overlayCoords(", ".normal("}) {
					if (!code.contains(element)) {
						problems.add(path.getFileName() + " 缺 " + element);
					}
				}
			}
		} catch (java.io.IOException error) {
			throw new AssertionError("cannot scan " + root, error);
		}
		if (!problems.isEmpty()) {
			throw new AssertionError("NEW_ENTITY vertex is incomplete "
					+ "(BufferBuilder#endVertex throws 'Not filled all elements of the vertex' "
					+ "and crashes the render thread): " + String.join("; ", problems));
		}
	}

	private static void checkAnimation(String animationPath, String geoPath, String[] required)
			throws Exception {
		JsonObject animations = read(animationPath).getAsJsonObject("animations");
		Set<String> bones = new HashSet<>();
		if (geoPath != null) {
			JsonObject geometry = read(geoPath).getAsJsonArray("minecraft:geometry")
					.get(0).getAsJsonObject();
			for (JsonElement bone : geometry.getAsJsonArray("bones")) {
				bones.add(bone.getAsJsonObject().get("name").getAsString());
			}
		}
		for (String name : required) {
			if (!animations.has(name)) {
				throw new AssertionError("missing animation: " + name + " in " + animationPath);
			}
			if (geoPath == null) {
				continue;
			}
			JsonObject clipBones = animations.getAsJsonObject(name).getAsJsonObject("bones");
			if (clipBones == null) {
				continue;
			}
			for (String bone : clipBones.keySet()) {
				if (!bones.contains(bone)) {
					throw new AssertionError(name + " references unknown bone " + bone
							+ " in " + animationPath);
				}
			}
		}
	}

	private static JsonObject read(String path) throws Exception {
		try (var stream = SoulAnchorKineticAssetContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private static Set<String> bones(String geoPath) throws Exception {
		JsonObject geometry = read(geoPath).getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject();
		Set<String> bones = new HashSet<>();
		for (JsonElement bone : geometry.getAsJsonArray("bones")) {
			bones.add(bone.getAsJsonObject().get("name").getAsString());
		}
		return bones;
	}

	private SoulAnchorKineticAssetContract() {
	}
}
