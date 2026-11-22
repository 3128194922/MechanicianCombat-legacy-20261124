package com.mechaniciancombat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 注册项与资源必须一一对应：缺语言条目会显示原始键名，缺模型会显示紫黑格。
 *
 * <p>历史缺陷：`HUNTER_SPEAR` 注册 id 是 `hunter_spear`，但语言条目写成了
 * `item.tkgun.mechanician_hunter_spear`，物品在游戏里直接显示键名。
 *
 * <p>本契约读取 `ModRegistries` 源码里的注册 id，逐项核对
 * `models/item/*.json`、`lang/zh_cn.json`、`lang/en_us.json`。
 * 只在开发树存在源码时执行。
 */
public final class RegistryAssetContract {
	private static final Set<String> HIDDEN = Set.of(
			// 纯运行时控制器物品：不给玩家看，也不需要语言条目。
			"soul_anchor_fpa_model", "kinetic_sword_fpa_model", "war_banner_fpa_model",
			"hunter_anchor_fpa_model", "portable_turret_fpa_model", "auto_turret_fpa_model",
			"soul_anchor_projectile_model", "tab_icon");

	public static void main(String[] args) throws Exception {
		Path registry = Path.of("src/main/java/com/mechaniciancombat/registry/ModRegistries.java");
		if (!Files.isRegularFile(registry)) {
			return;
		}
		String source = Files.readString(registry, StandardCharsets.UTF_8);
		Set<String> itemIds = collect(source, "ITEMS.register(");
		Set<String> blockIds = collect(source, "BLOCKS.register(");
		if (itemIds.isEmpty()) {
			throw new AssertionError("no ITEMS.register found in ModRegistries");
		}
		JsonObject zh = readLang("/assets/tkgun/lang/zh_cn.json");
		JsonObject en = readLang("/assets/tkgun/lang/en_us.json");

		for (String id : itemIds) {
			if (HIDDEN.contains(id)) {
				continue;
			}
			requireModel(id);
			String key = blockIds.contains(id) ? "block.tkgun." + id : "item.tkgun." + id;
			if (!zh.has(key) || !en.has(key)) {
				throw new AssertionError("registered item " + id + " has no translation key "
						+ key + " (zh=" + zh.has(key) + ", en=" + en.has(key) + ")");
			}
		}
		for (String id : blockIds) {
			for (String rel : new String[]{"blockstates/" + id + ".json",
					"models/block/" + id + ".json"}) {
				if (RegistryAssetContract.class.getResource("/assets/tkgun/" + rel) == null) {
					throw new AssertionError("block " + id + " missing asset " + rel);
				}
			}
		}
		System.out.println("RegistryAssetContract: " + itemIds.size() + " items, "
				+ blockIds.size() + " blocks — models and translations aligned");
		checkSoundAssets();
		checkMenuScreensRegistered();
	}

	/**
	 * `sounds.json` 里每个事件引用的 `.ogg` 必须真的存在。
	 *
	 * <p>缺文件时 MC 不会崩，只会**静音** —— 枪声/换弹声凭空消失，日志里只有一行
	 * "Unable to play unknown soundEvent"。这类缺陷靠试听极难定位。
	 */
	private static void checkSoundAssets() throws Exception {
		JsonObject sounds = readLang("/assets/tkgun/sounds.json");
		java.util.List<String> missing = new java.util.ArrayList<>();
		int files = 0;
		for (var event : sounds.entrySet()) {
			com.google.gson.JsonElement list = event.getValue().getAsJsonObject().get("sounds");
			if (list == null || !list.isJsonArray()) {
				missing.add(event.getKey() + " (no sounds array)");
				continue;
			}
			for (com.google.gson.JsonElement item : list.getAsJsonArray()) {
				String name = item.isJsonPrimitive()
						? item.getAsString()
						: item.getAsJsonObject().get("name").getAsString();
				name = name.replaceFirst("^tkgun:", "");
				files++;
				if (RegistryAssetContract.class.getResource(
						"/assets/tkgun/sounds/" + name + ".ogg") == null) {
					missing.add(event.getKey() + " -> " + name + ".ogg");
				}
			}
		}
		if (!missing.isEmpty()) {
			throw new AssertionError("sound events referencing missing ogg files: "
					+ String.join(", ", missing));
		}
		if (files == 0) {
			throw new AssertionError("sounds.json declares no sound files at all");
		}
	}

