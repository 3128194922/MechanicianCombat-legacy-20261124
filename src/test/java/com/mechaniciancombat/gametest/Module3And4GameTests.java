package com.mechaniciancombat.gametest;

import com.mojang.authlib.GameProfile;
import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.item.ArmorPartItem;
import com.mechaniciancombat.item.ArmorUpgradeData;
import com.mechaniciancombat.net.SoulChainPayload;
import com.mechaniciancombat.registry.ModRegistries;
import com.mechaniciancombat.screen.ArmorModificationTableScreenHandler;
import com.mechaniciancombat.screen.RecipeEditorScreenHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** 模块 3/4 的运行时契约：动能剑连段、护甲台写入、配方菜单、锁链包。 */
@GameTestHolder("tkgun")
@PrefixGameTestTemplate(false)
public final class Module3And4GameTests {
	private static FakePlayer player(GameTestHelper helper, String name) {
		return FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), name));
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void kineticSwordStartsAndChains(GameTestHelper helper) throws Exception {
		FakePlayer player = player(helper, "kinetic_sword_chain");
		player.setItemInHand(InteractionHand.MAIN_HAND,
				new ItemStack(ModRegistries.KINETIC_SWORD.get()));
		player.setOnGround(true);
		var melee = TkGunServer.class.getDeclaredMethod("handleMeleeInput",
				net.minecraft.server.level.ServerPlayer.class, boolean.class, boolean.class);
		melee.setAccessible(true);
		melee.invoke(null, player, true, false);
		var state = TkGunServer.state(player);
		helper.assertTrue(state.state == com.mechaniciancombat.TkGun.State.MELEE
						&& state.kineticSwordAttack && state.shotIndex == 1,
				"kinetic sword left click must start ATK_1");

		var tickMelee = TkGunServer.class.getDeclaredMethod("tickMelee",
				net.minecraft.server.level.ServerPlayer.class, TkGunServer.PState.class, long.class);
		tickMelee.setAccessible(true);
		long now = helper.getLevel().getGameTime();
		state.stateStarted = now - 20L;
		state.meleeHeld = true;
		tickMelee.invoke(null, player, state, now);
		helper.assertTrue(state.meleeCombo == 2 && state.kineticSwordAttack,
				"holding attack must chain ATK_1 -> ATK_2");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void armorTableWritesAndRemovesModules(GameTestHelper helper) throws Exception {
		FakePlayer player = player(helper, "armor_table_install");
		ItemStack chest = new ItemStack(ModRegistries.MECHANICIAN_CHESTPLATE.get());
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		ArmorModificationTableScreenHandler menu =
				new ArmorModificationTableScreenHandler(0, player.getInventory(), BlockPos.ZERO);
		var sync = ArmorModificationTableScreenHandler.class
				.getDeclaredMethod("syncTableState");
		sync.setAccessible(true);
		menu.getSlot(ArmorModificationTableScreenHandler.PART_SLOT_START)
				.set(new ItemStack(ModRegistries.UNDYING_TOTEM_PART.get()));
		sync.invoke(menu);
		ItemStack installed = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(ArmorUpgradeData.hasPart(installed, ArmorPartItem.Kind.C1),
				"armor table must write the C1 module into the chestplate");

		menu.getSlot(ArmorModificationTableScreenHandler.PART_SLOT_START).set(ItemStack.EMPTY);
		sync.invoke(menu);
		ItemStack removed = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(!ArmorUpgradeData.hasPart(removed, ArmorPartItem.Kind.C1),
				"removing the module slot must clear the chestplate module");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void recipeEditorMenuBuilds(GameTestHelper helper) {
		FakePlayer player = player(helper, "recipe_editor_menu");
		RecipeEditorScreenHandler menu =
				new RecipeEditorScreenHandler(0, player.getInventory());
		helper.assertTrue(RecipeEditorScreenHandler.TYPE != null,
				"recipe editor menu type must be registered");
		helper.assertTrue(menu.slots.size() == 15 + 36,
				"recipe editor must expose 15 editor slots plus player inventory");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulChainPayloadRoundTrips(GameTestHelper helper) {
		SoulChainPayload payload = new SoulChainPayload(List.of(
				new SoulChainPayload.ChainNode(1, 0.5D, 64.0D, 0.5D),
				new SoulChainPayload.ChainNode(2, 1.5D, 64.0D, 1.5D)), 30);
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		SoulChainPayload.CODEC.encoder().accept(payload, buffer);
		SoulChainPayload decoded = SoulChainPayload.CODEC.decoder().apply(buffer);
		helper.assertTrue(decoded.nodes().size() == 2
						&& decoded.nodes().get(0).entityId() == 1
						&& decoded.nodes().get(1).entityId() == 2
						&& decoded.durationTicks() == 30,
				"soul chain payload must round-trip nodes and duration");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void helmetToggleExpandsAndFolds(GameTestHelper helper) {
		FakePlayer player = player(helper, "helmet_toggle");
		player.setItemSlot(EquipmentSlot.CHEST,
				new ItemStack(ModRegistries.MECHANICIAN_CHESTPLATE.get()));
		player.setItemSlot(EquipmentSlot.LEGS,
				new ItemStack(ModRegistries.MECHANICIAN_LEGGINGS.get()));
		player.setItemSlot(EquipmentSlot.FEET,
				new ItemStack(ModRegistries.MECHANICIAN_BOOTS.get()));
		var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
				.resolve().orElseThrow();
		curios.setEquippedCurio("back", 0,
				new ItemStack(ModRegistries.MECHANICIAN_UNDERSUIT.get()));
		helper.assertTrue(com.mechaniciancombat.item.MechanicianUndersuitItem
				.isWearingFullSet(player), "full suit must be detected");

		com.mechaniciancombat.ArmorHelmetController.toggle(player);
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(ArmorUpgradeData.helmetExpanded(chest),
				"first toggle must expand the helmet");
		helper.assertTrue(ArmorUpgradeData.helmetTransitionDuration(chest)
						== ArmorUpgradeData.HELMET_EXPAND_TICKS,
				"expand transition must use the authored duration");

		// Immediate second press is discarded while the authored clip is active.
		com.mechaniciancombat.ArmorHelmetController.toggle(player);
		helper.assertTrue(ArmorUpgradeData.helmetExpanded(chest),
				"toggle during transition must be ignored");

		long expired = helper.getLevel().getGameTime()
				- ArmorUpgradeData.HELMET_EXPAND_TICKS - 1L;
		ArmorUpgradeData.beginHelmetTransition(chest, true, expired);
		com.mechaniciancombat.ArmorHelmetController.toggle(player);
		helper.assertTrue(!ArmorUpgradeData.helmetExpanded(chest),
				"toggle after the transition must fold the helmet");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void armorTableInstallsT1OnLeggings(GameTestHelper helper) throws Exception {
		FakePlayer player = player(helper, "armor_table_t1");
		player.setItemSlot(EquipmentSlot.LEGS,
				new ItemStack(ModRegistries.MECHANICIAN_LEGGINGS.get()));
		ArmorModificationTableScreenHandler menu =
				new ArmorModificationTableScreenHandler(0, player.getInventory(), BlockPos.ZERO);
		var sync = ArmorModificationTableScreenHandler.class.getDeclaredMethod("syncTableState");
		sync.setAccessible(true);
		int t1Slot = ArmorModificationTableScreenHandler.PART_SLOT_START
				+ ArmorPartItem.Kind.T1.ordinal();
		menu.getSlot(t1Slot).set(new ItemStack(ModRegistries.SOUL_ANCHOR_PART.get()));
		sync.invoke(menu);
		ItemStack installed = player.getItemBySlot(EquipmentSlot.LEGS);
		helper.assertTrue(ArmorUpgradeData.hasPart(installed, ArmorPartItem.Kind.T1),
				"armor table must write the T1 module into the leggings");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void backpackModuleSlotFollowsLauncher(GameTestHelper helper) {
		FakePlayer player = player(helper, "backpack_module_slot");
		com.mechaniciancombat.screen.BackpackModificationTableScreenHandler menu =
				new com.mechaniciancombat.screen.BackpackModificationTableScreenHandler(
						0, player.getInventory(), BlockPos.ZERO);
		int launcherSlot = com.mechaniciancombat.screen.BackpackModificationTableScreenHandler
				.FIRST_PART_SLOT_INDEX
				+ com.mechaniciancombat.screen.BackpackModificationTableScreenHandler.LAUNCHER_PART_SLOT;
		int moduleSlot = com.mechaniciancombat.screen.BackpackModificationTableScreenHandler
				.FIRST_PART_SLOT_INDEX
				+ com.mechaniciancombat.screen.BackpackModificationTableScreenHandler.MISSILE_PART_SLOT;
		menu.getSlot(launcherSlot).set(new ItemStack(ModRegistries.SUMMON_ANTENNA.get()));
		helper.assertTrue(menu.getSlot(moduleSlot).mayPlace(
						new ItemStack(ModRegistries.GOLEM_DISPATCH_CAPSULE.get())),
				"summon antenna must accept the golem capsule");
		helper.assertTrue(!menu.getSlot(moduleSlot).mayPlace(
						new ItemStack(ModRegistries.MISSILE_BOX.get())),
				"summon antenna must reject the missile box");
		menu.getSlot(launcherSlot).set(
				new ItemStack(ModRegistries.JUMP_BACKPACK_THRUSTER.get()));
		helper.assertTrue(menu.getSlot(moduleSlot).mayPlace(
						new ItemStack(ModRegistries.MISSILE_BOX.get())),
				"jump thruster must accept the missile box");
		helper.assertTrue(!menu.getSlot(moduleSlot).mayPlace(
						new ItemStack(ModRegistries.GOLEM_DISPATCH_CAPSULE.get())),
				"jump thruster must reject the golem capsule");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void recipeEditorBuildsAuthoredJson(GameTestHelper helper) throws Exception {
		FakePlayer player = player(helper, "recipe_json");
		RecipeEditorScreenHandler menu = new RecipeEditorScreenHandler(0, player.getInventory());
		menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
		menu.getSlot(RecipeEditorScreenHandler.CRAFTING_RESULT_SLOT)
				.set(new ItemStack(net.minecraft.world.item.Items.STICK));
		var build = RecipeEditorScreenHandler.class.getDeclaredMethod("buildRecipe",
				int.class, boolean.class, float.class, int.class);
		build.setAccessible(true);
		var shaped = (com.google.gson.JsonObject) build.invoke(menu, 0, true, 0.1F, 200);
		helper.assertTrue(shaped != null
						&& "minecraft:crafting_shaped".equals(shaped.get("type").getAsString())
						&& shaped.has("pattern") && shaped.has("key"),
				"shaped crafting page must build pattern/key JSON");

		menu.getSlot(RecipeEditorScreenHandler.FURNACE_INPUT_SLOT)
				.set(new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
		menu.getSlot(RecipeEditorScreenHandler.FURNACE_RESULT_SLOT)
				.set(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		var furnace = (com.google.gson.JsonObject) build.invoke(menu, 1, false, 0.7F, 120);
		helper.assertTrue(furnace != null
						&& "minecraft:smelting".equals(furnace.get("type").getAsString())
						&& furnace.get("cookingtime").getAsInt() == 120,
				"furnace page must build smelting JSON with cooking time");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulAnchorRequiresC3AndT1(GameTestHelper helper) {
		FakePlayer player = player(helper, "soul_anchor_gate");
		player.setOnGround(true);
		ItemStack chest = new ItemStack(ModRegistries.MECHANICIAN_CHESTPLATE.get());
		ItemStack legs = new ItemStack(ModRegistries.MECHANICIAN_LEGGINGS.get());
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		player.setItemSlot(EquipmentSlot.LEGS, legs);
		helper.assertTrue(!com.mechaniciancombat.SoulAnchorSkillService.isAvailable(player),
				"soul anchor must be locked without C3/T1 modules");
		ArmorUpgradeData.setStoredPart(chest, ArmorPartItem.Kind.C3,
				new ItemStack(ModRegistries.SOUL_CHAIN.get()));
		ArmorUpgradeData.setStoredPart(legs, ArmorPartItem.Kind.T1,
				new ItemStack(ModRegistries.SOUL_ANCHOR_PART.get()));
		helper.assertTrue(com.mechaniciancombat.SoulAnchorSkillService.isAvailable(player),
				"soul anchor must unlock with C3 + T1 installed");
		helper.succeed();
	}

	/** 配方写盘 + 数据包热重载：文件落盘、pack.mcmeta 生成、数据包被启用。 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void recipeEditorWritesAndReloadsDatapack(GameTestHelper helper) throws Exception {
		var server = helper.getLevel().getServer();
		com.google.gson.JsonObject recipe = new com.google.gson.JsonObject();
		recipe.addProperty("type", "minecraft:crafting_shapeless");
		recipe.addProperty("category", "misc");
		com.google.gson.JsonArray ingredients = new com.google.gson.JsonArray();
		com.google.gson.JsonObject ingredient = new com.google.gson.JsonObject();
		ingredient.addProperty("item", "minecraft:oak_planks");
		ingredients.add(ingredient);
		recipe.add("ingredients", ingredients);
		com.google.gson.JsonObject result = new com.google.gson.JsonObject();
		result.addProperty("item", "minecraft:stick");
		result.addProperty("count", 2);
		recipe.add("result", result);

		String recipeId = "gametest_" + UUID.randomUUID().toString().substring(0, 8);
		RecipeEditorScreenHandler.applyRecipe(server, RecipeEditorScreenHandler.PAGE_CRAFTING,
				recipeId, recipe);

		java.nio.file.Path root = server.getWorldPath(
				net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("tkgun_live");
		java.nio.file.Path file = root.resolve("data").resolve("tkgun_custom")
				.resolve("recipe").resolve(recipeId + ".json");
		helper.assertTrue(java.nio.file.Files.isRegularFile(file),
				"recipe json must be written to the live datapack");
		helper.assertTrue(java.nio.file.Files.isRegularFile(root.resolve("pack.mcmeta")),
				"datapack metadata must exist");
		helper.assertTrue(server.getPackRepository().getSelectedIds().contains("file/tkgun_live"),
				"live datapack must be enabled");

		// 清理，避免污染后续测试存档。
		java.nio.file.Files.deleteIfExists(file);
		helper.succeed();
	}

	private Module3And4GameTests() {
	}
}
