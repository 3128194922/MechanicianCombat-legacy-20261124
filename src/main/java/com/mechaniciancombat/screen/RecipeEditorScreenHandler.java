package com.mechaniciancombat.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.MechanicianBrewingRecipes;
import com.mechaniciancombat.net.RecipeEditorActionPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 可视化合成 / 熔炉 / 酿造配方编辑器。 */
public final class RecipeEditorScreenHandler extends AbstractContainerMenu {
	public static final int PAGE_CRAFTING = 0;
	public static final int PAGE_FURNACE = 1;
	public static final int PAGE_BREWING = 2;
	public static final int PAGE_COUNT = 3;

	public static final int GRID_SIZE = 9;
	public static final int CRAFTING_RESULT_SLOT = 9;
	public static final int FURNACE_INPUT_SLOT = 10;
	public static final int FURNACE_RESULT_SLOT = 11;
	public static final int BREWING_INPUT_SLOT = 12;
	public static final int BREWING_REAGENT_SLOT = 13;
	public static final int BREWING_RESULT_SLOT = 14;
	public static final int EDITOR_SLOT_COUNT = 15;
	public static final int PLAYER_SLOT_START = EDITOR_SLOT_COUNT;
	public static final int HOTBAR_SLOT_START = PLAYER_SLOT_START + 27;
	public static final int GRID_X = 30;
	public static final int GRID_Y = 17;
	public static final int RESULT_X = 124;
	public static final int RESULT_Y = 35;
	public static final int FURNACE_INPUT_X = 56;
	public static final int FURNACE_INPUT_Y = 17;
	public static final int FURNACE_RESULT_X = 116;
	public static final int FURNACE_RESULT_Y = 35;
	public static final int BREWING_INPUT_X = 79;
	public static final int BREWING_INPUT_Y = 17;
	public static final int BREWING_REAGENT_X = 56;
	public static final int BREWING_REAGENT_Y = 51;
	public static final int BREWING_RESULT_X = 102;
	public static final int BREWING_RESULT_Y = 51;
	public static final int PLAYER_X = 8;
	public static final int PLAYER_Y = 84;
	public static final int HOTBAR_Y = 142;

	private static final String DATA_PACK_ID = "tkgun_live";
	private static final String RECIPE_NAMESPACE = "tkgun_custom";
	private static final int DATA_PACK_FORMAT = 15;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static MenuType<RecipeEditorScreenHandler> TYPE;

	private final Player owner;
	private final Container ingredients = new SimpleContainer(GRID_SIZE);
	private final Container result = new SimpleContainer(1);
	private final Container furnaceInput = new SimpleContainer(1);
	private final Container furnaceResult = new SimpleContainer(1);
	private final Container brewingInput = new SimpleContainer(1);
	private final Container brewingReagent = new SimpleContainer(1);
	private final Container brewingResult = new SimpleContainer(1);
	private int page = PAGE_CRAFTING;