	/**
	 * 每个 `MENUS.register(...)` 的菜单类型都必须在客户端有 `MenuScreens.register`。
	 *
	 * <p>缺注册时右键方块**什么都不发生**（`openScreen` 走完，客户端找不到 Screen 工厂）。
	 */
	private static void checkMenuScreensRegistered() throws Exception {
		Path registry = Path.of("src/main/java/com/mechaniciancombat/registry/ModRegistries.java");
		Path client = Path.of("src/main/java/com/mechaniciancombat/client/TkGunClient.java");
		if (!Files.isRegularFile(registry) || !Files.isRegularFile(client)) {
			return;
		}
		String registrySource = Files.readString(registry, StandardCharsets.UTF_8);
		java.util.Set<String> menuFields = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(\\w+)\\s*=\\s*MENUS\\.register\\(").matcher(registrySource);
		while (m.find()) {
			menuFields.add(m.group(1));
		}
		if (menuFields.isEmpty()) {
			throw new AssertionError("no MENUS.register found in ModRegistries");
		}
		String clientSource = Files.readString(client, StandardCharsets.UTF_8);
		// 客户端的写法是 `MenuScreens.register(<X>ScreenHandler.TYPE, ...)`，
		// 而每个 `*ScreenHandler.TYPE` 又在服务端初始化时被赋成对应的菜单注册项。
		// 所以要核对的是"菜单项 → ScreenHandler 类 → 客户端注册"这条链，
		// 而不是要求客户端直接写出 `ModRegistries.<FIELD>`。
		java.util.List<String> missing = new java.util.ArrayList<>();
		for (String field : menuFields) {
			// 找出 `com.mechaniciancombat.screen.<X>ScreenHandler.TYPE = <field>;` 的赋值。
			// 赋值语句跨行且末尾带 `.get();`：
			//   X.TYPE =
			//       com...ModRegistries.<FIELD>.get();
			java.util.regex.Pattern assign = java.util.regex.Pattern.compile(
					"([\\w.]*?(\\w+ScreenHandler))\\.TYPE\\s*=\\s*(?:\\r?\\n\\s*)?"
							+ "(?:[\\w.]*\\.)?" + java.util.regex.Pattern.quote(field)
							+ "\\s*\\.\\s*get\\s*\\(\\s*\\)\\s*;");
			java.util.regex.Matcher a = assign.matcher(registrySource + "\n" + readText(Path.of(
					"src/main/java/com/mechaniciancombat/TkGunServer.java")));
			String handler = null;
			while (a.find()) {
				handler = a.group(2);
			}
			if (handler == null) {
				missing.add(field + " (no ScreenHandler.TYPE assignment)");
				continue;
			}
			if (!clientSource.contains(handler + ".TYPE")) {
				missing.add(field + " -> " + handler + " (client never registers its TYPE)");
			}
		}
		if (!missing.isEmpty()) {
			throw new AssertionError("menu types with no client screen registration "
					+ "(right-clicking the block would do nothing): " + String.join(", ", missing));
		}
	}

	private static Set<String> collect(String source, String needle) {
		java.util.Set<String> ids = new java.util.TreeSet<>();
		int index = 0;
		while ((index = source.indexOf(needle, index)) >= 0) {
			index += needle.length();
			int cursor = index;
			while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
				cursor++;
			}
			if (cursor >= source.length() || source.charAt(cursor) != '"') {
				continue;
			}
			int end = source.indexOf('"', cursor + 1);
			if (end > cursor) {
				ids.add(source.substring(cursor + 1, end));
				index = end;
			}
		}
		return ids;
	}

	private static void requireModel(String id) {
		String path = "/assets/tkgun/models/item/" + id + ".json";
		if (RegistryAssetContract.class.getResource(path) == null) {
			throw new AssertionError("registered item " + id + " has no item model (" + path + ")");
		}
	}

	private static JsonObject readLang(String path) throws Exception {
		try (var stream = RegistryAssetContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private static String readText(Path path) throws Exception {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private RegistryAssetContract() {
	}
}
