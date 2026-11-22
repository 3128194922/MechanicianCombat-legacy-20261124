package com.mechaniciancombat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 护甲改装台与护甲模块资源契约。 */
public final class ArmorModificationAssetContract {
	public static void main(String[] args) throws Exception {
		for (String path : new String[]{
				"/assets/tkgun/blockstates/armor_modification_table.json",
				"/assets/tkgun/models/block/armor_modification_table.json",
				"/assets/tkgun/models/item/armor_modification_table.json",
				"/assets/tkgun/textures/block/armor_modification_table.png",
				"/assets/tkgun/textures/gui/container/armor_modification_table.png",
				"/assets/tkgun/textures/gui/gui_chest.png",
				"/assets/tkgun/models/item/undying_totem.json",
				"/assets/tkgun/models/item/pauldorn_left.json",
				"/assets/tkgun/models/item/soul_chain.json",
				"/assets/tkgun/textures/item/undying_totem.png",
				"/assets/tkgun/textures/item/pauldorn_left.png",
				"/assets/tkgun/textures/item/soul_chain.png",
				"/assets/tkgun/textures/item/soul_anchor.png",
				"/assets/tkgun/models/item/golem_dispatch_capsule.json",
				"/assets/tkgun/textures/item/golem_dispatch_capsule.png",
				"/data/tkgun/loot_tables/blocks/armor_modification_table.json"}) {
			if (ArmorModificationAssetContract.class.getResource(path) == null) {
				throw new AssertionError("missing resource: " + path);
			}
		}
		JsonObject blockstate = read("/assets/tkgun/blockstates/armor_modification_table.json");
		if (!blockstate.has("variants")) {
			throw new AssertionError("armor table blockstate has no variants");
		}
		String text = blockstate.toString();
		if (!text.contains("tkgun:block/armor_modification_table")) {
			throw new AssertionError("armor table blockstate does not reference its model");
		}
		JsonObject itemModel = read("/assets/tkgun/models/item/armor_modification_table.json");
		if (!itemModel.has("parent")) {
			throw new AssertionError("armor table item model has no parent");
		}
		for (String type : new String[]{
				"com.mechaniciancombat.ArmorAbilities",
				"com.mechaniciancombat.SoulChainAbility",
				"com.mechaniciancombat.item.ArmorPartItem",
				"com.mechaniciancombat.item.ArmorUpgradeData",
				"com.mechaniciancombat.net.ArmorAbilityPayload",
				"com.mechaniciancombat.net.SoulChainPayload"}) {
			Class.forName(type, false, ArmorModificationAssetContract.class.getClassLoader());
		}
		java.util.Set<String> chestplateBones = boneNames(
				"/assets/tkgun/armor_models/chestplate.bbmodel");
		for (String bone : new String[]{"soul_chain", "pauldorn_left", "undying_totem",
				"\u7075\u9b42\u4e4b\u951a"}) {
			if (!chestplateBones.contains(bone)) {
				throw new AssertionError("chestplate model missing module bone " + bone);
			}
		}
		for (String path : new String[]{
				"/assets/tkgun/armor_models/helmet.bbmodel",
				"/assets/tkgun/textures/entity/mechanician_chestplate.png",
				"/assets/tkgun/textures/entity/mechanician_leggings.png",
				"/assets/tkgun/textures/entity/mechanician_boots.png",
				"/assets/tkgun/textures/entity/mechanician_helmet.png",
				"/assets/tkgun/textures/entity/mechanician_undersuit.png",
				"/assets/tkgun/textures/entity/soul_anchor_armor.png"}) {
			if (ArmorModificationAssetContract.class.getResource(path) == null) {
				throw new AssertionError("missing resource: " + path);
			}
		}
		System.out.println("ArmorModificationAssetContract: armor table and modules aligned");
	}

	private static java.util.Set<String> boneNames(String path) throws Exception {
		JsonObject document = read(path);
		java.util.Set<String> names = new java.util.HashSet<>();
		collectBones(document.getAsJsonArray("outliner"), names);
		return names;
	}

	private static void collectBones(com.google.gson.JsonArray array,
									 java.util.Set<String> names) {
		if (array == null) {
			return;
		}
		for (com.google.gson.JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject bone = element.getAsJsonObject();
			if (bone.has("name")) {
				names.add(bone.get("name").getAsString());
			}
			if (bone.has("children")) {
				for (com.google.gson.JsonElement child : bone.getAsJsonArray("children")) {
					if (child.isJsonObject()) {
						com.google.gson.JsonArray single = new com.google.gson.JsonArray();
						single.add(child);
						collectBones(single, names);
					}
				}
			}
		}
	}

	private static JsonObject read(String path) throws Exception {
		try (var stream = ArmorModificationAssetContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private ArmorModificationAssetContract() {
	}
}
