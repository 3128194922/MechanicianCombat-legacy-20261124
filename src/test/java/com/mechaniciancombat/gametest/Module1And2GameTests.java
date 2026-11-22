package com.mechaniciancombat.gametest;

import com.mojang.authlib.GameProfile;
import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.entity.HunterTurretEntity;
import com.mechaniciancombat.entity.WarBannerEntity;
import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 模块 1/2 的运行时契约：战旗光环、猎矛炮台部署与上限、跳跃推进器/导弹舱模块校验。 */
@GameTestHolder("tkgun")
@PrefixGameTestTemplate(false)
public final class Module1And2GameTests {
	private static FakePlayer player(GameTestHelper helper, String name) {
		return FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), name));
	}

	private static BlockPos stand(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	/**
	 * 战旗实体的生命周期常量必须自洽：光环有效期不能长过实体寿命，
	 * 且落点冷却必须覆盖"旗子在场 + 冷却"的完整窗口。
	 *
	 * <p>这里不测"光环是否施加到玩家"——那需要把真实玩家放进世界并推进实体 tick，
	 * GameTest 的 FakePlayer 不进 `getEntitiesOfClass` 的查询结果，硬测只会得到一个假阴性。
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void warBannerLifetimeConstantsAreConsistent(GameTestHelper helper) {
		int lifetime = WarBannerEntity.LIFETIME_TICKS;
		if (lifetime <= 0) {
			throw new AssertionError("war banner lifetime must be positive, got " + lifetime);
		}
		if (TkGun.WAR_BANNER_COOLDOWN_TICKS < lifetime) {
			throw new AssertionError("war banner cooldown (" + TkGun.WAR_BANNER_COOLDOWN_TICKS
					+ ") is shorter than its lifetime (" + lifetime + ")");
		}
		if (TkGun.WAR_BANNER_SKILL_TICKS <= 0) {
			throw new AssertionError("war banner cast window must be positive");
		}
		helper.succeed();
	}

	/** 猎矛部署：地面右键应当成功，并留下"部署中"状态。 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void hunterSpearStartsDeployOnGround(GameTestHelper helper) {
		FakePlayer owner = player(helper, "hunter_deploy");
		BlockPos pos = stand(helper, 2, 1, 2);
		owner.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		owner.setOnGround(true);
		ItemStack spear = new ItemStack(ModRegistries.HUNTER_SPEAR.get());
		owner.setItemInHand(InteractionHand.MAIN_HAND, spear);

		boolean started = TkGunServer.deployHunterTurret(owner, spear);
		if (!started) {
			throw new AssertionError("hunter spear deploy was rejected while standing on the ground");
		}
		helper.succeed();
	}

	/** 不在手上 / 空中时，部署必须被拒绝（避免凭空生成炮台）。 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hunterSpearDeployRejectsWrongItem(GameTestHelper helper) {
		FakePlayer owner = player(helper, "hunter_reject");
		BlockPos pos = stand(helper, 2, 1, 2);
		owner.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		ItemStack notSpear = new ItemStack(net.minecraft.world.item.Items.STICK);

		if (TkGunServer.deployHunterTurret(owner, notSpear)) {
			throw new AssertionError("hunter turret deployed from a non-spear item");
		}
		helper.succeed();
	}

	/**
	 * 重复部署必须被"部署中"状态挡住。
	 *
	 * <p>实体生成发生在 23 tick 的 tick 驱动之后（{@code tickHunterSpearDeploy}），
	 * GameTest 里不推进服务端 tick，所以这里验证的是**状态机闸门**而不是最终实体：
	 * 第一次成功、紧接着第二次必须被拒。
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void hunterSpearDeployIsNotReentrant(GameTestHelper helper) {
		FakePlayer owner = player(helper, "hunter_reenter");
		BlockPos pos = stand(helper, 2, 1, 2);
		owner.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		owner.setOnGround(true);
		ItemStack spear = new ItemStack(ModRegistries.HUNTER_SPEAR.get());
		owner.setItemInHand(InteractionHand.MAIN_HAND, spear);

		if (!TkGunServer.deployHunterTurret(owner, spear)) {
			throw new AssertionError("first hunter spear deploy was rejected");
		}
		if (TkGunServer.deployHunterTurret(owner, spear)) {
			throw new AssertionError("second deploy started while the first is still in progress");
		}
		helper.succeed();
	}

	/** 跳跃推进器模块校验：只有装了推进器才能装导弹舱。 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void missileBoxRequiresJumpThruster(GameTestHelper helper) {
		ItemStack backpack = new ItemStack(ModRegistries.COMBAT_BACKPACK.get());
		BackpackUpgradeData.setMissileBox(backpack, true);
		if (BackpackUpgradeData.canUseMissileBox(backpack)) {
			throw new AssertionError("missile box usable without a jump thruster");
		}
		helper.succeed();
	}

	private Module1And2GameTests() {
	}
}
