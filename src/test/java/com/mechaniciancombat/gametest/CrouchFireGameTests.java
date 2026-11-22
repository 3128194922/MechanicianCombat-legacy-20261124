package com.mechaniciancombat.gametest;

import com.mojang.authlib.GameProfile;
import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.registry.ModRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 蹲射（专注）开火的**服务端运行时**契约。
 *
 * <p>为什么需要这一层：`CrouchFireWindowContract` 只证明"状态时长 ≥ 动画时长"（静态）。
 * 但客户端之所以会掉回 `idle`（站姿），前提是**服务端那一帧真的发出了 `focus=false`
 * 或状态 `IDLE` 的包**。这里直接驱动真实状态机，逐 tick 断言这件事没有发生。
 *
 * <p>`focus` 位在 `broadcast()` 里的算法是 `st.deployed && TkGun.holdingGun(actor)` ——
 * 所以本测试同时覆盖了两个必要条件：`deployed` 不掉、枪还在手上。
 */
@GameTestHolder("tkgun")
@PrefixGameTestTemplate(false)
public final class CrouchFireGameTests {

	private static FakePlayer gunner(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), name));
		ItemStack gun = new ItemStack(ModRegistries.BOWGUN.get());
		// 装满弹仓，避免测试被"空弹匣"提前打断。
		NonNullList<ItemStack> ring = NonNullList.withSize(
				com.mechaniciancombat.item.GunAmmoData.SIZE, ItemStack.EMPTY);
		ring.set(0, new ItemStack(TkGunServer.BOLT, 64));
		com.mechaniciancombat.item.GunAmmoData.save(gun, ring, 0,
				helper.getLevel().registryAccess());
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		return player;
	}

	/**
	 * 专注开火**全程**必须保持 `deployed` 与 FIRE 状态。
	 *
	 * <p>这是"蹲射瞬间站立"的服务端侧根因防护：只要任一 tick 里 `deployed` 掉了，
	 * 客户端收到的 `focus` 就是 false，`BowgunRig.pick` 会走腰射分支 → 播 `idle`（站姿）。
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void focusedFireKeepsDeployedForWholeWindow(GameTestHelper helper) {
		FakePlayer player = gunner(helper, "crouch_fire_hold");
		TkGunServer.PState st = TkGunServer.state(player);
		st.deployed = true;
		st.focusShots = 0;
		st.heat = 0.0F;
		st.reloadUntil = 0;

		TkGunServer.tryFire(player, false);

		helper.assertTrue(st.deployed, "focused fire must keep deployed=true right after trigger");
		helper.assertTrue(TkGun.holdingGun(player), "the gun must still be in hand while firing");
		helper.assertTrue(st.shotQueue.size() > 0 || st.shotAnimating,
				"triggering in focus must enqueue a shot");

		// 逐 tick 检查：状态机内部推进时 deployed 不得被清掉。
		for (int tick = 0; tick < 6; tick++) {
			TkGunServer.advancePlayerTickForTest(player);
			helper.assertTrue(st.deployed,
					"deployed dropped at tick " + tick + " — client would receive focus=false "
							+ "and snap the crouch pose back to standing");
		}
		helper.succeed();
	}

	/**
	 * 专注开火的状态**必须是 FIRE**，且时长覆盖整段 `Focus_shooting*`。
	 *
	 * <p>与静态契约互补：那条读常量，这条读**运行时真正下发到包里的 tick 数**。
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void focusedFireStateCoversAuthoredClip(GameTestHelper helper) {
		FakePlayer player = gunner(helper, "crouch_fire_state");
		TkGunServer.PState st = TkGunServer.state(player);
		st.deployed = true;
		st.heat = 0.0F;
		st.reloadUntil = 0;

		TkGunServer.tryFire(player, false);
		// 推进到出伤帧（ATTACK_FRAME_TICKS = 1），让状态机真正进入 FIRE。
		TkGunServer.advancePlayerTickForTest(player);

		helper.assertTrue(st.state == TkGun.State.FIRE,
				"focused shot must be in FIRE, got " + st.state);
		int remaining = (int) Math.max(0, st.stateUntil - player.level().getGameTime());
		helper.assertTrue(remaining >= TkGun.FIRE_TICKS - 4,
				"FIRE window must cover the authored clip: remaining=" + remaining
						+ " but FIRE_TICKS=" + TkGun.FIRE_TICKS
						+ " (a short window makes the client fall back to idle)");
		helper.succeed();
	}

	/**
	 * 腰射**不能**被误判成专注。
	 *
	 * <p>反向对照：如果 `deployed` 的判定写错（例如对腰射也置 true），
	 * 腰射就会播 `Focus_shooting*`（蹲姿），而玩家明明是站着的。
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hipFireStaysUndeployed(GameTestHelper helper) {
		FakePlayer player = gunner(helper, "hip_fire_control");
		TkGunServer.PState st = TkGunServer.state(player);
		st.deployed = false;
		st.mag = 0;
		st.heat = 0.0F;
		st.reloadUntil = 0;
		// 腰射要先"举枪"（服务端用 isUsingItem 权威判定）。
		player.startUsingItem(InteractionHand.MAIN_HAND);

		TkGunServer.tryFire(player, false);

		helper.assertTrue(!st.deployed, "hip fire must not deploy the gun");
		helper.assertTrue(st.mag > 0 || st.shotQueue.size() > 0 || st.shotAnimating,
				"hip fire must still enqueue a shot");
		helper.succeed();
	}

	/** `focus` 位（发给客户端的那一位）= `deployed && holdingGun`，两者缺一不可。 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void focusFlagRequiresBothDeployedAndHolding(GameTestHelper helper) {
		FakePlayer player = gunner(helper, "focus_flag");
		TkGunServer.PState st = TkGunServer.state(player);

		st.deployed = true;
		helper.assertTrue(st.deployed && TkGun.holdingGun(player),
				"deployed + holding must produce focus=true");

		// 换成空手：即使 deployed 还残留，客户端也必须收到 focus=false
		// （否则人已经收枪，模型还在播蹲射）。
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(!(st.deployed && TkGun.holdingGun(player)),
				"an empty hand must force focus=false even if deployed is stale");
		helper.succeed();
	}

	private CrouchFireGameTests() {
	}
}
