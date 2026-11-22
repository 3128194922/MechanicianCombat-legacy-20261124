package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.ClientNetwork;
import com.mechaniciancombat.net.GunReloadPayload;
import com.mechaniciancombat.net.GunRollPayload;
import com.mechaniciancombat.screen.BackpackModificationTableScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端自动演示 + 截图（只有 -Dtkgun.demo=1 时才跑）。
 *
 * 用途：验证第三人称持枪动作是否真的播出来；闪避演示发送正式 C2S 请求，避免 GLFW 物理输入无法脚本化。
 * 每进入一个新动画就截一张 assistant 目录下的 PNG，日志里也会打 STEP。
 */
public final class GunDebugHarness {
	/**
	 * ⚠️ 别用 {@code Boolean.getBoolean("tkgun.demo")} —— 它只认字面量 "true"，
	 * 而启动脚本传的是 {@code -Dtkgun.demo=1}，那样开关永远关着（2026-10-12 踩过：
	 * 演示一直没跑，之前能出截图是因为用 RCON 打开了远程演示）。
	 */
	private static final boolean ENABLED = truthy(System.getProperty("tkgun.demo"));
	/** Optional UI acceptance path; disabled unless the isolated client is explicitly launched with -Dtkgun.demoUi=1. */
	private static final boolean UI_DEMO = truthy(System.getProperty("tkgun.demoUi"));
	/** Exercises real screen mouse handling against a server menu in an isolated acceptance client. */
	private static final boolean UI_TRANSFER_DEMO = truthy(System.getProperty("tkgun.demoUiTransfer"));
	/** Optional end-to-end backpack table UI acceptance path for isolated clients. */
	private static final boolean UI_TABLE_DEMO = truthy(System.getProperty("tkgun.demoTableUi"));
	/** Isolated singleplayer visual scenario for an equipped auto-launcher backpack. */
	private static final boolean BACKPACK_DEMO = truthy(System.getProperty("tkgun.demoBackpack"));
	private static final boolean LOYALTY_DEMO = truthy(System.getProperty("tkgun.loyaltyDemo"));
	private static final boolean WIND_DEMO = truthy(System.getProperty("tkgun.windDemo"));
	/** 配方编辑器界面的实机验收（只开屏 + 截图，不改配方）。 */
	private static final boolean RECIPE_UI_DEMO = truthy(System.getProperty("tkgun.demoRecipeUi"));
	/** 猎矛固定炮台的实机验收：发矛 → 右键部署 → 等炮台生成/开火。 */
	private static final boolean HUNTER_DEMO = truthy(System.getProperty("tkgun.demoHunter"));
	/** 两个改装台方块的界面实机验收。 */
	private static final boolean TABLES_DEMO = truthy(System.getProperty("tkgun.demoTables"));
	/** 战旗光环的实机验收：召唤战旗 → 检查玩家是否获得吸收/力量。 */
	private static final boolean BANNER_DEMO = truthy(System.getProperty("tkgun.demoBanner"));
	/** 灵魂锚的实机验收：装 C3/T1 部件 → 按住冲刺蓄力 → 松开投掷。 */
	private static final boolean SOUL_ANCHOR_DEMO = truthy(System.getProperty("tkgun.demoSoulAnchor"));
	/**
	 * 演示用哪把枪：`bowgun`（默认）/ `abyssal_ancient_cannon` / `veritas_et_justitia`。
	 *
	 * <p>为什么要能选：蹲射 bug 只出现在后两把，只验 bowgun 等于没验到问题所在。
	 * 用法 `-Ptkgun.demoGun=abyssal_ancient_cannon`。
	 */
	private static final String DEMO_GUN = System.getProperty("tkgun.demoGun", "bowgun").trim();

	private static boolean truthy(String value) {
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
	}

	/**
	 * 单机验收：把 `DEMO_GUN` 指定的枪 + 弹药塞进快捷栏 0/1，并让客户端真的选中它。
	 *
	 * <p>成功才置 `demoKitGranted`，失败（例如服务器还没起来）下一 tick 会重试。
	 */
	private static void grantDemoKit(Minecraft client) {
		var server = client.getSingleplayerServer();
		if (server == null || client.player == null) {
			return;
		}
		String name = client.player.getGameProfile().getName();
		server.execute(() -> {
			try {
				var source = server.createCommandSourceStack();
				int granted = server.getCommands().performPrefixedCommand(source,
						"tkgun kit " + name + " " + DEMO_GUN);
				TkGun.LOGGER.info("[tkgun-demo] /tkgun kit {} 结果={} player={}", DEMO_GUN, granted, name);
				if (granted > 0) {
					demoKitGranted = true;
				}
			} catch (Throwable error) {
				TkGun.LOGGER.warn("[tkgun-demo] /tkgun kit 抛异常", error);
			}
		});
	}

	/** 通过 `/tkgun fire` 让服务端真的开一枪（与玩家扣扳机同一个 `tryFire` 入口）。 */
	private static void fireViaServer(Minecraft client) {
		var server = client.getSingleplayerServer();
		if (server == null || client.player == null) {
			return;
		}
		String name = client.player.getGameProfile().getName();
		server.execute(() -> {
			try {
				server.getCommands().performPrefixedCommand(
						server.createCommandSourceStack(), "tkgun fire " + name);
				TkGun.LOGGER.info("[tkgun-demo] 已请求服务端开火（专注中）player={}", name);
			} catch (Throwable error) {
				TkGun.LOGGER.warn("[tkgun-demo] /tkgun fire 抛异常", error);
			}
		});
	}
	private static boolean remoteEnabled;
	private static int hunterDemoTick;
	private static int tablesDemoTick;
	private static int bannerDemoTick;
	private static int soulDemoTick;

