package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.CombatBackpackAnimation;
import com.mechaniciancombat.item.CombatBackpackItem;
import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.net.CombatBackpackStatusPayload;
import com.mechaniciancombat.client.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class CombatBackpackClientState {
	private static final int FIRE_RESULT_TIMEOUT_TICKS = 40;
	private static final int MAX_CHARGES = 10;
	private static final double PROJECTILE_OFFSET_X = -3.0D / 16.0D;
	private static final double PROJECTILE_OFFSET_Y = 13.0D / 16.0D;
	private static final double PROJECTILE_OFFSET_Z = 8.0D / 16.0D;

	private static final class Entry {
		boolean firingRequested;
		int firingRequestedAt;
		int targetEntityId = -1;
		int phase = CombatBackpackStatusPayload.PHASE_READY;
		int charges;
		int remainingTicks;
		boolean aimLatched;
		int missilePhase = CombatBackpackStatusPayload.MISSILE_NONE;
		int jumpEnergyPixels;
		boolean jumpEnergyLocked;
		BackpackUpgradeData.LauncherType launcherType = BackpackUpgradeData.LauncherType.NONE;
		CombatBackpackAnimation.Phase animationPhase = CombatBackpackAnimation.Phase.READY;
		boolean animationInitialized;
		/** 当前动画阶段的起始客户端 tick（姿态采样按它算秒数）。 */
		int animationStartedAt;
		/** 导弹舱叠层阶段的起始客户端 tick。 */
		int missileStartedAt;
		CombatBackpackAnimation.Phase missileAnimationPhase = CombatBackpackAnimation.Phase.READY;
		/**
		 * 炮台展开时刻（-1 = 未展开）。
		 *
		 * <p>第一人称炮台道具只在"便携炮台锁定到目标 / 自动炮台开始自动攻击"之后显示；
		 * 否则背包里装着炮台模块时会一直举着一门炮。
		 */
		int turretActivationAt = -1;
		/** 便携炮台目标消失 / 自动炮台停机后进入冷却，用来播放 `CD` 片段。 */
		boolean turretCdLatched;
		int turretCdStartedAt = -1;
		int openSoundAt = -1;
		int closeSoundAt = -1;
	}

	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static int ticks;
    private static int nextViewSyncAt;

	public static void trigger(LocalPlayer player) {
		if (player == null) {
			return;
		}
		net.minecraft.world.item.ItemStack backpack = CombatBackpackItem.equippedStack(player);
		if (!BackpackUpgradeData.canUseLauncher(backpack)
				|| BackpackUpgradeData.launcherType(backpack)
				== BackpackUpgradeData.LauncherType.JUMP_THRUSTER) {
			return;
		}
		Entry entry = ENTRIES.computeIfAbsent(player.getId(), ignored -> new Entry());
		if (entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				|| entry.firingRequested) {
			return;
		}
		if (hasActiveTarget(player, entry)) {
			entry.firingRequested = true;
			entry.firingRequestedAt = ticks;
		}
		sendAction(player);
	}

	public static void tick(Minecraft client) {
		ticks++;
		for (Map.Entry<Integer, Entry> pair : ENTRIES.entrySet()) {
			Entry state = pair.getValue();
			if (state.remainingTicks > 0) {
				state.remainingTicks--;
			}
			if (state.phase == CombatBackpackStatusPayload.PHASE_MARKED
					&& state.remainingTicks == 0) {
				state.phase = CombatBackpackStatusPayload.PHASE_READY;
				state.targetEntityId = -1;
			}
			updateAnimationPhase(pair.getKey(), state);
			if (pair.getKey() == localPlayerId()
					&& state.openSoundAt >= 0 && ticks >= state.openSoundAt) {
				state.openSoundAt = -1;
				playBackpackSound("open");
			}
			if (pair.getKey() == localPlayerId()
					&& state.closeSoundAt >= 0 && ticks >= state.closeSoundAt) {
				state.closeSoundAt = -1;
				playBackpackSound("close");
			}
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Entry entry = ENTRIES.computeIfAbsent(player.getId(), ignored -> new Entry());
		if (!BackpackUpgradeData.canUseLauncher(
				CombatBackpackItem.equippedStack(player))) {
			reset(player.getId());
			return;
		}
        if (ticks >= nextViewSyncAt && BackpackUpgradeData.launcherType(CombatBackpackItem.equippedStack(player))
                == BackpackUpgradeData.LauncherType.AUTO) {
            Camera camera=client.gameRenderer.getMainCamera(); Vec3 pos=camera.getPosition();
            ClientNetwork.send(new com.mechaniciancombat.net.CombatBackpackViewPayload(pos.x,pos.y,pos.z,camera.getYRot(),camera.getXRot()));
            nextViewSyncAt=ticks+2;
        }
		if (entry.firingRequested
				&& ticks - entry.firingRequestedAt >= FIRE_RESULT_TIMEOUT_TICKS) {
			// A disconnect or rejected packet must not strand C3 in the firing state.
			entry.firingRequested = false;
		}
	}

	private static void sendAction(LocalPlayer player) {
		Vec3 front = projectileSpawnPosition(player);
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		double verticalFov = Math.toRadians(
				Math.max(1.0D, Math.min(170.0D, GunCamera.renderedFovDegrees())));
		double halfVerticalTan = Math.tan(verticalFov * 0.5D);
		int framebufferWidth = Math.max(1, client.getWindow().getWidth());
		int framebufferHeight = Math.max(1, client.getWindow().getHeight());
		double halfHorizontalTan = halfVerticalTan
				* framebufferWidth / (double) framebufferHeight;
		ClientNetwork.send(new com.mechaniciancombat.net.CombatBackpackFirePayload(
				front.x, front.y, front.z,
				cameraPos.x, cameraPos.y, cameraPos.z,
				camera.getYRot(), camera.getXRot(),
				halfHorizontalTan, halfVerticalTan));
	}

	public static void applyStatus(int ownerEntityId, int targetEntityId, int phase,
								   int charges, int remainingTicks) {
		applyStatus(ownerEntityId, targetEntityId, phase, charges, remainingTicks, 0, 0, 0, false);
	}

	/**
	 * 导弹舱：按下开始锁定，松开齐射。端口坐标暂用玩家包围盒中心
	 * （渲染层量到真实舱口后可替换），服务端只把它当出膛点提示。
	 */
	public static void sendMissileAction(LocalPlayer player, boolean release) {
		if (player == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		double verticalFov = Math.toRadians(
				Math.max(1.0D, Math.min(170.0D, GunCamera.renderedFovDegrees())));
		double halfVerticalTan = Math.tan(verticalFov * 0.5D);
		int framebufferWidth = Math.max(1, client.getWindow().getWidth());
		int framebufferHeight = Math.max(1, client.getWindow().getHeight());
		double halfHorizontalTan = halfVerticalTan
				* framebufferWidth / (double) framebufferHeight;
		Vec3 center = player.getBoundingBox().getCenter();
		double[] ports = new double[18];
		for (int index = 0; index < 6; index++) {
			ports[index * 3] = center.x;
			ports[index * 3 + 1] = center.y;
			ports[index * 3 + 2] = center.z;
		}
		ClientNetwork.send(new com.mechaniciancombat.net.MissileBoxPayload(
				release, cameraPos.x, cameraPos.y, cameraPos.z,
				camera.getYRot(), camera.getXRot(),
				halfHorizontalTan, halfVerticalTan, ports));
	}

	public static void applyStatus(int ownerEntityId, int targetEntityId, int phase,
								   int charges, int remainingTicks, int launcherType,
								   int missilePhase, int jumpEnergyPixels,
								   boolean jumpEnergyLocked) {
		Entry entry = ENTRIES.computeIfAbsent(ownerEntityId, ignored -> new Entry());
		int previousPhase = entry.phase;
		BackpackUpgradeData.LauncherType previousType = entry.launcherType;
		entry.targetEntityId = Math.max(-1, targetEntityId);
		entry.phase = Math.max(CombatBackpackStatusPayload.PHASE_READY,
				Math.min(CombatBackpackStatusPayload.PHASE_BANNER_CD, phase));
		entry.jumpEnergyPixels = jumpEnergyPixels;
		entry.jumpEnergyLocked = jumpEnergyLocked;
		entry.missilePhase = Math.max(CombatBackpackStatusPayload.MISSILE_NONE,
				Math.min(CombatBackpackStatusPayload.MISSILE_CD, missilePhase));
		CombatBackpackAnimation.Phase missileAnim = switch (entry.missilePhase) {
			case CombatBackpackStatusPayload.MISSILE_AIM -> CombatBackpackAnimation.Phase.MISSILE_AIM;
			case CombatBackpackStatusPayload.MISSILE_CD -> CombatBackpackAnimation.Phase.MISSILE_CD;
			default -> CombatBackpackAnimation.Phase.READY;
		};
		if (missileAnim != entry.missileAnimationPhase) {
			entry.missileAnimationPhase = missileAnim;
			entry.missileStartedAt = ticks;
		}
		CombatBackpackAnimation.setMissilePhase(ownerEntityId, missileAnim);
		// —— 炮台展开 / 冷却门控（第一人称炮台道具只在这之后才显示）——
		boolean turretActivatedNow = entry.targetEntityId >= 0
				&& ((entry.launcherType == BackpackUpgradeData.LauncherType.PORTABLE
						&& entry.phase == CombatBackpackStatusPayload.PHASE_MARKED
						&& previousPhase != CombatBackpackStatusPayload.PHASE_MARKED)
				|| (entry.launcherType == BackpackUpgradeData.LauncherType.AUTO
						&& entry.phase == CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE
						&& previousPhase != CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE));
		if (entry.launcherType != previousType) {
			entry.turretActivationAt = -1;
			entry.turretCdLatched = false;
			entry.turretCdStartedAt = -1;
		}
		if (turretActivatedNow) {
			entry.turretActivationAt = ticks;
			entry.turretCdLatched = false;
			entry.turretCdStartedAt = -1;
		} else if (entry.launcherType == BackpackUpgradeData.LauncherType.PORTABLE
				&& previousPhase == CombatBackpackStatusPayload.PHASE_MARKED
				&& entry.phase != CombatBackpackStatusPayload.PHASE_MARKED) {
			entry.turretCdLatched = true;
			entry.turretCdStartedAt = ticks;
		} else if (entry.launcherType == BackpackUpgradeData.LauncherType.AUTO
				&& previousPhase == CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE
				&& (entry.phase == CombatBackpackStatusPayload.PHASE_READY
						|| entry.phase == CombatBackpackStatusPayload.PHASE_AUTO_PAUSED
						|| entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN)) {
			entry.turretCdLatched = true;
			entry.turretCdStartedAt = ticks;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level != null
				&& client.level.getEntity(ownerEntityId)
				instanceof net.minecraft.world.entity.LivingEntity owner) {
			entry.launcherType = BackpackUpgradeData.launcherType(
					CombatBackpackItem.equippedStack(owner));
		}
		entry.charges = Math.max(0, Math.min(MAX_CHARGES, charges));
		entry.remainingTicks = Math.max(0, remainingTicks);
		if (entry.phase == CombatBackpackStatusPayload.PHASE_MARKED) {
			entry.aimLatched = true;
		} else if (entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
				|| entry.phase == CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT) {
			entry.aimLatched = false;
		}
		updateAnimationPhase(ownerEntityId, entry);
	}

	private static boolean hasActiveTarget(LocalPlayer player, Entry entry) {
		if (entry.phase != CombatBackpackStatusPayload.PHASE_MARKED
				|| entry.targetEntityId < 0 || entry.remainingTicks <= 0) {
			return false;
		}
		net.minecraft.world.entity.Entity target = player.level().getEntity(entry.targetEntityId);
		if (target == null || !target.isAlive()) {
			clearTarget(entry);
			return false;
		}
		return entry.charges > 0;
	}

	private static void clearTarget(Entry entry) {
		entry.targetEntityId = -1;
	}

	public static void completeFire(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return;
		}
		entry.firingRequested = false;
	}

	public static void reset(int playerId) {
		ENTRIES.remove(playerId);
		CombatBackpackAnimation.clear(playerId);
	}

	private static void updateAnimationPhase(int playerId, Entry entry) {
		CombatBackpackAnimation.Phase next = animationPhase(entry);
		if (entry.animationInitialized && next == entry.animationPhase) {
			return;
		}
		entry.animationInitialized = true;
		entry.animationPhase = next;
		entry.animationStartedAt = ticks;
		CombatBackpackAnimation.setPhase(playerId, next);
		entry.openSoundAt = -1;
		entry.closeSoundAt = -1;
		if (playerId != localPlayerId()) {
			return;
		}
		if (next == CombatBackpackAnimation.Phase.READY) {
			playBackpackSound("open");
			entry.closeSoundAt = ticks + 6;
		} else if (next == CombatBackpackAnimation.Phase.CD) {
			entry.openSoundAt = ticks + 1;
			entry.closeSoundAt = ticks + 7;
		}
	}

	private static int localPlayerId() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? -1 : client.player.getId();
	}

	private static void playBackpackSound(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.getSoundManager() == null) {
			return;
		}
		SoundEvent sound = SoundEvent.createVariableRangeEvent(TkGun.id("backpack_" + name));
		client.getSoundManager().play(SimpleSoundInstance.forUI(
				sound, 1.0F, TkGun.BACKPACK_SOUND_VOLUME));
	}

	public static CombatBackpackAnimation.Phase phase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackAnimation.Phase.READY : animationPhase(entry);
	}

	public static int statusPhase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackStatusPayload.PHASE_READY : entry.phase;
	}

	/** 客户端 tick 计数（炮台/导弹的动画时钟与它对齐）。 */
	public static int clientTicks() {
		return ticks;
	}

	/** 读取某玩家的背包状态条目（炮台第一人称等渲染器需要）。 */
	public static Entry get(int playerId) {
		return ENTRIES.get(playerId);
	}

	/** 该玩家背包当前安装的发射器类型。 */
	public static BackpackUpgradeData.LauncherType launcherType(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? BackpackUpgradeData.LauncherType.NONE : entry.launcherType;
	}

	/** 当前动画阶段已持续多少 tick（含帧插值），供手绘姿态采样用。 */
	public static double animationAgeTicks(int playerId, float partialTick) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return 0.0D;
		}
		return Math.max(0.0D, ticks - entry.animationStartedAt + partialTick);
	}

	/** 导弹舱叠层阶段已持续多少 tick（含帧插值）。 */
	public static double missileAnimationAgeTicks(int playerId, float partialTick) {
		Entry entry = ENTRIES.get(playerId);
		if (entry == null) {
			return 0.0D;
		}
		return Math.max(0.0D, ticks - entry.missileStartedAt + partialTick);
	}

	/** 导弹舱当前阶段（背包叠层动画用）。 */
	public static CombatBackpackAnimation.Phase missileAnimationPhase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackAnimation.Phase.READY : entry.missileAnimationPhase;
	}

	/** 炮台是否已展开（第一人称炮台道具的显示门控）。 */
	public static boolean turretActivated(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.turretActivationAt >= 0;
	}

	public static int turretActivationAt(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? -1 : entry.turretActivationAt;
	}

	public static int turretCdStartedAt(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? -1 : entry.turretCdStartedAt;
	}

	public static boolean turretCdLatched(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.turretCdLatched;
	}

	public static int targetEntityId(int ownerEntityId) {
		Entry entry = ENTRIES.get(ownerEntityId);
		if (entry == null
				|| (entry.phase != CombatBackpackStatusPayload.PHASE_MARKED
						&& entry.phase != CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE)
				|| entry.targetEntityId < 0 || entry.remainingTicks <= 0) {
			return -1;
		}
		Minecraft client = Minecraft.getInstance();
		net.minecraft.world.entity.Entity target = client.level == null ? null
				: client.level.getEntity(entry.targetEntityId);
		if (target == null || !target.isAlive()) {
			clearTarget(entry);
			return -1;
		}
		return entry.targetEntityId;
	}

	public static int charges(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0 : entry.charges;
	}

	public static int remainingTicks(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0 : entry.remainingTicks;
	}

	/** 导弹舱阶段：`MISSILE_NONE` / `MISSILE_AIM` / `MISSILE_CD`。 */
	public static int missilePhase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? CombatBackpackStatusPayload.MISSILE_NONE : entry.missilePhase;
	}

	/** 跳跃推进器能量（像素高度，0..最大值）。 */
	public static int jumpEnergyPixels(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? 0 : entry.jumpEnergyPixels;
	}

	/** 跳跃推进器是否在锁定（回充前的红色脉冲提示）。 */
	public static boolean jumpEnergyLocked(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry != null && entry.jumpEnergyLocked;
	}

	private static CombatBackpackAnimation.Phase animationPhase(Entry entry) {
		if (entry.launcherType == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA) {
			return entry.phase == CombatBackpackStatusPayload.PHASE_COOLDOWN
					|| entry.phase == CombatBackpackStatusPayload.PHASE_SUMMON_DORMANT
					? CombatBackpackAnimation.Phase.CD
					: CombatBackpackAnimation.Phase.READY;
		}
		if (entry.phase == CombatBackpackStatusPayload.PHASE_READY && entry.aimLatched) {
			return CombatBackpackAnimation.Phase.AIM;
		}
		if (entry.launcherType == BackpackUpgradeData.LauncherType.BATTLE_BANNER) {
			return switch (entry.phase) {
				case CombatBackpackStatusPayload.PHASE_BANNER_READY ->
						CombatBackpackAnimation.Phase.BANNER_READY;
				case CombatBackpackStatusPayload.PHASE_BANNER_ACTIVE ->
						CombatBackpackAnimation.Phase.BANNER_AIM;
				case CombatBackpackStatusPayload.PHASE_BANNER_CD,
						CombatBackpackStatusPayload.PHASE_COOLDOWN ->
						CombatBackpackAnimation.Phase.BANNER_CD;
				default -> CombatBackpackAnimation.Phase.BANNER_READY;
			};
		}
		return switch (entry.phase) {
			case CombatBackpackStatusPayload.PHASE_COOLDOWN ->
					CombatBackpackAnimation.Phase.CD;
			case CombatBackpackStatusPayload.PHASE_MARKED ->
					CombatBackpackAnimation.Phase.AIM;
			case CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE ->
					CombatBackpackAnimation.Phase.AIM;
			case CombatBackpackStatusPayload.PHASE_AUTO_PAUSED ->
					CombatBackpackAnimation.Phase.AIM;
			default -> CombatBackpackAnimation.Phase.READY;
		};
	}

	public static Vec3 projectileSpawnPosition(LocalPlayer player) {
		Vec3 front = CombatBackpackGeoRenderer.canonicalLaunchPosition(player.getId());
		if (front == null) {
			front = player.position().add(0.0D, 1.0D, 0.0D);
		}
		double yaw = Math.toRadians(player.yBodyRot);
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
		Vec3 offset = right.scale(PROJECTILE_OFFSET_X)
				.add(0.0D, PROJECTILE_OFFSET_Y, 0.0D)
				.add(forward.scale(PROJECTILE_OFFSET_Z));
		return front.add(offset);
	}

	private CombatBackpackClientState() {
	}
}