	public RecipeEditorScreenHandler(int containerId, Inventory playerInventory) {
		super(TYPE, containerId);
		this.owner = playerInventory.player;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				addSlot(new PageSlot(ingredients, column + row * 3,
						GRID_X + column * 18, GRID_Y + row * 18, PAGE_CRAFTING));
			}
		}
		addSlot(new PageSlot(result, 0, RESULT_X, RESULT_Y, PAGE_CRAFTING));
		addSlot(new PageSlot(furnaceInput, 0, FURNACE_INPUT_X, FURNACE_INPUT_Y, PAGE_FURNACE));
		addSlot(new PageSlot(furnaceResult, 0, FURNACE_RESULT_X, FURNACE_RESULT_Y, PAGE_FURNACE));
		addSlot(new PageSlot(brewingInput, 0, BREWING_INPUT_X, BREWING_INPUT_Y, PAGE_BREWING));
		addSlot(new PageSlot(brewingReagent, 0, BREWING_REAGENT_X, BREWING_REAGENT_Y, PAGE_BREWING));
		addSlot(new PageSlot(brewingResult, 0, BREWING_RESULT_X, BREWING_RESULT_Y, PAGE_BREWING));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9,
						PLAYER_X + column * 18, PLAYER_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, HOTBAR_Y));
		}
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = Math.max(PAGE_CRAFTING, Math.min(PAGE_BREWING, page));
	}

	private final class PageSlot extends Slot {
		private final int slotPage;

		private PageSlot(Container container, int index, int x, int y, int slotPage) {
			super(container, index, x, y);
			this.slotPage = slotPage;
		}

		@Override
		public boolean isActive() {
			return page == slotPage;
		}
	}

	public void handleAction(ServerPlayer player, RecipeEditorActionPayload action) {
		if (player.containerMenu != this || !player.hasPermissions(2)) {
			return;
		}
		setPage(action.page());
		if (action.action() == RecipeEditorActionPayload.ACTION_SET_PAGE) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		try {
			boolean delete = action.action() == RecipeEditorActionPayload.ACTION_DELETE;
			String recipeId = normalizeRecipeId(action.recipeId());
			if (recipeId.isEmpty() && !delete) {
				recipeId = defaultRecipeId();
			}
			if (recipeId.isEmpty()) {
				player.sendSystemMessage(Component.translatable(
						"message.tkgun.recipe_editor.invalid_id"));
				return;
			}
			Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATA_PACK_ID);
			Path recipeDir = recipeDirectory(root, page);
			Files.createDirectories(recipeDir);
			Path target = recipeDir.resolve(recipeId + ".json").normalize();
			if (!target.startsWith(recipeDir.normalize())) {
				throw new IllegalArgumentException("Invalid recipe path");
			}
			if (delete) {
				Files.deleteIfExists(target);
				writePackMetadata(root);
				if (page == PAGE_BREWING) {
					MechanicianBrewingRecipes.reload(server);
				}
				enableAndReload(server);
				player.sendSystemMessage(Component.translatable(
						"message.tkgun.recipe_editor.deleted", RECIPE_NAMESPACE + ":" + recipeId));
				return;
			}

			JsonObject recipe = page == PAGE_BREWING
					? buildBrewingRecipe()
					: buildRecipe(page, action.shaped(), action.experience(), action.cookingTime());
			if (recipe == null) {
				player.sendSystemMessage(Component.translatable(
						"message.tkgun.recipe_editor.invalid_recipe"));
				return;
			}
			applyRecipe(server, page, recipeId, recipe);
			player.sendSystemMessage(Component.translatable(
					"message.tkgun.recipe_editor.saved", RECIPE_NAMESPACE + ":" + recipeId));
			TkGun.LOGGER.info("[tkgun] Visual recipe saved: {}:{}", RECIPE_NAMESPACE, recipeId);
		} catch (Throwable error) {
			TkGun.LOGGER.error("[tkgun] Recipe editor save failed", error);
			player.sendSystemMessage(Component.translatable(
					"message.tkgun.recipe_editor.failed", String.valueOf(error.getMessage())));
		}
	}

	/**
	 * 写盘并热重载数据包。与权限无关，便于 GameTest / 服务端自检直接调用。
	 */
	public static void applyRecipe(MinecraftServer server, int page,
								   String recipeId, JsonObject recipe) throws Exception {
		if (server == null || recipe == null) {
			throw new IllegalArgumentException("missing server or recipe");
		}
		Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATA_PACK_ID);
		Path recipeDir = recipeDirectory(root, page);
		Files.createDirectories(recipeDir);
		Path target = recipeDir.resolve(recipeId + ".json").normalize();
		if (!target.startsWith(recipeDir.normalize())) {
			throw new IllegalArgumentException("Invalid recipe path");
		}
		writePackMetadata(root);
		if (Files.isRegularFile(target)) {
			Path backup = root.resolve("backups").resolve(recipeId + ".json");
			Files.createDirectories(backup.getParent());
			Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.writeString(target, GSON.toJson(recipe), StandardCharsets.UTF_8);
		if (page == PAGE_BREWING) {
			MechanicianBrewingRecipes.reload(server);
		}
		enableAndReload(server);
	}

	private JsonObject buildRecipe(int page, boolean shaped, float experience, int cookingTime) {
		ItemStack output = outputForPage(page).getItem(0);
		if (output.isEmpty()) {
			return null;
		}
		JsonObject recipe = new JsonObject();
		JsonObject resultJson = serializeStack(output);
		if (resultJson == null) {
			return null;
		}
		if (page == PAGE_FURNACE) {
			ItemStack input = furnaceInput.getItem(0);
			if (input.isEmpty()) {
				return null;
			}
			recipe.addProperty("type", "minecraft:smelting");
			recipe.addProperty("category", "misc");
			recipe.add("ingredient", ingredient(input));
			recipe.add("result", resultJson);
			recipe.addProperty("experience", Math.max(0.0F, experience));
			recipe.addProperty("cookingtime", Math.max(1, cookingTime));
			return recipe;
		}
		recipe.addProperty("type", shaped
				? "minecraft:crafting_shaped" : "minecraft:crafting_shapeless");
		recipe.addProperty("category", "misc");
		if (shaped) {
			if (!buildShaped(recipe)) {
				return null;
			}
		} else {
			JsonArray list = new JsonArray();
			for (int index = 0; index < GRID_SIZE; index++) {
				ItemStack stack = ingredients.getItem(index);
				if (!stack.isEmpty()) {
					list.add(ingredient(stack));
				}
			}
			if (list.isEmpty()) {
				return null;
			}
			recipe.add("ingredients", list);
		}
		recipe.add("result", resultJson);
		return recipe;
	}

	private JsonObject buildBrewingRecipe() {
		ItemStack input = brewingInput.getItem(0);
		ItemStack reagent = brewingReagent.getItem(0);
		ItemStack output = brewingResult.getItem(0);
		if (input.isEmpty() || reagent.isEmpty() || output.isEmpty()) {
			return null;
		}
		JsonObject recipe = new JsonObject();
		recipe.addProperty("type", "tkgun:brewing");
		recipe.addProperty("input_item",
				BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
		recipe.addProperty("reagent_item",
				BuiltInRegistries.ITEM.getKey(reagent.getItem()).toString());
		recipe.addProperty("output_item",
				BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
		recipe.addProperty("output_count",
				Math.max(1, Math.min(output.getMaxStackSize(), output.getCount())));
		return recipe;
	}

	private Container outputForPage(int page) {
		return switch (page) {
			case PAGE_FURNACE -> furnaceResult;
			case PAGE_BREWING -> brewingResult;
			default -> result;
		};
	}

	private boolean buildShaped(JsonObject recipe) {
		int minRow = 3;
		int maxRow = -1;
		int minColumn = 3;
		int maxColumn = -1;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				if (ingredients.getItem(column + row * 3).isEmpty()) {
					continue;
				}
				minRow = Math.min(minRow, row);
				maxRow = Math.max(maxRow, row);
				minColumn = Math.min(minColumn, column);
				maxColumn = Math.max(maxColumn, column);
			}
		}
		if (maxRow < minRow || maxColumn < minColumn) {
			return false;
		}
		Map<String, Character> symbols = new LinkedHashMap<>();
		JsonObject key = new JsonObject();
		JsonArray pattern = new JsonArray();
		int nextSymbol = 0;
		for (int row = minRow; row <= maxRow; row++) {
			StringBuilder line = new StringBuilder();
			for (int column = minColumn; column <= maxColumn; column++) {
				ItemStack stack = ingredients.getItem(column + row * 3);
				if (stack.isEmpty()) {
					line.append(' ');
					continue;
				}
				String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				Character symbol = symbols.get(itemId);
				if (symbol == null) {
					symbol = (char) ('A' + nextSymbol++);
					symbols.put(itemId, symbol);
					key.add(String.valueOf(symbol), ingredient(stack));
				}
				line.append(symbol);
			}
			pattern.add(line.toString());
		}
		recipe.add("pattern", pattern);
		recipe.add("key", key);
		return true;
	}

	private JsonObject ingredient(ItemStack stack) {
		JsonObject ingredient = new JsonObject();
		ingredient.addProperty("item",
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		return ingredient;
	}

	/** 1.20.1 配方 result 只接受 item/count/nbt，不序列化完整组件。 */
	private JsonObject serializeStack(ItemStack stack) {
		JsonObject json = new JsonObject();
		json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		json.addProperty("count", Math.max(1, stack.getCount()));
		if (stack.getTag() != null && !stack.getTag().isEmpty()) {
			json.addProperty("nbt", stack.getTag().toString());
		}
		return json;
	}

	private String defaultRecipeId() {
		ItemStack output = outputForPage(page).getItem(0);
		String item = output.isEmpty() ? "recipe"
				: BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
		return "custom_" + item + "_" + System.currentTimeMillis();
	}

	private static Path recipeDirectory(Path root, int page) {
		return root.resolve("data").resolve(RECIPE_NAMESPACE)
				.resolve(page == PAGE_BREWING ? "brewing" : "recipe");
	}

	private static String normalizeRecipeId(String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		int separator = value.indexOf(':');
		if (separator >= 0) {
			value = value.substring(separator + 1);
		}
		value = value.replaceAll("[^a-z0-9_./-]", "_");
		while (value.startsWith("/")) {
			value = value.substring(1);
		}
		return value;
	}

	private static void writePackMetadata(Path root) throws Exception {
		Files.createDirectories(root);
		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", DATA_PACK_FORMAT);
		pack.addProperty("description", "MechanicianCombat visual recipe editor");
		JsonObject metadata = new JsonObject();
		metadata.add("pack", pack);
		Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(metadata),
				StandardCharsets.UTF_8);
	}

	private static void enableAndReload(MinecraftServer server) {
		var repository = server.getPackRepository();
		repository.reload();
		List<String> ids = new ArrayList<>(repository.getSelectedIds());
		String packId = "file/" + DATA_PACK_ID;
		if (!ids.contains(packId)) {
			ids.add(packId);
		}
		repository.setSelected(ids);
		server.reloadResources(repository.getSelectedIds()).exceptionally(error -> {
			TkGun.LOGGER.error("[tkgun] Failed to reload visual recipe data pack", error);
			return null;
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();
		boolean moved;
		if (slotIndex < PLAYER_SLOT_START) {
			moved = moveItemStackTo(original, PLAYER_SLOT_START, slots.size(), true);
		} else if (slotIndex < HOTBAR_SLOT_START) {
			moved = moveItemStackTo(original, HOTBAR_SLOT_START, slots.size(), true);
		} else {
			moved = moveItemStackTo(original, PLAYER_SLOT_START, HOTBAR_SLOT_START, true);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!(player instanceof ServerPlayer)) {
			return;
		}
		for (int index = 0; index < ingredients.getContainerSize(); index++) {
			ItemStack stack = ingredients.removeItemNoUpdate(index);
			if (!stack.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stack);
			}
		}
		for (Container container : new Container[]{
				result, furnaceInput, furnaceResult, brewingInput, brewingReagent, brewingResult}) {
			ItemStack stack = container.removeItemNoUpdate(0);
			if (!stack.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stack);
			}
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player == owner && player.isAlive();
	}

	@Override
	public MenuType<?> getType() {
		return TYPE;
	}
}