	/**
	 * 灵魂锚的实机验收。
	 *
	 * <p>`SoulAnchorSkillService#isAvailable` 要求**胸甲装 C3（灵魂锁链）+ 护腿装 T1（灵魂锚）**，
	 * 所以这里先用 `ArmorUpgradeData.setPartId` 把部件写进护甲再穿上 ——
	 * 顺带也验证了模块 4 的"护甲模块"数据链。
	 */
	private static void tickSoulAnchorDemo(Minecraft client) {
		soulDemoTick++;
		if (soulDemoTick == 10 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			var playerId = client.player.getUUID();
			server.execute(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player == null) {
					return;
				}
				net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(
						com.mechaniciancombat.registry.ModRegistries.MECHANICIAN_CHESTPLATE.get());
				net.minecraft.world.item.ItemStack legs = new net.minecraft.world.item.ItemStack(
						com.mechaniciancombat.registry.ModRegistries.MECHANICIAN_LEGGINGS.get());
				com.mechaniciancombat.item.ArmorUpgradeData.setPartId(chest,
						com.mechaniciancombat.item.ArmorPartItem.Kind.C3, "tkgun:soul_chain");
				com.mechaniciancombat.item.ArmorUpgradeData.setPartId(legs,
						com.mechaniciancombat.item.ArmorPartItem.Kind.T1, "tkgun:soul_anchor");
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
				// 底衣是护甲生效的前置。
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
				player.getInventory().armor.set(2, chest);
				player.getInventory().armor.set(1, legs);
				// ⚠️ 没有底衣时，`TkGunServer#enforceMechanicianArmorRequirement` 会**每 tick**
				// 把机械师护甲脱下来（"机械师护甲必须配底衣"）—— 不装底衣，
				// 前面装的 C3/T1 下一 tick 就没了，`isAvailable` 变 false。
				top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve()
						.ifPresent(curios -> {
							var charm = curios.getCurios().get("charm");
							if (charm != null && charm.getStacks().getSlots() > 0) {
								charm.getStacks().setStackInSlot(0, new net.minecraft.world.item.ItemStack(
										com.mechaniciancombat.registry.ModRegistries.MECHANICIAN_UNDERSUIT.get()));
							}
						});
				TkGun.LOGGER.info("[tkgun-soul-demo] 已装 C3/T1 部件 可用={}",
						com.mechaniciancombat.SoulAnchorSkillService.isAvailable(player));
				TkGun.LOGGER.info("[tkgun-soul-demo] 落地={} 胸甲={} 护腿={}",
						player.onGround(),
						com.mechaniciancombat.item.ArmorUpgradeData.partId(
								player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
								com.mechaniciancombat.item.ArmorPartItem.Kind.C3),
						com.mechaniciancombat.item.ArmorUpgradeData.partId(
								player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
								com.mechaniciancombat.item.ArmorPartItem.Kind.T1));
			});
		}
		// 蓄力 = 按住（held=true），投掷 = 松开（held=false）。
		// 直接发 `SoulAnchorInputPayload`，与玩家按冲刺键走同一条 C2S 链路；
		// 不依赖 `keySprint.setDown`（原版输入链未必在 demo 环境里如实反映）。
		if (soulDemoTick == 60) {
			ClientNetwork.send(new com.mechaniciancombat.net.SoulAnchorInputPayload(true, false, false));
			TkGun.LOGGER.info("[tkgun-soul-demo] 已发 press（蓄力）");
		}
		if (soulDemoTick == 100) {
			ClientNetwork.send(new com.mechaniciancombat.net.SoulAnchorInputPayload(false, false, false));
			TkGun.LOGGER.info("[tkgun-soul-demo] 已发 release（投掷）");
		}
		// press 之后**逐 tick** 观察：找不到目标时会立刻 finish，窗口很窄。
		if (soulDemoTick >= 60 && soulDemoTick <= 72 || soulDemoTick == 90
				|| soulDemoTick == 105 || soulDemoTick == 130) {
			int anchors = client.level.getEntitiesOfClass(
					com.mechaniciancombat.entity.SoulAnchorEntity.class,
					client.player.getBoundingBox().inflate(32.0D)).size();
			TkGun.LOGGER.info("[tkgun-soul-demo] tick={} 阶段={} 锚实体={}",
					soulDemoTick,
					describeSoulPhase(client.player.getId()),
					anchors);
			// 只在关键帧截图：每 tick 截图会让 IO 线程堆积。
			if (soulDemoTick == 62 || soulDemoTick == 130) {
				shot(client, "tkgun_soul_" + soulDemoTick + ".png");
			}
		}
	}

	/**
	 * 战旗光环的实机验收：在玩家旁边召唤一面战旗，观察玩家是否获得
	 * `ABSORPTION` + `DAMAGE_BOOST`（`WarBannerEntity#applyAura` 每 5 tick 施加一次）。
	 */
	/** 把灵魂锚客户端阶段翻译成可读字符串（`Entry.phase()` 是 byte）。 */
	private static String describeSoulPhase(int playerId) {
		var entry = com.mechaniciancombat.client.SoulAnchorSkillClientState.get(playerId);
		if (entry == null) {
			return "无";
		}
		int phase = entry.phase();
		return switch (phase) {
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.READY -> "READY";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.THROW -> "THROW";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.ANCHOR_FLIGHT -> "ANCHOR_FLIGHT";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.ANCHOR_STALL -> "ANCHOR_STALL";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.DRAG -> "DRAG";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.DRAG_ENTITY -> "DRAG_ENTITY";
			case com.mechaniciancombat.net.SoulAnchorSkillPayload.RECALL -> "RECALL";
			default -> "phase=" + phase;
		};
	}

	private static void tickBannerDemo(Minecraft client) {
		bannerDemoTick++;
		if (bannerDemoTick == 1 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			var pos = client.player.blockPosition().offset(3, 0, 0);
			String uuid = client.player.getUUID().toString();
			server.execute(() -> {
				var source = server.createCommandSourceStack();
				// 世界持久化：先清掉旧战旗，避免冷却/数量干扰。
				server.getCommands().performPrefixedCommand(source, "kill @e[type=tkgun:war_banner]");
				server.getCommands().performPrefixedCommand(source,
						"summon tkgun:war_banner " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
								+ " {Owner:[" + uuid + "]}");
				TkGun.LOGGER.info("[tkgun-banner-demo] 已召唤战旗 {}", pos);
			});
		}
		if (bannerDemoTick == 40 || bannerDemoTick == 80 || bannerDemoTick == 140) {
			var absorption = client.player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
			var strength = client.player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
			int banners = client.level.getEntitiesOfClass(
					com.mechaniciancombat.entity.WarBannerEntity.class,
					client.player.getBoundingBox().inflate(16.0D)).size();
			TkGun.LOGGER.info("[tkgun-banner-demo] tick={} 战旗={} 吸收={} 力量={}",
					bannerDemoTick, banners,
					absorption == null ? "无" : ("有(" + absorption.getDuration() + "t)"),
					strength == null ? "无" : ("有(" + strength.getDuration() + "t)"));
			shot(client, "tkgun_banner_" + bannerDemoTick + ".png");
		}
	}
	private static net.minecraft.core.BlockPos armorTablePos;
	private static net.minecraft.core.BlockPos backpackTablePos;

	/**
	 * 两个改装台界面的实机验收：放方块 → 右键打开 → 截图。
	 *
	 * <p>方块交互不能用 `KeyMapping.setDown` 模拟（原版走 `consumeClick` 的按下边沿），
	 * 这里直接 `gameMode.useItemOn(...)`，与玩家真的右键方块是同一条 C2S 链路。
	 */
	private static void tickTablesDemo(Minecraft client) {
		tablesDemoTick++;
		if (tablesDemoTick == 1 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			net.minecraft.core.BlockPos base = client.player.blockPosition();
			armorTablePos = base.offset(2, 0, 0);
			backpackTablePos = base.offset(-2, 0, 0);
			net.minecraft.core.BlockPos armor = armorTablePos;
			net.minecraft.core.BlockPos backpack = backpackTablePos;
			server.execute(() -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source,
						"setblock " + armor.getX() + " " + armor.getY() + " " + armor.getZ()
								+ " tkgun:armor_modification_table");
				server.getCommands().performPrefixedCommand(source,
						"setblock " + backpack.getX() + " " + backpack.getY() + " " + backpack.getZ()
								+ " tkgun:backpack_modification_table");
				TkGun.LOGGER.info("[tkgun-tables-demo] 已放置两个改装台");
			});
		}
		if (tablesDemoTick == 20 && armorTablePos != null) {
			useBlock(client, armorTablePos);
		}
		if (tablesDemoTick == 40) {
			TkGun.LOGGER.info("[tkgun-tables-demo] 盔甲改装台 screen={}",
					client.screen == null ? "null" : client.screen.getClass().getSimpleName());
			shot(client, "tkgun_armor_table.png");
		}
		if (tablesDemoTick == 60 && client.screen != null) {
			client.screen.onClose();
		}
		if (tablesDemoTick == 80 && backpackTablePos != null) {
			useBlock(client, backpackTablePos);
		}
		if (tablesDemoTick == 100) {
			TkGun.LOGGER.info("[tkgun-tables-demo] 背包改装台 screen={}",
					client.screen == null ? "null" : client.screen.getClass().getSimpleName());
			shot(client, "tkgun_backpack_table.png");
		}
	}

	private static void useBlock(Minecraft client, net.minecraft.core.BlockPos pos) {
		if (client.gameMode == null) {
			return;
		}
		var hit = new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(pos),
				net.minecraft.core.Direction.UP, pos, false);
		client.gameMode.useItemOn(client.player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);
	}

	/**
	 * 猎矛固定炮台的实机验收：发矛 → 右键部署 → 等炮台生成并开火。
	 *
	 * <p>部署走 `MechanicianHunterSpearItem#use` → `TkGunServer#deployHunterTurret`，
	 * 之后 `tickAll` 里的 `tickHunterSpearDeploy` 在 23 tick 后生成炮台。
	 */
	private static void tickHunterDemo(Minecraft client) {
		hunterDemoTick++;
		if (hunterDemoTick == 1 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			String name = client.player.getGameProfile().getName();
			server.execute(() -> {
				var source = server.createCommandSourceStack();
				// 世界是持久化的：上一轮演示留下的炮台会占住数量上限，先清掉。
				server.getCommands().performPrefixedCommand(source,
						"kill @e[type=tkgun:hunter_turret]");
				server.getCommands().performPrefixedCommand(source,
						"item replace entity " + name + " hotbar.0 with tkgun:hunter_spear");
				server.getCommands().performPrefixedCommand(source,
						"item replace entity " + name + " hotbar.1 with tkgun:bolt 64");
				server.getCommands().performPrefixedCommand(source, "clear " + name + " minecraft:dirt");
				TkGun.LOGGER.info("[tkgun-hunter-demo] 已发放猎矛");
			});
		}
		if (hunterDemoTick == 3) {
			client.player.getInventory().selected = 0;
			if (client.getConnection() != null) {
				client.getConnection().send(
						new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
			}
		}
		// 抬头（避免右键被方块交互吃掉），然后按住右键部署。
		if (hunterDemoTick == 20) {
			client.player.setXRot(-35.0F);
		}
		// ⚠️ 不能用 `keyUse.setDown(true)` 模拟右键：原版 `handleKeybinds` 用的是
		// `consumeClick()`（按下边沿），`setDown` 不产生边沿 → `Item#use` 永远不会被调用。
		// 直接走 `gameMode.useItem`，与玩家真的点右键是同一条 C2S 链路。
		if (hunterDemoTick == 26 && client.gameMode != null) {
			client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
			TkGun.LOGGER.info("[tkgun-hunter-demo] 已触发右键使用（部署）");
		}
		// 炮台要有目标才会开火：在正前方 8 格放一只僵尸。
		if (hunterDemoTick == 40 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			float yaw = client.player.getYRot();
			double dx = -Math.sin(Math.toRadians(yaw)) * 8.0D;
			double dz = Math.cos(Math.toRadians(yaw)) * 8.0D;
			double x = client.player.getX() + dx;
			double y = client.player.getY();
			double z = client.player.getZ() + dz;
			server.execute(() -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source,
						String.format(java.util.Locale.ROOT,
								"summon minecraft:zombie %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b}",
								x, y, z));
				TkGun.LOGGER.info("[tkgun-hunter-demo] 已召唤目标僵尸 ({}, {}, {})", Math.round(x), Math.round(y), Math.round(z));
			});
		}
		if (hunterDemoTick == 60 || hunterDemoTick == 140 || hunterDemoTick == 220) {
			var turrets = client.level.getEntitiesOfClass(
					com.mechaniciancombat.entity.HunterTurretEntity.class,
					client.player.getBoundingBox().inflate(24.0D));
			TkGun.LOGGER.info("[tkgun-hunter-demo] tick={} 炮台数={} 手上={}",
					hunterDemoTick, turrets.size(), client.player.getMainHandItem().getItem());
			if (!turrets.isEmpty()) {
				TkGun.LOGGER.info("[tkgun-hunter-demo] 炮台 fireSequence={}（>0 = 真的开过火）",
						turrets.get(0).fireSequence());
			}
			int bolts = client.level.getEntitiesOfClass(
					com.mechaniciancombat.entity.MoltenTridentEntity.class,
					client.player.getBoundingBox().inflate(32.0D)).size();
			TkGun.LOGGER.info("[tkgun-hunter-demo] 场上炮台弹丸={}", bolts);
			var monsters = client.level.getEntitiesOfClass(
					net.minecraft.world.entity.LivingEntity.class,
					client.player.getBoundingBox().inflate(16.0D),
					e -> e instanceof net.minecraft.world.entity.monster.Monster && e.isAlive());
			TkGun.LOGGER.info("[tkgun-hunter-demo] 附近存活怪物={} 玩家={}",
					monsters.size(), client.player.blockPosition());
			for (var m : monsters) {
				TkGun.LOGGER.info("[tkgun-hunter-demo]   目标 {} 位置={} 血={}",
						m.getType().toShortString(), m.blockPosition(), m.getHealth());
			}
			if (!turrets.isEmpty()) {
				TkGun.LOGGER.info("[tkgun-hunter-demo]   炮台位置={}",
						turrets.get(0).blockPosition());
			}
			// 把镜头对准炮台，否则截图里可能只有地面。
			if (!turrets.isEmpty()) {
				var turret = turrets.get(0);
				net.minecraft.world.phys.Vec3 eye = client.player.getEyePosition();
				net.minecraft.world.phys.Vec3 to = turret.position().add(0, 0.9D, 0).subtract(eye);
				double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
				client.player.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
				client.player.setYHeadRot(client.player.getYRot());
				client.player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, horiz)));
				TkGun.LOGGER.info("[tkgun-hunter-demo] 对准炮台 距离={} 格",
						Math.round(horiz * 10) / 10.0);
			}
			shot(client, "tkgun_hunter_" + hunterDemoTick + ".png");
		}
	}
	private static boolean ammoUiCaptured;
	private static int ammoUiTransferStage;
	private static int ammoUiTransferNextTick;
	private static net.minecraft.world.item.Item ammoUiExpectedItem;
	private static int ammoUiExpectedCount;
	private static int tableUiStage;
	private static int tableUiNextTick;

	public static void setRemoteEnabled(boolean on) {
		remoteEnabled = on;
		if (!on) {
			releaseKeys(Minecraft.getInstance());
			started = false;
			waitTicks = 0;
			tick = 0;
			ammoUiCaptured = false;
			ammoUiTransferStage = 0;
			ammoUiTransferNextTick = 0;
			ammoUiExpectedItem = null;
			ammoUiExpectedCount = 0;
			tableUiStage = 0;
			tableUiNextTick = 0;
		}
		TkGun.LOGGER.info("[tkgun-demo] 远程演示 {}", on ? "开启" : "关闭");
	}
	private static final Set<String> SHOT = new HashSet<>();
	private static int tick;
	private static boolean started;
	private static int waitTicks;
	/** 演示模式下的自动发放只做一次。 */
	private static boolean demoKitGranted;
	private static int bootTicks;
	private static String lastAnim = "";
	private static int shotDelay = -1;
	private static String shotName = "";

	public static void tick(Minecraft client) {
		if (!ENABLED && !LOYALTY_DEMO && !WIND_DEMO && !remoteEnabled) {
			return;
		}
		if (client.player == null || client.level == null) {
			autoConfirm(client);
			autoLogin(client);
			return;
		}
		if (LOYALTY_DEMO) {
			tickLoyaltyDemo(client);
			return;
		}
		if (WIND_DEMO) {
			tickWindDemo(client);
			return;
		}
		if (HUNTER_DEMO) {
			tickHunterDemo(client);
			return;
		}
		if (TABLES_DEMO) {
			tickTablesDemo(client);
			return;
		}
		if (BANNER_DEMO) {
			tickBannerDemo(client);
			return;
		}
		if (SOUL_ANCHOR_DEMO) {
			tickSoulAnchorDemo(client);
			return;
		}
		if (!started) {
			// 拿着"布景清理棒"时走另一条线：定期点一下左键，用来验证"左键点 NPC 才清掉"
			if (holdingCleanupWand(client)) {
				wandTick++;
				if (wandTick % 40 == 1) {
					TkGun.LOGGER.info("[tkgun-demo] 清理棒：点一下左键（tick {}）", wandTick);
					client.options.keyAttack.setDown(true);
				} else if (wandTick % 40 == 3) {
					client.options.keyAttack.setDown(false);
				}
				return;
			}
			// 等拿到枪再开始（单机自己发，联机由 RCON /item replace 发好）
			//
			// ⚠️ 必须先按 `DEMO_GUN` **强制发一次**再判断"拿没拿到"：
			// 世界是持久化的，上一轮演示留下的枪还在槽 0，`holdingGun` 一进世界就是 true，
			// 于是发枪分支被整个跳过 —— 指定 `-Ptkgun.demoGun=abyssal_ancient_cannon`
			// 会静默失效，验的还是旧枪（2026-11-22 踩过）。
			if (!demoKitGranted && client.getSingleplayerServer() != null) {
				grantDemoKit(client);
			}
			if (TkGun.holdingGun(client.player)) {
				started = true;
				ammoUiCaptured = false;
				tick = 0;
				releasedKeys = false;
				client.player.getInventory().selected = 0;
				TkGun.LOGGER.info("[tkgun-demo] 拿到武器，开始自动演示（第三人称）");
			} else {
				waitTicks++;
				// 发枪由上面的 `grantDemoKit` 负责（它在 `holdingGun` 判断之前无条件跑一次），
				// 这里只负责等客户端真正拿到手。
				// ⚠️ `/tkgun kit` 把炮塞在快捷栏 0，但玩家**当前选中的格子**可能是别的
				// （2026-11-22 实测服务端 `selected=8`）——那样服务端看到的"主手"不是枪：
				// 右键会去啃手上的东西、左键的开火包被忽略，演示白跑。
				//
				// ⚠️ 原来这里靠 `keyHotbarSlots[0].setDown(...)` 模拟按键，**不生效**：
				// 那条路要经过原版 `handleKeybinds`，而模组自己的 `FocusInputLockMixin`
				// 会在持枪/专注时整段取消它。现在直接发原版切换槽位包，绕开按键链。
				if (waitTicks % 20 == 1) {
					client.player.getInventory().selected = 0;
					if (client.getConnection() != null) {
						client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
					}
				}
				if (waitTicks % 100 == 0) {
					TkGun.LOGGER.info("[tkgun-demo] 等待手持破垒重炮（服务端用 /item replace 发到快捷栏 0）…");
				}
				return;
			}
		}
		tick++;
		if (BACKPACK_DEMO && tick == 1 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			var playerId = client.player.getUUID();
			server.execute(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player == null) return;
				player.serverLevel().setDayTime(6000L);
				var stack = new net.minecraft.world.item.ItemStack(
						com.mechaniciancombat.registry.ModRegistries.COMBAT_BACKPACK.get());
				com.mechaniciancombat.item.BackpackUpgradeData.installCore(stack);
				com.mechaniciancombat.item.BackpackUpgradeData.setLauncherType(stack,
						com.mechaniciancombat.item.BackpackUpgradeData.LauncherType.AUTO);
				top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve()
						.ifPresent(curios -> curios.setEquippedCurio("back", 0, stack));
				TkGun.LOGGER.info("[tkgun-backpack-demo] equipped auto-launcher backpack");
			});
		}
		if (BACKPACK_DEMO && tick == 80) {
			client.player.setXRot(5.0F);
			shot(client, "tkgun_backpack_ready.png");
		}
		if (BACKPACK_DEMO && tick == 85 && client.getSingleplayerServer() != null) {
			var server = client.getSingleplayerServer();
			var playerId = client.player.getUUID();
			server.execute(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player != null) player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 64));
			});
		}
		if (BACKPACK_DEMO && (tick == 100 || tick == 160)) {
			CombatBackpackClientState.trigger(client.player);
			TkGun.LOGGER.info("[tkgun-backpack-demo] toggle tick={} installed={}", tick,
					com.mechaniciancombat.item.BackpackUpgradeData.canUseLauncher(
							com.mechaniciancombat.item.CombatBackpackItem.equippedStack(client.player)));
		}
		if (BACKPACK_DEMO && (tick == 110 || tick == 125 || tick == 170)) {
			TkGun.LOGGER.info("[tkgun-backpack-demo] tick={} status={} animation={} charges={}", tick,
					CombatBackpackClientState.statusPhase(client.player.getId()),
					CombatBackpackClientState.phase(client.player.getId()),
					CombatBackpackClientState.charges(client.player.getId()));
			shot(client, "tkgun_backpack_" + tick + ".png");
		}
		if (BACKPACK_DEMO && tick == 180) {
			client.player.getInventory().selected = 1;
		}
		if (BACKPACK_DEMO && tick == 190) {
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			CombatBackpackClientState.trigger(client.player);
		}
		if (BACKPACK_DEMO && (tick == 200 || tick == 218)) {
			TkGun.LOGGER.info("[tkgun-backpack-demo] no-gun tick={} holding={} status={}", tick,
					TkGun.holdingGun(client.player), CombatBackpackClientState.statusPhase(client.player.getId()));
			shot(client, "tkgun_backpack_nogun_" + tick + ".png");
		}
		if (BACKPACK_DEMO && tick == 230) client.player.getInventory().selected = 0;
		if (tick == 5) {
			client.player.getInventory().selected = 0;
			client.options.keyHotbarSlots[0].setDown(true);   // 让**服务端**也知道切到了槽 0
			// 抬头看天：不然右键会被准星指着的方块吃掉（村庄里到处是交互方块），
			// 物品 use 根本不会触发，看起来像"举枪没反应"。
			client.player.setXRot(-55.0F);
		}
		if (tick == 8) {
			client.options.keyHotbarSlots[0].setDown(false);
		}
		// 背包界面（检查物品在 GUI 里长什么样）+ 定时截图
		if (tick == 26) {
			client.setScreen(new InventoryScreen(client.player));
		}
		// 把鼠标挪到快捷栏第 1 格的破垒重炮上 —— 这样工具提示会弹出来（LegendaryTooltips 的
		// 3D 模型预览就挂在提示框左上角，用户 2026-10-14 说的"左边预览的模型"就是它）。
		if (tick == 28 || tick == 30) {
			hoverSlot(client, 36);
		}
		if (tick == 32) {
			shot(client, "tkgun_gui_inventory.png");
		}
		if (tick == 34) {
			shot(client, "tkgun_gui_tooltip.png");
			client.setScreen(null);
		}
		// Exercise the production open-menu C2S request and capture the actual registered screen.
		if (UI_DEMO && tick == 35) {
			ClientNetwork.send(new com.mechaniciancombat.net.OpenAmmoStoragePayload());
			TkGun.LOGGER.info("[tkgun-ui] requested ammo_storage through production C2S payload");
		}
		if (UI_DEMO && tick >= 50 && tick <= 100 && !ammoUiCaptured) {
			if (tick == 50) {
				TkGun.LOGGER.info("[tkgun-ui] first poll screen={}",
						client.screen == null ? "<none>" : client.screen.getClass().getName());
			}
			if (client.screen instanceof com.mechaniciancombat.client.screen.AmmoStorageScreen) {
				shot(client, "tkgun_gui_ammo_storage.png");
				TkGun.LOGGER.info("[tkgun-ui] PASS ammo_storage");
				ammoUiCaptured = true;
				if (UI_TRANSFER_DEMO) {
					com.mechaniciancombat.client.screen.AmmoStorageScreen screen =
							(com.mechaniciancombat.client.screen.AmmoStorageScreen) client.screen;
					net.minecraft.world.item.ItemStack source = screen.acceptanceSlotStack(
							com.mechaniciancombat.screen.AmmoStorageScreenHandler.SIZE + 1);
					if (source.isEmpty() || source.getCount() != 64) {
						TkGun.LOGGER.error("[tkgun-ui] FAIL ammo_storage_click_roundtrip initial inventory slot count={}",
								source.getCount());
						client.screen.onClose();
					} else {
						ammoUiExpectedItem = source.getItem();
						ammoUiExpectedCount = source.getCount();
						ammoUiTransferStage = 1;
						ammoUiTransferNextTick = tick + 1;
						TkGun.LOGGER.info("[tkgun-ui] BEGIN ammo_storage_click_roundtrip count={}", ammoUiExpectedCount);
					}
				} else {
					client.screen.onClose();
				}
			} else if (tick == 100) {
				TkGun.LOGGER.error("[tkgun-ui] FAIL ammo_storage screen was not opened by the production C2S request");
			}
		}
		if (UI_TRANSFER_DEMO) {
			tickAmmoStorageClickRoundTrip(client);
		}
		if (UI_TABLE_DEMO) {
			tickBackpackTableUiAcceptance(client);
		}
		if (tick == 36) {
			// 用正式客户端 -> 服务端装填消息准备弹匣，确保后续自动开火阶段确实能发射。
			ClientNetwork.send(new GunReloadPayload());
			TkGun.LOGGER.info("[tkgun-demo] 已请求装填，准备验证真实开火与弹药消耗");
		}
		if (RECIPE_UI_DEMO) {
			// 走**玩家自己的**命令源（不是控制台源）：`/tkgun recipeeditor` 内部用
			// `getPlayerOrException()`，控制台源会直接失败。
			if (tick == 60 && client.getConnection() != null) {
				client.getConnection().sendCommand("tkgun recipeeditor");
			}
			if (tick == 90) {
				TkGun.LOGGER.info("[tkgun-ui] recipe editor screen={}",
						client.screen == null ? "null" : client.screen.getClass().getSimpleName());
				shot(client, "tkgun_recipe_editor.png");
			}
			if (tick == 110 && client.screen != null) {
				client.screen.onClose();
			}
		}
		// 截图工具/人手会抢焦点 → MC 弹暂停菜单，演示里直接关掉它，别让画面停在菜单上
		if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			client.setScreen(null);
		}
		if (tick % 25 == 0 && (tick <= 350 || tick >= 400)) {
			shot(client, "tkgun_t" + tick + "_" + sanitize(TkGunClient.currentAnimation(client.player.getUUID())) + ".png");
		}
		// 可视化验收脚本：右键举枪，Z 切换专注；专注期间移动输入被屏蔽，Alt 进入翻滚/滑铲循环。
		// 实际状态转移以服务端状态机和 /tkgun status 为准。
		boolean use = (tick > 40 && tick <= 75) || (tick >= 293 && tick <= 320);
		boolean attack = (tick >= 125 && tick <= 127) || (tick >= 140 && tick <= 142) || (tick >= 150 && tick <= 152)
				// **专注内开火**（蹲射验收的关键一段）：
				// 专注在 t≈292 由 Shift 进入，`Focus_ready → Focus_hold` 之后必须真的扣扳机，
				// 才会走到 `Focus_shooting*`。此前这里没有这一段，于是 demo 跑了 4000+ tick
				// 一次 `Focus_shooting` 都没出现 —— 蹲射的"射击瞬间是否站起"根本无从验收。
				|| (tick >= 300 && tick <= 302) || (tick >= 312 && tick <= 314);
		boolean move = (tick > 200 && tick <= 230) || (tick > 260 && tick <= 300);
		//
		// ⚠️ 按键脚本只在 tick <= 330 这一段运行，跑完**必须把按过的键全部松开并停止接管**：
		// 以前这段是无条件每 tick 执行的，脚本结束后仍会把 W/疾跑/潜行/右键强制设成"没按"，
		// 于是玩家自己怎么按 W 都没反应 —— 用户原话："老是禁用我的 w 按键"（2026-10-12）。
		if (tick <= 330) {
			client.options.keyUse.setDown(use);
			client.options.keyAttack.setDown(attack);
			client.options.keyUp.setDown(move);
			// 疾跑/滑铲都要"人在动"才成立：光按疾跑键、不按前进键，原版不会进疾跑
			client.options.keySprint.setDown(tick > 270 && tick <= 300);
			client.options.keyShift.setDown((tick > 290 && tick <= 300)
					|| (BACKPACK_DEMO && tick >= 210 && tick <= 220));
			// **专注内开火的确定性驱动**：在专注窗口里直接让服务端执行 `/tkgun fire`。
			//
			// 为什么不用 `keyAttack`：专注时 `FocusInputLockMixin` 会取消整段 `handleInputEvents`，
			// 模拟按键不一定被原版输入链消费（实测 abyssal 的按键注入没产生任何开火包）。
			// 走命令与玩家扣扳机最终落到同一个 `TkGunServer.tryFire`，只是绕开了输入链，
			// 用来验收"服务端说 FIRE + focus 时，客户端 rig 到底播什么"这件事完全够用。
			if (tick == 301 || tick == 313) {
				fireViaServer(client);
			}
		} else if (tick >= 400 && tick <= 520) {
			// —— 相机阶段（2026-10-14 用户要求：越肩视角 + 人物/摄像头解耦 + 右键转向 + 瞄准放大）——
			// 键位照旧的语义按（长按右键 = 瞄准），鼠标用 changeLookDirection 模拟（端到端走同一条路）。
			client.options.keyUse.setDown(tick >= 415 && tick <= 445);
			// 瞄准里也开两枪：验"指哪打哪"——服务端日志的 hitscan 方向应该带俯仰、落点≈准星落点
			client.options.keyAttack.setDown(tick >= 428 && tick <= 430 || tick >= 436 && tick <= 438);
			// 瞄准里插一段**前进**（425~443）：瞄准时身体和相机有个夹角，这时候走出来的方向
			// 才区分得开"相机相对"还是"身体相对"（都贴相机 = 解耦对了）；465~500 再来一段普通走动。
			client.options.keyUp.setDown((tick >= 425 && tick <= 443) || (tick >= 465 && tick <= 500));
			client.options.keySprint.setDown(false);
			client.options.keyShift.setDown(false);
			if (tick == 424) {
				lastCamMoveX = client.player.getX();
				lastCamMoveZ = client.player.getZ();
			}
			if (tick == 444) {
				logCamera(client, "⑤ 瞄准中按住 W（位移方向该贴**相机**，不贴身体）");
				assertCameraMove(client);
			}
			if (tick >= 400 && tick <= 410) {
				client.player.turn(3.0D, 0.0D);      // 模拟"鼠标往右推"
			}
			if (tick == 412) {
				logCamera(client, "① 没瞄准：自由环视 30°（身体应该纹丝不动）");
				shot(client, "tkgun_cam1_free.png");
			}
			// ⑥ 瞄准落点的"转头过程"（用户 2026-10-14："那个实际落点是可以缓慢过去的"）：
			//    按住右键的同时猛甩一下鼠标 —— 落点（圈圈）**不会**瞬间贴到准星上，
			//    而是按 GunCamera 的转头速度慢慢转过去。这几张截图就该看到圈圈从枪管方向滑向准星。
			if (tick >= 415 && tick <= 421) {
				client.player.turn(8.0D, 0.0D);      // 8°/tick × 7 tick ≈ 56° 的急甩
			}
			if (tick == 416 || tick == 419 || tick == 422 || tick == 426 || tick == 431 || tick == 445) {
				shot(client, "tkgun_aim_slew" + tick + ".png");
			}
			if (tick == 430) {
				logCamera(client, "② 长按右键中（身体应该正在追视角、FOV 应该收到 " + GunCamera.AIM_FOV + "）");
			}
			if (tick == 445) {
				logCamera(client, "③ 瞄准保持（落点应该已经转到位：圈圈和准星重合）");
				shot(client, "tkgun_cam2_aim.png");
			}
			if (tick >= 455 && tick <= 468) {
				client.player.turn(-3.0D, 0.0D);     // 松开之后再转回来 42°
			}
			if (tick == 462) {
				lastCamMoveX = client.player.getX();
				lastCamMoveZ = client.player.getZ();
			}
			if (tick == 495) {
				assertCameraMove(client);
			}
			if (tick == 501) {
				logCamera(client, "④ 走动中（W 应该按**相机**方向走，身体朝移动方向）");
				shot(client, "tkgun_cam3_walk.png");
			}
		} else if (tick >= 560 && tick <= 1500) {
			// —— 四方向走动 + 移动开火 + 强化阶段（2026-10-14 第二版素材）——
			//
			// 要验的东西 / 对应的键位安排：
			//   620~660 按住 A（左移）+ 630 扣扳机 → 期望 rig=walk_left，**上半身叠 stand_shooting**（移动开火）
			//   720~760 按住 D（右移）+ 730 扣扳机 → 期望 rig=walk_right，同上
			//   840~880 按住 S（后退）+ 850 扣扳机 → 期望 rig=walk_back，同上
			//   1010    站定扣扳机 → 期望 **stand_shooting 叠层**（2026-10-15：没进专注时是行走射击）
			//   1050    通过 HUD 技能桥或 Z 切换专注 → 期望进入 AIM 系专注状态
			//   1085    站定扣扳机 → 期望 Focus_shooting[N]（架着炮精瞄，按落点高度取 _22.5/_45）
			//   1130    专注中按侧移并扣扳机 → 期望移动输入被屏蔽，只有射击逻辑执行
			// ⚠️ 2026-10-15：**扣扳机必须先举枪**（右键按住才算瞄准，见 TkGunServer#tryFire），
			//    所以这一段里把右键按住（以前这里是一律松开）。
			client.options.keyUse.setDown(
					(tick >= 600 && tick <= 1180) || (tick >= 1380 && tick <= 1490));
			client.options.keySprint.setDown(false);
			client.options.keyShift.setDown(false);
			client.options.keyUp.setDown(false);
			client.options.keyAttack.setDown(
					(tick >= 630 && tick <= 632) || (tick >= 730 && tick <= 732) || (tick >= 850 && tick <= 852)
							|| (tick >= 1010 && tick <= 1012) || (tick >= 1085 && tick <= 1087)
							|| (tick >= 1130 && tick <= 1132)
							// 仰角三档（1410 抬头 / 1440 半抬头 / 1470 平视）
							|| (tick >= 1410 && tick <= 1412) || (tick >= 1440 && tick <= 1442)
							|| (tick >= 1470 && tick <= 1472));
			client.options.keyLeft.setDown((tick >= 620 && tick <= 660) || (tick >= 1120 && tick <= 1160));
			client.options.keyRight.setDown(tick >= 720 && tick <= 760);
			client.options.keyDown.setDown(tick >= 840 && tick <= 880);
			if (tick == 630) {
				TkGun.LOGGER.info("[tkgun-demo] ① 左移中开火（期望 walk_left 的腿 + stand_shooting 的上半身）");
			}
			if (tick == 730) {
				TkGun.LOGGER.info("[tkgun-demo] ② 右移中开火（期望 walk_right + stand_shooting）");
			}
			if (tick == 850) {
				TkGun.LOGGER.info("[tkgun-demo] ③ 后退中开火（期望 walk_back + stand_shooting）");
			}
			if (tick == 1010) {
				TkGun.LOGGER.info("[tkgun-demo] ④ 站定开火（期望腰射/行走射击：rig=idle|walk_* + 上半身 stand_shooting）");
			}
			if (tick == 1050) {
				// ⑤ 进入专注。
				//
				// ⚠️ 原来这里走 `pressHudSkillKey`（模拟外部 HUD 模组的技能键），
				// 但隔离验收客户端里**没有那个 mod**，按键永远找不到 —— 于是整段
				// Focus_* 从未播放（2026-11-22 实测：demo 跑了 4000+ tick，
				// `rig ->` 里一次 Focus 都没有）。
				//
				// 现在直接发专注包，与玩家按 Z 走的是同一条 C2S 链路
				// （`GunFocusPayload`），不依赖任何第三方按键绑定。
				ClientNetwork.send(new com.mechaniciancombat.net.GunFocusPayload(false));
				TkGun.LOGGER.info("[tkgun-demo] ⑤ 发送专注包（GunFocusPayload）；"
						+ "期望 rig=Focus_ready → Focus_hold + 服务端『进入专注状态』");
			}
			if (tick == 1085) {
				TkGun.LOGGER.info("[tkgun-demo] ⑥ 专注里开火（期望 Focus_shooting[N] + 仰角档，不再叠 stand_shooting）");
			}
			if (tick == 1130) {
				TkGun.LOGGER.info("[tkgun-demo] ⑦ 专注中侧移开火（期望移动输入被清零，专注射击继续）");
			}
			// —— 仰角变体（作者给每段射击都做了 基础/_22.5/_45）：抬头打的时候该切到 _45 ——
			// `changeLookDirection` 走的是原版鼠标那条路（解耦相机下 setPitch 会被 ShoulderSurfing 顶掉）。
			// ⚠️ 这里**必须用 `setPitch`**：实测 `changeLookDirection(0, dy)` 在这套输入环境下
			//    改不动俯仰角（2026-10-14 试过 -3 / -150 / +140 三档，实体俯仰纹丝不动），
			//    而 `setPitch` 一写就生效（-75 能稳定保持到开火那一 tick）。
			if (tick == 1400) {
				client.player.setXRot(-75.0F);                          // 抬头 75° → _45
			}
			if (tick == 1430) {
				client.player.setXRot(-25.0F);                          // 抬头 25° → _22.5
			}
			if (tick == 1460) {
				client.player.setXRot(0.0F);                            // 平视 → 不带后缀
			}
			if (tick >= 1400 && tick <= 1403) {
				client.player.turn(0.0D, -150.0D);        // 大幅抬头（≈ -22°/tick）
			}
			if (tick == 1410) {
				TkGun.LOGGER.info("[tkgun-demo] ⑧ 抬头 {}° 开火（期望 Focus_shooting[N]_45）",
						Math.round(-client.player.getXRot()));
			}
			if (tick >= 1430 && tick <= 1433) {
				client.player.turn(0.0D, 140.0D);         // 收回到 ~25°
			}
			if (tick == 1440) {
				TkGun.LOGGER.info("[tkgun-demo] ⑨ 抬头 {}° 开火（期望 _22.5）",
						Math.round(-client.player.getXRot()));
			}
			if (tick >= 1460 && tick <= 1463) {
				client.player.turn(0.0D, 60.0D);          // 接近平视
			}
			if (tick == 1470) {
				TkGun.LOGGER.info("[tkgun-demo] ⑩ 抬头 {}° 开火（期望不带后缀）",
						Math.round(-client.player.getXRot()));
			}
		} else if (tick > 1500 && !releasedKeys) {
			// ⚠️ 收尾要等**相机阶段 + 走动/强化阶段都跑完**（tick 1200）：早先这里一过 330 就把键全松开、
			//    远程演示也关掉了，结果新加的相机阶段（400~520）根本轮不到跑。
			releaseKeys(client);
			releasedKeys = true;
			// 远程演示（RCON 打开的那种）跑完就自己关掉：免得留着一个一直在按键盘的脚本
			// 把玩家的 W / 疾跑 / 右键占住（2026-10-12 用户反馈过）。
			if (remoteEnabled) {
				remoteEnabled = false;
			}
			TkGun.LOGGER.info("[tkgun-demo] 脚本结束，已把自动按键全部松开（控制权还给玩家）");
		}
		// 连点（同一段里左键连按三下）= 验证"排队不覆盖"，已经并进上面的 attack 脚本
		if (tick == 340) {
			requestDemoDodge(client, "翻滚");
		}
		if (tick == 375) {
			requestDemoDodge(client, "翻滚后的滑铲");
		}

		// 观察动画切换 -> 截图。
		// 用 **GunClientState 的动作状态**选择诊断动画标签；GeckoLib 评估枪械动画，
		// PlayerAnimator 将同帧身体姿势应用到原版 PlayerModel。
		String anim = GunClientState.animationName(client.player.getId());
		if (anim == null) {
			anim = TkGunClient.currentAnimation(client.player.getUUID());
		}
		if (anim == null) {
			anim = TkGun.holdingGun(client.player) ? "(vanilla)" : "(none)";
		}
		if (!anim.equals(lastAnim)) {
			TkGun.LOGGER.info("[tkgun-demo] STEP t={} 动画 -> {}", tick, anim);
			lastAnim = anim;
			shotDelay = 6;
			shotName = "tkgun_" + tick + "_" + anim.replace(":", "_") + ".png";
		}
		if (shotDelay > 0 && --shotDelay == 0) {
			shot(client, shotName);
		}
	}

	/** Performs pickup, deposit, extraction and return through the registered screen's real mouse handler. */
	private static void tickAmmoStorageClickRoundTrip(Minecraft client) {
		if (ammoUiTransferStage == 0 || ammoUiTransferStage < 0) {
			return;
		}
		if (!(client.screen instanceof com.mechaniciancombat.client.screen.AmmoStorageScreen screen)) {
			failAmmoStorageClickRoundTrip(client, "ammo screen closed before the round trip finished");
			return;
		}
		if (tick < ammoUiTransferNextTick) {
			return;
		}
		int inventorySlot = com.mechaniciancombat.screen.AmmoStorageScreenHandler.SIZE + 1;
		switch (ammoUiTransferStage) {
			case 1 -> {
				if (!screen.acceptanceClickSlot(inventorySlot)) {
					failAmmoStorageClickRoundTrip(client, "could not click the inventory source slot");
				} else {
					ammoUiTransferStage = 2;
					ammoUiTransferNextTick = tick + 4;
				}
			}
			case 2 -> {
				if (!isExpectedAmmo(screen.acceptanceCarriedStack())) {
					failAmmoStorageClickRoundTrip(client, "inventory pickup did not place all 64 rounds on the cursor");
				} else if (!screen.acceptanceClickSlot(0)) {
					failAmmoStorageClickRoundTrip(client, "could not click the first ammo-ring slot");
				} else {
					ammoUiTransferStage = 3;
					ammoUiTransferNextTick = tick + 4;
				}
			}
			case 3 -> {
				net.minecraft.world.item.ItemStack ring = screen.acceptanceSlotStack(0);
				net.minecraft.world.item.ItemStack carried = screen.acceptanceCarriedStack();
				TkGun.LOGGER.info("[tkgun-ui] click roundtrip after deposit: ring={}x{}, cursor={}x{}",
						itemName(ring), ring.getCount(), itemName(carried), carried.getCount());
				if (!isExpectedAmmo(ring) || !carried.isEmpty()) {
					failAmmoStorageClickRoundTrip(client, "deposit state mismatch: ring=" + itemName(ring) + "x"
							+ ring.getCount() + ", cursor=" + itemName(carried) + "x" + carried.getCount());
				} else if (!screen.acceptanceClickSlot(0)) {
					failAmmoStorageClickRoundTrip(client, "could not extract ammo from the ring");
				} else {
					ammoUiTransferStage = 4;
					ammoUiTransferNextTick = tick + 4;
				}
			}
			case 4 -> {
				if (!screen.acceptanceSlotStack(0).isEmpty()
						|| !isExpectedAmmo(screen.acceptanceCarriedStack())) {
					failAmmoStorageClickRoundTrip(client, "ring extraction did not return the full stack to the cursor");
				} else if (!screen.acceptanceClickSlot(inventorySlot)) {
					failAmmoStorageClickRoundTrip(client, "could not return the stack to its inventory slot");
				} else {
					ammoUiTransferStage = 5;
					ammoUiTransferNextTick = tick + 4;
				}
			}
			case 5 -> {
				if (!isExpectedAmmo(screen.acceptanceSlotStack(inventorySlot))
						|| !screen.acceptanceSlotStack(0).isEmpty()
						|| !screen.acceptanceCarriedStack().isEmpty()) {
					failAmmoStorageClickRoundTrip(client, "round trip changed the item count or left a stack on the cursor");
				} else {
					TkGun.LOGGER.info("[tkgun-ui] PASS ammo_storage_click_roundtrip count={}", ammoUiExpectedCount);
					shot(client, "tkgun_gui_ammo_storage_roundtrip.png");
					ammoUiTransferStage = 0;
					client.screen.onClose();
					if (UI_TABLE_DEMO) {
						tableUiStage = 1;
						tableUiNextTick = tick + 12;
						TkGun.LOGGER.info("[tkgun-table-ui] READY waiting for isolated table fixture");
					}
				}
			}
			default -> failAmmoStorageClickRoundTrip(client, "invalid click-roundtrip stage " + ammoUiTransferStage);
		}
	}

	private static boolean isExpectedAmmo(net.minecraft.world.item.ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == ammoUiExpectedItem
				&& stack.getCount() == ammoUiExpectedCount;
	}

	private static String itemName(net.minecraft.world.item.ItemStack stack) {
		return stack.isEmpty() ? "empty"
				: net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static void failAmmoStorageClickRoundTrip(Minecraft client, String reason) {
		TkGun.LOGGER.error("[tkgun-ui] FAIL ammo_storage_click_roundtrip: {}", reason);
		ammoUiTransferStage = -1;
		if (client.screen != null) {
			client.screen.onClose();
		}
	}

	/**
	 * Exercise the production block interaction, registered screen, item filters,
	 * slot synchronization, and backpack component NBT in the isolated UI client.
	 */
	private static void tickBackpackTableUiAcceptance(Minecraft client) {
		if (tableUiStage == 0 || tableUiStage < 0 || tick < tableUiNextTick) {
			return;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			failBackpackTableUi(client, "client world/player/game mode unavailable");
			return;
		}
		boolean playerG = client.player.getGameProfile().getName().endsWith("G");
		net.minecraft.core.BlockPos tablePos = new net.minecraft.core.BlockPos(playerG ? -5 : 5, 100, 3);
		switch (tableUiStage) {
			case 1 -> {
				if (!client.level.getBlockState(tablePos).is(
						com.mechaniciancombat.registry.ModRegistries.BACKPACK_TABLE_BLOCK.get())) {
					failBackpackTableUi(client, "isolated table fixture was not placed");
					return;
				}
				var hit = new net.minecraft.world.phys.BlockHitResult(
						net.minecraft.world.phys.Vec3.atCenterOf(tablePos),
						net.minecraft.core.Direction.NORTH, tablePos, false);
				var result = client.gameMode.useItemOn(
						client.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				TkGun.LOGGER.info("[tkgun-table-ui] requested production block use result={}", result);
				tableUiStage = 2;
				tableUiNextTick = tick + 12;
			}
			case 2 -> {
				if (!(client.screen instanceof com.mechaniciancombat.client.screen.BackpackModificationTableScreen screen)) {
					failBackpackTableUi(client, "production block use did not open BackpackModificationTableScreen");
					return;
				}
				TkGun.LOGGER.info("[tkgun-table-ui] PASS opened production modification table screen");
				shot(client, "tkgun_gui_backpack_table.png");
				if (screen.acceptanceSlotStack(BackpackModificationTableScreenHandler.HOTBAR_SLOT_START + 2)
						.isEmpty()) {
					failBackpackTableUi(client, "test backpack is missing from hotbar slot 2");
					return;
				}
				tableUiStage = 3;
				tableUiNextTick = tick + 1;
			}
			case 3 -> tableClick(client, 34, 4);
			case 4 -> tableClick(client, BackpackModificationTableScreenHandler.BACKPACK_SLOT_INDEX, 5);
			case 5 -> {
				var screen = backpackTableScreen(client);
				if (screen == null || !(screen.acceptanceSlotStack(
						BackpackModificationTableScreenHandler.BACKPACK_SLOT_INDEX).getItem()
						instanceof com.mechaniciancombat.item.CombatBackpackItem)
						|| !screen.acceptanceCarriedStack().isEmpty()) {
					failBackpackTableUi(client, "backpack did not synchronize into the table slot");
					return;
				}
				if (!screen.acceptanceClickSlot(35)) {
					failBackpackTableUi(client, "could not pick up backpack core");
					return;
				}
				tableUiStage = 6;
				tableUiNextTick = tick + 4;
			}
			case 6 -> tableClick(client, BackpackModificationTableScreenHandler.FIRST_PART_SLOT_INDEX, 7);
			case 7 -> {
				var screen = backpackTableScreen(client);
				if (screen == null || screen.acceptanceSlotStack(
						BackpackModificationTableScreenHandler.FIRST_PART_SLOT_INDEX).isEmpty()
						|| !screen.acceptanceCarriedStack().isEmpty()) {
					failBackpackTableUi(client, "backpack core did not synchronize into its component slot");
					return;
				}
				if (!screen.acceptanceClickSlot(36)) {
					failBackpackTableUi(client, "could not pick up portable launcher");
					return;
				}
				tableUiStage = 8;
				tableUiNextTick = tick + 4;
			}
			case 8 -> tableClick(client,
					BackpackModificationTableScreenHandler.FIRST_PART_SLOT_INDEX
							+ BackpackModificationTableScreenHandler.LAUNCHER_PART_SLOT, 9);
			case 9 -> {
				var screen = backpackTableScreen(client);
				if (screen == null || !(screen.acceptanceSlotStack(
						BackpackModificationTableScreenHandler.BACKPACK_SLOT_INDEX).getItem()
								instanceof com.mechaniciancombat.item.CombatBackpackItem)
						|| !screen.acceptanceSlotStack(
							BackpackModificationTableScreenHandler.FIRST_PART_SLOT_INDEX).is(
								com.mechaniciancombat.registry.ModRegistries.BACKPACK_CORE.get())
						|| !screen.acceptanceSlotStack(
							BackpackModificationTableScreenHandler.FIRST_PART_SLOT_INDEX
									+ BackpackModificationTableScreenHandler.LAUNCHER_PART_SLOT).is(
								com.mechaniciancombat.registry.ModRegistries.PORTABLE_LAUNCHER.get())
						|| !screen.acceptanceCarriedStack().isEmpty()) {
					failBackpackTableUi(client, "table core/launcher slots or carried stack did not synchronize");
					return;
				}
				var backpack = screen.acceptanceSlotStack(
						BackpackModificationTableScreenHandler.BACKPACK_SLOT_INDEX);
				if (!com.mechaniciancombat.item.BackpackUpgradeData.hasCore(backpack)
						|| !com.mechaniciancombat.item.BackpackUpgradeData.hasLauncher(backpack)
						|| com.mechaniciancombat.item.BackpackUpgradeData.launcherType(backpack)
								!= com.mechaniciancombat.item.BackpackUpgradeData.LauncherType.PORTABLE) {
					failBackpackTableUi(client, "backpack NBT did not persist the installed core and launcher");
					return;
				}
				TkGun.LOGGER.info("[tkgun-table-ui] PASS backpack/core/portable launcher synchronized and saved");
				shot(client, "tkgun_gui_backpack_table_installed.png");
				tableUiStage = 0;
				client.screen.onClose();
			}
			default -> failBackpackTableUi(client, "invalid table UI acceptance stage " + tableUiStage);
		}
	}

	private static com.mechaniciancombat.client.screen.BackpackModificationTableScreen backpackTableScreen(
			Minecraft client) {
		return client.screen instanceof com.mechaniciancombat.client.screen.BackpackModificationTableScreen screen
				? screen : null;
	}

	private static void tableClick(Minecraft client, int slot, int nextStage) {
		var screen = backpackTableScreen(client);
		if (screen == null || !screen.acceptanceClickSlot(slot)) {
			failBackpackTableUi(client, "mouse click failed for menu slot " + slot);
			return;
		}
		tableUiStage = nextStage;
		tableUiNextTick = tick + 4;
	}

	private static void failBackpackTableUi(Minecraft client, String reason) {
		TkGun.LOGGER.error("[tkgun-table-ui] FAIL {}", reason);
		tableUiStage = -1;
		if (client.screen != null) {
			client.screen.onClose();
		}
	}

	/** 仅用于隔离验收：按住原版使用键后松开，实际投掷带忠诚的熔铁弩矢。 */
	private static int loyaltyDemoTick;
	private static void tickLoyaltyDemo(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}
		if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
				client.player.getMainHandItem().getItem()).equals(TkGun.id("bolt"))) {
			client.player.getInventory().selected = 0;
			client.options.keyHotbarSlots[0].setDown(loyaltyDemoTick % 20 < 2);
			return;
		}
		loyaltyDemoTick++;
		client.options.keyHotbarSlots[0].setDown(false);
		if (loyaltyDemoTick == 1) {
			client.player.setYRot(0.0F);
			client.player.setYHeadRot(0.0F);
			client.player.setXRot(0.0F);
			TkGun.LOGGER.info("[tkgun-loyalty-demo] 开始原版使用输入投掷；主手物品={}，持续按键 30 tick",
					client.player.getMainHandItem());
		}
		client.options.keyUse.setDown(loyaltyDemoTick >= 5 && loyaltyDemoTick <= 34);
		if (loyaltyDemoTick == 5) {
			TkGun.LOGGER.info("[tkgun-loyalty-demo] 按下右键开始蓄力");
		}
		if (loyaltyDemoTick == 34) {
			TkGun.LOGGER.info("[tkgun-loyalty-demo] 松开右键，等待弹体命中/忠诚返航");
		}
		if (loyaltyDemoTick == 40) {
			shot(client, "tkgun_loyalty_throw.png");
		}
	}

	/** 仅用于隔离验收：使用一次本项目风弹，并将碰撞/击退留给原版客户端与专服链路。 */
	private static int windDemoTick;
	private static void tickWindDemo(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}
		if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
				client.player.getMainHandItem().getItem()).equals(TkGun.id("wind_charge"))) {
			client.player.getInventory().selected = 0;
			client.options.keyHotbarSlots[0].setDown(windDemoTick % 20 < 2);
			return;
		}
		windDemoTick++;
		client.options.keyHotbarSlots[0].setDown(false);
		if (windDemoTick == 1) {
			client.player.setYRot(0.0F);
			client.player.setYHeadRot(0.0F);
			client.player.setXRot(0.0F);
			TkGun.LOGGER.info("[tkgun-wind-demo] 使用本项目风弹，主手数量={}，朝向 yaw=0/pitch=0",
					client.player.getMainHandItem().getCount());
		}
		client.options.keyUse.setDown(false);
		if (windDemoTick == 2) {
			var result = client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
			TkGun.LOGGER.info("[tkgun-wind-demo] 通过客户端物品使用入口投掷风弹，结果={}", result);
		}
		if (windDemoTick == 30) {
			shot(client, "tkgun_wind_charge_throw.png");
		}
	}

	private static String sanitize(String anim) {
		return anim == null ? "none" : anim.replace(":", "_");
	}

	private static int wandTick;

	/**
	 * 按一下 **hud mod 的技能键**（`key.hud.skill<N>`）。
	 *
	 * <p>为什么要这样：技能键是 hud mod 注册的 KeyBinding，tkgun 这边拿不到那个对象，
	 * 但 `client.options.allKeys` 里**所有**注册过的键位都在 —— 按翻译键找出来设成按下就行，
	 * hud 的客户端 tick 会照常走它那条"按键 → 查蓝/冷却 → 发包"的路。
	 */
	private static boolean pressHudSkillKey(Minecraft client, int slot) {
		String want = "key.hud.skill" + slot;
		boolean found = false;
		for (KeyMapping kb : client.options.keyMappings) {
			if (want.equals(kb.getName())) {
				kb.setDown(true);
				found = true;
			}
		}
		return found;
	}

	/** 松开所有 hud 技能键（按一下就得放，不然 hud 那边会一直当按住）。 */
	private static void releaseHudSkillKeys(Minecraft client) {
		for (KeyMapping kb : client.options.keyMappings) {
			String key = kb.getName();
			if (key != null && key.startsWith("key.hud.skill")) {
				kb.setDown(false);
			}
		}
	}

	/** 自动脚本按过 / 压过的键；脚本结束或远程关闭时全部松开，控制权还给玩家。 */
	private static boolean releasedKeys = true;

	private static void releaseKeys(Minecraft client) {
		if (client == null) {
			return;
		}
		client.options.keyUse.setDown(false);
		client.options.keyUp.setDown(false);
		client.options.keySprint.setDown(false);
		client.options.keyShift.setDown(false);
		client.options.keyAttack.setDown(false);
		releaseHudSkillKeys(client);
	}

	/** 主手是不是布景清理棒（按物品 id 判，不引 tkm 的类）。 */
	private static boolean holdingCleanupWand(Minecraft client) {
		if (client.player == null) {
			return false;
		}
		var stack = client.player.getMainHandItem();
		if (stack.isEmpty()) {
			return false;
		}
		return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
				.equals("tkm:cleanup_wand");
	}

	/**
	 * 把真实光标挪到某个 GUI 槽位上（走反射：`Mouse.x/y` 是 private，`HandledScreen.x/y` 是 protected）。
	 * `HandledScreen.render` 每帧用真实鼠标坐标算 `focusedSlot`，所以挪了光标工具提示就会弹。
	 * 槽位用**玩家背包的槽号**（36 = 快捷栏第 1 格），坐标 = 屏幕原点 + 槽位自身坐标 + 8（槽心）。
	 */
	private static void hoverSlot(Minecraft client, int handlerIndex) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		try {
			Slot slot = screen.getMenu().getSlot(handlerIndex);
			int ox = intField(screen, "x");
			int oy = intField(screen, "y");
			double lx = ox + slot.x + 8.0;
			double ly = oy + slot.y + 8.0;
			// Mouse.x/y 存的是窗口原始像素 -> 按 GUI 缩放折算回去
			double fx = (double) client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth();
			double fy = (double) client.getWindow().getHeight() / client.getWindow().getGuiScaledHeight();
			setDouble(client.mouseHandler, "x", lx * fx);
			setDouble(client.mouseHandler, "y", ly * fy);
			TkGun.LOGGER.info("[tkgun-demo] 光标悬停槽 {} 逻辑=({}, {}) 设备=({}, {}) 物品={}",
					handlerIndex, lx, ly, lx * fx, ly * fy, slot.getItem().getItem());
		} catch (Throwable t) {
			TkGun.LOGGER.warn("[tkgun-demo] 悬停槽 {} 失败: {}", handlerIndex, t.toString());
		}
	}

	private static int intField(Object o, String name) throws Exception {
		return field(o, name, INT_ALIASES).getInt(o);
	}

	private static void setDouble(Object o, String name, double value) throws Exception {
		field(o, name, DOUBLE_ALIASES).setDouble(o, value);
	}

	// ⚠️ **线上跑的是 intermediary 名**：反射写 yarn 名（x / y）会抛 NoSuchFieldException
	// （2026-10-14 实测："悬停槽 36 失败: java.lang.NoSuchFieldException: x"）。
	// 名字来自 `moddev\tools\yarn-1.21.1.tiny`：Mouse.x=field_1795, y=field_1794,
	// HandledScreen.x=field_2776, y=field_2800。两个名字都试，开发/线上都能跑。
	private static final String[][] INT_ALIASES = {{"x", "field_2776"}, {"y", "field_2800"}};
	private static final String[][] DOUBLE_ALIASES = {{"x", "field_1795"}, {"y", "field_1794"}};

	private static Field field(Object o, String name, String[][] aliases) throws Exception {
		String[] names = {name};
		for (String[] pair : aliases) {
			if (pair[0].equals(name)) {
				names = pair;
			}
		}
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			for (String n : names) {
				try {
					Field f = c.getDeclaredField(n);
					f.setAccessible(true);
					return f;
				} catch (NoSuchFieldException ignored) {
					// 换下一个名字 / 往父类找
				}
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				msg -> TkGun.LOGGER.info("[tkgun-demo] 截图 {} ({})", name, msg.getString()));
	}

	// ---------------------------------------------------------------- 相机阶段的小工具
	/** 相机阶段上一次记的位置（验"W 是按相机方向走"）。 */
	private static double lastCamMoveX;
	private static double lastCamMoveZ;

	/** 打一行"越肩/解耦"的实测数据（视角、身体、差、FOV 系数），日志里一眼能看出对不对。 */
	private static void logCamera(Minecraft client, String what) {
		TkGun.LOGGER.info("[tkgun-demo] {}：相机 yaw={} 身体 yaw={} 差={}° 瞄准={} FOV系数={} 视角={} 朝向={}",
				what, round(GunCamera.lookYaw()), round(client.player.getYRot()),
				round(net.minecraft.util.Mth.wrapDegrees(
						GunCamera.lookYaw() - client.player.getYRot())),
				GunCamera.aiming(client), round(GunCamera.fovScale()),
				client.options.getCameraType(), dirOf(client));
	}

	/**
	 * 验"走动 = 相机相对"：这一段按的是 W，所以世界位移方向应该 ≈ **视角 yaw**
	 * （如果输入没被扭回身体坐标系，就会是**身体 yaw** —— 那说明解耦那条数学错了）。
	 */
	private static void assertCameraMove(Minecraft client) {
		double dx = client.player.getX() - lastCamMoveX;
		double dz = client.player.getZ() - lastCamMoveZ;
		double moved = Math.sqrt(dx * dx + dz * dz);
		double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
		TkGun.LOGGER.info("[tkgun-demo] 走动验算：实际位移={} 格 位移方向 yaw={}° / 相机 yaw={}° / 身体 yaw={}°"
						+ "（位移方向该贴相机；贴身体就说明输入没扭对）",
				round(moved), round(moveYaw), round(GunCamera.lookYaw()),
				round(client.player.getYRot()));
	}

	private static String dirOf(Minecraft client) {
		var v = client.player.getViewVector(1.0F);
		return "(" + round(v.x) + ", " + round(v.z) + ")";
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	/**
	 * 自动点「登 录」（只在 -Dtkgun.demo=1 的开发演示里跑）。
	 *
	 * TkAuth 的登录页按用户要求是"填好账号密码、但让人自己点登录"，所以自动化演示必须
	 * 补这一下，否则客户端会一直停在登录页。这里不引 TkAuth 的类（不是依赖），只按按钮
	 * 文案找。同一个 Screen 实例只点一次，避免反复触发。
	 */
	private static Object lastLoginScreen;
	/**
	 * ⚠️ 登录只自动点，但要**限流**：同一个 Screen 实例只点一次，两次点击至少隔 5 秒，最多 6 次。
	 * （早先那版是"整局只点一次"，结果 quickplay 被 tkauth 踢回来后偶发点不中 → 客户端一直卡在登录页，
	 *  2026-10-12 因此白等过好几轮；无限重试又会变成一秒十几个连接把服务端刷成
	 *  `lost connection: Disconnected`，两头都不能要。）
	 */
	private static int loginAttempts;
	private static long lastLoginClickAt;

	private static boolean autoLogin(Minecraft client) {
		Screen screen = client.screen;
		// 同一个屏幕实例也允许**重试**（点不中是很常见的：quickplay 被踢回来那一瞬间按钮还没就绪），
		// 靠上面的 5 秒节流 + 6 次上限兜住，不会变成刷连接。
		if (screen == null || loginAttempts >= 6) {
			return false;
		}
		// ⚠️ 2026-10-14：带着 `-Dtk.dev.autologin` 起的时候（launch_client.py --dev）
		//    TkAuth 自己会登录进服，这里再点一次 = **同一个账号两条连接** → 服务端把先到的那条踢掉
		//    （实测 `lost connection: Disconnected`，然后卡在登录页）。有那个参数就交给它，别插手。
		if (System.getProperty("tk.dev.autologin") != null) {
			return false;
		}
		// ⚠️ 2026-10-13 实测：5 秒节流太短 —— 连接要 8 秒左右才进世界，第二次点击会
		//    又发一条登录（同一个账号第二个连接），服务端把前一个踢掉，客户端拿到
		//    "你已在异地登录"直接掉线。拉到 20 秒后就不会点了重复的。
		if (System.currentTimeMillis() - lastLoginClickAt < 20000L) {
			return false;
		}
		for (GuiEventListener e : screen.children()) {
			if (e instanceof Button b) {
				String label = b.getMessage().getString().replace(" ", "");
				if (label.contains("登录") || label.toLowerCase().contains("login")) {
					lastLoginScreen = screen;
					loginAttempts++;
					lastLoginClickAt = System.currentTimeMillis();
					TkGun.LOGGER.info("[tkgun-demo] 自动点「{}」进服", b.getMessage().getString());
					b.onPress();
					return true;
				}
			}
		}
		// TkAuth 的登录页是**即时模式**手绘按钮（不是 ButtonWidget），只能按它的布局算坐标点。
		// 布局：PANEL_W=320，panelX=(w-320)/2，panelY=max(28, h/2-121-12)；
		//       登录按钮在 (panelX+34, panelY+190)，尺寸 118x24。
		String cls = screen.getClass().getName();
		if (cls.contains("LoginScreen")) {
			int panelX = (screen.width - 320) / 2;
			int panelY = Math.max(28, screen.height / 2 - 121 - 12);
			int x = panelX + 34 + 59;
			int y = panelY + 190 + 12;
			lastLoginScreen = screen;
			loginAttempts++;
			lastLoginClickAt = System.currentTimeMillis();
			TkGun.LOGGER.info("[tkgun-demo] 自动点登录按钮 @({},{}) [{}]", x, y, cls);
			screen.mouseClicked(x, y, 0);
			return true;
		}
		return false;
	}

	private static void requestDemoDodge(Minecraft client, String label) {
		// 生产输入读取 GLFW 物理键状态，KeyMapping#setDown 无法模拟该输入。
		// 演示改发同一个 C2S 请求，走服务端正式闪避状态机与动画广播；本地预测只用于首帧。
		ClientNetwork.send(TkGunClient.rollInput(client));
		if ("翻滚".equals(label)) {
			GunClientState.beginLocalRoll(client.player.getId());
		}
		TkGun.LOGGER.info("[tkgun-demo] 通过正式翻滚请求触发：{}", label);
	}

	/** 测试世界带"实验性世界设置"确认框，自动点第一个按钮（= 继续加载）。 */
	private static boolean autoConfirm(Minecraft client) {
		if (client.screen instanceof ConfirmScreen screen) {
			for (GuiEventListener e : screen.children()) {
				if (e instanceof Button b) {
					TkGun.LOGGER.info("[tkgun-demo] 自动确认对话框：「{}」", b.getMessage().getString());
					b.onPress();
					return true;
				}
			}
		}
		return false;
	}

	private GunDebugHarness() {
	}
}
