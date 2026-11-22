package com.mechaniciancombat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import com.mechaniciancombat.net.GunDemoPayload;
import com.mechaniciancombat.net.ModNetwork;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** /tkgun —— 调试/运维用：查状态、强制状态、补弹。 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
		modid = TkGun.MOD_ID,
		bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class TkGunCommand {
	public static void register() {
		// 命令由 Forge 的 RegisterCommandsEvent 自动挂接。
	}

	@net.minecraftforge.eventbus.api.SubscribeEvent
	public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
		build(event.getDispatcher());
	}

	private static void build(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("tkgun").requires(s -> s.hasPermission(2))
				.then(Commands.literal("status")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									TkGunServer.PState st = TkGunServer.state(p);
									ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
											"tkgun %s: state=%s 剩余=%d 弹夹=%d/%d 第%d发动画=%s 连射=%d 热=%.1f 过热=%b 队列=%d 滑铲=%d 专注=%b",
											p.getName().getString(), st.state,
											Math.max(0, st.stateUntil - p.level().getGameTime()),
											TkGunServer.activeMag(st), TkGun.magazineCapacity(st.deployed), st.shotIndex,
											TkGun.shotAnimation(st.shotIndex), st.combo, st.heat,
											TkGunServer.isOverheated(p), st.shotQueue.size(), st.slideUntil,
											TkGunServer.focused(p))), false);
									return 1;
								})))
				// /tkgun focus <player> —— 进/出**专注（架枪）**状态；技能「破垒重炮·专注」就是调这个。
				// ⚠️ `over` 保留成同一个东西的别名：老 hud jar（技能还叫"强化"那版）喊的是 `tkgun over`，
				//    两边 jar 不同步时至少不会"按了没反应"。
				.then(Commands.literal("focus")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									boolean on = TkGunServer.startFocus(p);
									ctx.getSource().sendSuccess(() -> Component.literal(
											"tkgun: 专注 " + (on ? "开启" : "解除/被拒") + " -> "
													+ p.getName().getString()), false);
									return on ? 1 : 0;
								})
								.then(Commands.literal("off")
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											TkGunServer.stopFocus(p);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"tkgun: 专注解除 -> " + p.getName().getString()), false);
											return 1;
										}))))
				.then(Commands.literal("over")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									boolean on = TkGunServer.startFocus(p);
									ctx.getSource().sendSuccess(() -> Component.literal(
											"tkgun: 专注 " + (on ? "开启" : "解除/被拒")
													+ "（over 是旧别名）-> " + p.getName().getString()), false);
									return on ? 1 : 0;
								})
								.then(Commands.literal("off")
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											TkGunServer.stopFocus(p);
											return 1;
										}))))
				.then(Commands.literal("state")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word())
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "state");
											TkGun.State s = TkGun.State.valueOf(name.toUpperCase(Locale.ROOT));
											int ticks = switch (s) {
												case FIRE -> TkGun.FIRE_TICKS;
												case OVERHEAT -> TkGun.OVERHEAT_ANIM_TICKS;
												case INSPECT -> TkGun.INSPECT_TICKS;
												case SLIDE -> TkGun.SLIDE_TICKS;
												case AIM_HOLD -> 600;   // 保持姿势：给足时间，等玩家动
												case ROLL -> com.mechaniciancombat.CombatRollHook.ROLL_DURATION;
												default -> 20;
											};
											TkGunServer.forceState(p, s, ticks);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"tkgun: " + p.getName().getString() + " -> " + s), false);
											return 1;
										}))))
				.then(Commands.literal("fire")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									TkGunServer.tryFire(p, true);
									return 1;
								})))
				// /tkgun probe <player> —— 开一枪，并让服务端在**同一 tick**把弹丸位置反解成
				// "枪口相对玩家的偏移"打进日志（RCON 往返太慢，弹丸早飞走了/撞地了，测得准）。
				.then(Commands.literal("probe")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									TkGunServer.PROBE_NEXT_SHOT = true;
									TkGunServer.tryFire(p, true);
									return 1;
								})))
				// /tkgun slide <player> —— 直接起一次滑铲（跳过"疾跑+蹲"的输入判定），
				// 用来验收滑铲位移（服务端会打「滑铲结束：实测位移 …」到日志）
				.then(Commands.literal("slide")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									TkGunServer.forceSlide(p);
									ctx.getSource().sendSuccess(() -> Component.literal(
											"tkgun: 强制滑铲 -> " + p.getName().getString()), false);
									return 1;
								})))
				// /tkgun burst <player> <n> —— 一次入队 n 发，用来验收"连发排队不覆盖"
				.then(Commands.literal("burst")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n");
											int added = TkGunServer.queueShots(p, n);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"tkgun: 入队 " + added + " 发 -> " + p.getName().getString()), false);
											return added;
										}))))
				// /tkgun kit <player> —— 把武器+箭塞进快捷栏 0/1（联机验证用）
				.then(Commands.literal("kit")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									return grantKit(EntityArgument.getPlayer(ctx, "player"), TkGunServer.BOWGUN);
								})
								// /tkgun kit <player> <variant> —— 指定枪种。
								// 蹲射 bug 只出现在 abyssal / veritas，验收时必须能单独发这两把，
								// 否则只验到本来就没问题的 bowgun（2026-11-22 踩过）。
								.then(Commands.argument("variant", com.mojang.brigadier.arguments.StringArgumentType.word())
										.suggests((ctx, builder) -> {
											builder.suggest("bowgun");
											builder.suggest("abyssal_ancient_cannon");
											builder.suggest("veritas_et_justitia");
											return builder.buildFuture();
										})
										.executes(ctx -> grantKit(EntityArgument.getPlayer(ctx, "player"),
												gunFor(com.mojang.brigadier.arguments.StringArgumentType
														.getString(ctx, "variant")))))))
				.then(Commands.literal("recipeeditor")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							net.minecraftforge.network.NetworkHooks.openScreen(player,
									new net.minecraft.world.SimpleMenuProvider(
											(containerId, inventory, menuPlayer) ->
													new com.mechaniciancombat.screen.RecipeEditorScreenHandler(
															containerId, inventory),
											Component.translatable("screen.tkgun.recipe_editor.title")));
							return 1;
						}))
				// /tkgun demo <player> <on|off> —— 远程开关客户端的自动演示（自己按键跑全套动作并截图）
				.then(Commands.literal("demo")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
											ModNetwork.send(p, new GunDemoPayload(on));
											ctx.getSource().sendSuccess(() -> Component.literal(
													"tkgun: 演示 " + (on ? "开启" : "关闭") + " -> " + p.getName().getString()), false);
											return 1;
										})))));
	}

	private TkGunCommand() {
	}

	/** 把枪种名字映射成物品（未知名字回落到 bowgun，并打一条 warn）。 */
	private static net.minecraft.world.item.Item gunFor(String variant) {
		if (variant != null) {
			switch (variant) {
				case "abyssal_ancient_cannon":
					return com.mechaniciancombat.registry.ModRegistries.ABYSSAL_ANCIENT_CANNON.get();
				case "veritas_et_justitia":
					return com.mechaniciancombat.registry.ModRegistries.VERITAS_ET_JUSTITIA.get();
				case "kinetic_sword":
					return com.mechaniciancombat.registry.ModRegistries.KINETIC_SWORD.get();
				case "bowgun":
					break;
				default:
					TkGun.LOGGER.warn("[tkgun] kit: 未知枪种 {}，回落到 bowgun", variant);
					break;
			}
		}
		return TkGunServer.BOWGUN;
	}

	/**
	 * 发放一套枪械验收装备并**让客户端真的拿在手上**。
	 *
	 * <p>三个步骤缺一不可（都踩过坑）：
	 * <ol>
	 *   <li>`setItem(0, ...)` —— 塞物品；</li>
	 *   <li>`selected = 0` + `ClientboundSetCarriedItemPacket` —— 切选中槽位，
	 *       否则玩家手上还是原来那一格（实测 `selected=8`），`holdingGun` 为 false；</li>
	 *   <li>`broadcastFullState()` —— 把整套容器推给客户端，
	 *       只改 Inventory 不会同步。</li>
	 * </ol>
	 */
	private static int grantKit(ServerPlayer p, net.minecraft.world.item.Item gun) {
		ItemStack stack = new ItemStack(gun);
		// **弹药环必须一起初始化**：只给背包塞箭不够 —— 开火时服务端从枪的弹药环
		// （`GunAmmoData` 的 NBT）取弹，环是空的就一直"空仓"静默拒绝，
		// 表现是"扣了扳机但一发都没出"（2026-11-22 踩过：demo 跑了几千 tick 没有一次开火）。
		net.minecraft.core.NonNullList<ItemStack> ring = net.minecraft.core.NonNullList
				.withSize(com.mechaniciancombat.item.GunAmmoData.SIZE, ItemStack.EMPTY);
		ring.set(0, new ItemStack(TkGunServer.BOLT, 64));
		com.mechaniciancombat.item.GunAmmoData.save(stack, ring, 0, p.server.registryAccess());
		p.getInventory().setItem(0, stack);
		p.getInventory().setItem(1, new ItemStack(TkGunServer.BOLT, 64));
		p.getInventory().selected = 0;
		p.connection.send(
				new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
		p.inventoryMenu.broadcastFullState();
		if (p.containerMenu != p.inventoryMenu) {
			p.containerMenu.broadcastChanges();
		}
		TkGun.LOGGER.info("[tkgun] kit: 槽0={} 槽1={} selected={} holdingGun={}",
				p.getInventory().getItem(0).getItem(),
				p.getInventory().getItem(1).getItem(),
				p.getInventory().selected,
				TkGun.holdingGun(p));
		return 1;
	}
}
