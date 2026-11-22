package com.mechaniciancombat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 配方编辑器：本地化键与核心类必须存在。 */
public final class RecipeEditorContract {
	public static void main(String[] args) throws Exception {
		for (String lang : new String[]{"zh_cn.json", "en_us.json"}) {
			JsonObject json = read("/assets/tkgun/lang/" + lang);
			for (String key : new String[]{
					"screen.tkgun.recipe_editor.title",
					"screen.tkgun.recipe_editor.id",
					"screen.tkgun.recipe_editor.save",
					"screen.tkgun.recipe_editor.delete",
					"screen.tkgun.recipe_editor.page.crafting",
					"screen.tkgun.recipe_editor.page.furnace",
					"screen.tkgun.recipe_editor.page.brewing",
					"message.tkgun.recipe_editor.saved",
					"message.tkgun.recipe_editor.deleted",
					"message.tkgun.recipe_editor.failed"}) {
				if (!json.has(key)) {
					throw new AssertionError("missing lang key " + key + " in " + lang);
				}
			}
		}
		for (String type : new String[]{
				"com.mechaniciancombat.screen.RecipeEditorScreenHandler",
				"com.mechaniciancombat.client.screen.RecipeEditorScreen",
				"com.mechaniciancombat.net.RecipeEditorActionPayload",
				"com.mechaniciancombat.item.MechanicianBrewingRecipes",
				"com.mechaniciancombat.mixin.MechanicianBrewingStandMixin"}) {
			Class.forName(type, false, RecipeEditorContract.class.getClassLoader());
		}
		System.out.println("RecipeEditorContract: editor screen, payload and brewing recipes aligned");
	}

	private static JsonObject read(String path) throws Exception {
		try (var stream = RecipeEditorContract.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("missing resource: " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private RecipeEditorContract() {
	}
}
