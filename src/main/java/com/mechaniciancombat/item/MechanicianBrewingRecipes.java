package com.mechaniciancombat.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 由可视化配方编辑器生成的物品酿造配方。 */
public final class MechanicianBrewingRecipes {
	private static final String DATA_PACK_ID = "tkgun_live";
	private static final String RECIPE_NAMESPACE = "tkgun_custom";
	private static volatile Map<Item, Map<Item, ItemStack>> recipes = Map.of();

	public static void reload(MinecraftServer server) {
		Map<Item, Map<Item, ItemStack>> loaded = new HashMap<>();
		Map<ResourceLocation, Resource> builtIns = server.getResourceManager()
				.listResources("brewing", id -> RECIPE_NAMESPACE.equals(id.getNamespace())
						&& id.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, Resource> entry : builtIns.entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				load(JsonParser.parseReader(reader).getAsJsonObject(), loaded);
			} catch (Throwable error) {
				TkGun.LOGGER.warn("[tkgun] Invalid built-in brewing recipe {}",
						entry.getKey(), error);
			}
		}
		Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATA_PACK_ID);
		Path directory = root.resolve("data").resolve(RECIPE_NAMESPACE).resolve("brewing");
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
				for (Path file : files) {
					try {
						load(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
								.getAsJsonObject(), loaded);
					} catch (Throwable error) {
						TkGun.LOGGER.warn("[tkgun] Invalid brewing recipe {}", file, error);
					}
				}
			} catch (Throwable error) {
				TkGun.LOGGER.error("[tkgun] Failed to scan brewing recipes", error);
			}
		}
		recipes = loaded;
		TkGun.LOGGER.info("[tkgun] Loaded custom item brewing recipes: {}",
				loaded.values().stream().mapToInt(Map::size).sum());
	}

	private static void load(JsonObject json, Map<Item, Map<Item, ItemStack>> loaded) {
		Item input = item(json, "input_item");
		Item reagent = item(json, "reagent_item");
		Item output = item(json, "output_item");
		if (input == Items.AIR || reagent == Items.AIR || output == Items.AIR) {
			return;
		}
		int count = Math.max(1, Math.min(output.getMaxStackSize(),
				json.has("output_count") ? json.get("output_count").getAsInt() : 1));
		loaded.computeIfAbsent(input, ignored -> new HashMap<>())
				.put(reagent, new ItemStack(output, count));
	}

	public static ItemStack find(ItemStack input, ItemStack reagent) {
		if (input.isEmpty() || reagent.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Map<Item, ItemStack> reagents = recipes.get(input.getItem());
		if (reagents == null) {
			return ItemStack.EMPTY;
		}
		ItemStack output = reagents.get(reagent.getItem());
		return output == null ? ItemStack.EMPTY : output.copy();
	}

	public static boolean acceptsInput(ItemStack stack) {
		return !stack.isEmpty() && recipes.containsKey(stack.getItem());
	}

	public static boolean acceptsReagent(ItemStack input, ItemStack reagent) {
		return !find(input, reagent).isEmpty();
	}

	private static Item item(JsonObject json, String key) {
		if (!json.has(key)) {
			return Items.AIR;
		}
		ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
		return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
	}

	private MechanicianBrewingRecipes() {
	}
}
