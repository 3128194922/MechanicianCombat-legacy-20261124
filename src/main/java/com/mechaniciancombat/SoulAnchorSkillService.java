package com.mechaniciancombat;

import com.mechaniciancombat.entity.SoulAnchorEntity;
import com.mechaniciancombat.item.MechanicianArmorItem;
import com.mechaniciancombat.net.ModNetwork;
import com.mechaniciancombat.net.SoulAnchorSkillPayload;
import com.mechaniciancombat.net.SoulAnchorStatusPayload;
import com.mechaniciancombat.registry.ModRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 胸甲 T1 灵魂锚技能，独立于枪械状态机。
 *
 * <p>完整流程：按住 → READY（锁定目标）→ 松开投掷 → 锚飞行 → 命中后按情况
 * 牵引玩家 / 牵引实体 / 持枪滑步 → 收招。所有位移均由服务端结算并广播阶段。
 *
 * <p>本地适配：暂未移植上游的护甲模块系统（C3/T1 部件），改为
 * 「同时装备 Mechanician 胸甲与护腿」即视为解锁；模块 4 接入改装台后可换回部件判定。
 */
public final class SoulAnchorSkillService {
	/** 第一人称投掷动作在 0.25s 到达最终关键帧。 */
	private static final int THROW_TICKS = 5;
	private static final int GUN_THROW_TICKS = 5;
	private static final int PLAYER_DRAG_TIMEOUT_TICKS = 40;
	private static final int ENTITY_DRAG_TICKS = 10;
	private static final int ANCHOR_FLIGHT_TIMEOUT_TICKS = 100;
	private static final int ANCHOR_TRANSITION_TICKS = 6;
	private static final int CHAIN_SLIDING_TICKS = 14;
	private static final int CHAIN_SLIDING_TO_IDLE_TICKS = 5;
	private static final int CHAIN_SLIDING_TO_RUN_TICKS = 5;
	private static final int ACCELERATION_PAUSE_HOLD_TICKS = 200;
	private static final double CHAIN_SLIDING_DISTANCE = 12.0D;
	private static final double CHAIN_SLIDING_ARRIVAL_DISTANCE = 0.5D;
	private static final int CLIENT_HOLD_TIMEOUT_TICKS = 1200;
	private static final double TARGET_RANGE = 32.0D;
	private static final double FIREWORK_FORWARD_SPEED = 1.5D;
	private static final double FIREWORK_FORWARD_BIAS = 0.1D;
	private static final double FIREWORK_VELOCITY_BLEND = 0.5D;

	private enum Stage {
		READY,
		THROW,
		ANCHOR_FLYING,
		PLAYER_DRAG,
		ENTITY_DRAG,
		GUN_THROWING,
		GUN_FLYING,
		GUN_ENTITY_DRAG,
		GUN_SLIDING,
		GUN_SLIDING_TO_IDLE,
		GUN_SLIDING_TO_RUN,
		GUN_RECALL
	}

	private static final class Skill {
		Stage stage = Stage.THROW;
		boolean gun;
		boolean sword;
		long stageStart;
		long stageEnd;
		int anchorId = -1;
		long anchorLandedAt;
		boolean throwSoundPlayed;
		int targetId = -1;
		boolean targetHighlighted;
		double slideDirX;
		double slideDirZ;
		boolean returnToRunAfterSlide;
		boolean slideDashPaused;
		long slidePausedDashTicks;
		long slidePausedSpeedBoostTicks;
		double slideResumeSpeed;
	}

	private static final Map<UUID, Skill> SKILLS = new HashMap<>();

	private SoulAnchorSkillService() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
					if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
						return;
					}
					if (event.getServer() == null) {
						return;
					}
					for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
						tick(player);
					}
				});
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
					if (event.getEntity() instanceof ServerPlayer player) {
						finish(player);
					}
				});
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.server.ServerStoppedEvent event) -> SKILLS.clear());
	}

	public static boolean isAvailable(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator() || !player.onGround()) {
			return false;
		}
		return hasInstalledModules(player);
	}

	private static boolean hasInstalledModules(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
		return com.mechaniciancombat.item.ArmorUpgradeData.hasPart(chest,
				com.mechaniciancombat.item.ArmorPartItem.Kind.C3)
				&& com.mechaniciancombat.item.ArmorUpgradeData.hasPart(legs,
				com.mechaniciancombat.item.ArmorPartItem.Kind.T1);
	}

	public static boolean press(ServerPlayer player, boolean sprintingAtPress) {
		if (!isAvailable(player) || player.isUsingItem()) {
			return false;
		}
		TkGunServer.PState current = TkGunServer.state(player);
		if (player.level().getGameTime() < current.warBannerCastUntil) {
			return false;
		}
		boolean gun = TkGun.holdingGun(player);
		if (gun) {
			if (current.state != TkGun.State.IDLE || current.deployed) {
				return false;
			}
		}
		return startReady(player, gun, TkGun.isKineticSword(player), sprintingAtPress);
	}

	public static void release(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Skill skill = SKILLS.get(player.getUUID());
		if (skill == null || skill.stage != Stage.READY) {
			return;
		}
		pauseAccelerationCountdown(player, skill);
		selectTarget(player, skill);
		clearReadyTarget(player, skill);
		beginStage(player, skill,
				usesGunMotion(skill) ? Stage.GUN_THROWING : Stage.THROW,
				usesGunMotion(skill) ? GUN_THROW_TICKS : THROW_TICKS);
	}

	public static void cancel(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Skill skill = SKILLS.get(player.getUUID());
		if (skill != null && canSneakRecall(skill.stage)) {
			cancelDrag(player, skill);
		}
	}

	public static boolean isActive(ServerPlayer player) {
		return player != null && SKILLS.containsKey(player.getUUID());
	}

	/** 回收阶段允许近战开始（可见姿势由近战接管）。 */
	public static boolean allowsMeleeStart(ServerPlayer player) {
		Skill skill = player == null ? null : SKILLS.get(player.getUUID());
		return skill == null || skill.stage == Stage.GUN_RECALL;
	}

	private static boolean startReady(ServerPlayer player, boolean gun, boolean sword,
									  boolean sprintingAtPress) {
		if (SKILLS.containsKey(player.getUUID())) {
			return false;
		}
		Skill skill = new Skill();
		skill.gun = gun;
		skill.sword = sword;
		skill.stage = Stage.READY;
		skill.stageStart = player.level().getGameTime();
		skill.stageEnd = skill.stageStart;
		TkGunServer.PState gunState = TkGunServer.state(player);
		skill.returnToRunAfterSlide = usesGunMotion(skill)
				&& (sprintingAtPress || player.isSprinting()
				|| skill.stageStart < gunState.dashUntil
				|| skill.stageStart < gunState.speedBoostUntil);
		if (skill.returnToRunAfterSlide) {
			skill.slideResumeSpeed = Math.max(0.28D, Math.min(0.42D,
					player.getDeltaMovement().horizontalDistance()));
		}
		SKILLS.put(player.getUUID(), skill);
		suppressReadySprint(player);
		syncPhase(player, skill, SoulAnchorSkillPayload.READY, CLIENT_HOLD_TIMEOUT_TICKS);
		selectTarget(player, skill);
		return true;
	}

	private static void tick(ServerPlayer player) {
		Skill skill = SKILLS.get(player.getUUID());
		if (skill == null) {
			return;
		}
		if (!player.isAlive() || !hasInstalledModules(player)) {
			finish(player);
			return;
		}
		long now = player.level().getGameTime();
		if (skill.stage == Stage.READY) {
			suppressReadySprint(player);
			selectTarget(player, skill);
			return;
		}
		if (!usesGunMotion(skill)) {
			player.setSprinting(false);
		}
		if (!skill.throwSoundPlayed && now >= skill.stageStart + 3L
				&& (skill.stage == Stage.THROW || skill.stage == Stage.GUN_THROWING)) {
			skill.throwSoundPlayed = true;
			TkGunServer.playSound(player, TkGun.SND_CHAIN, 1.0F, 1.0F);
		}
		if (skill.gun && locksGunMovement(skill.stage)) {
			player.zza = 0.0F;
			player.xxa = 0.0F;
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(0.0D, velocity.y, 0.0D);
			player.hasImpulse = true;
		}
		switch (skill.stage) {
			case READY -> {
			}
			case THROW -> {
				if (now >= skill.stageEnd && !spawnAnchor(player, skill)) {
					finish(player);
				}
			}
			case GUN_THROWING -> {
				if (now >= skill.stageEnd && !spawnAnchor(player, skill)) {
					finish(player);
				}
			}
			case ANCHOR_FLYING, GUN_FLYING -> observeAnchor(player, skill, now);
			case PLAYER_DRAG -> pullPlayerToAnchor(player, skill, now);
			case GUN_SLIDING -> slidePlayerToAnchor(player, skill, now);
			case ENTITY_DRAG, GUN_ENTITY_DRAG, GUN_RECALL -> observeRecall(player, skill, now);
			case GUN_SLIDING_TO_IDLE -> tickSlidingToIdle(player, skill, now);
			case GUN_SLIDING_TO_RUN -> tickSlidingToRun(player, skill, now);
		}
	}

	private static boolean locksGunMovement(Stage stage) {
		return stage == Stage.GUN_SLIDING_TO_IDLE;
	}

	private static void suppressReadySprint(ServerPlayer player) {
		TkGunServer.PState state = TkGunServer.state(player);
		if (TkGun.holdingGun(player) && player.level().getGameTime() < state.dashUntil) {
			return;
		}
		player.setSprinting(false);
	}

	private static boolean spawnAnchor(ServerPlayer player, Skill skill) {
		Vec3 direction = player.getLookAngle().normalize();
		Entity marked = skill.targetId < 0 ? null
				: player.serverLevel().getEntity(skill.targetId);
		if (marked instanceof LivingEntity living && living.isAlive()) {
			Vec3 toTarget = living.getBoundingBox().getCenter().subtract(player.getEyePosition());
			if (toTarget.lengthSqr() > 1.0E-6D) {
				direction = toTarget.normalize();
			}
		} else {
			skill.targetId = -1;
		}
		double yaw = Math.toRadians(player.getYRot());
		Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
		Vec3 spawn = player.position()
				.add(0.0D, player.getEyeHeight() * 0.72D, 0.0D)
				.add(left.scale(0.32D))
				.add(direction.scale(0.45D));
		SoulAnchorEntity anchor = new SoulAnchorEntity(ModRegistries.SOUL_ANCHOR_ENTITY.get(),
				player.level(), player);
		anchor.setPos(spawn.x, spawn.y, spawn.z);
		anchor.setDeltaMovement(direction.scale(2.75D));
		player.serverLevel().addFreshEntity(anchor);
		skill.anchorId = anchor.getId();
		skill.anchorLandedAt = 0L;
		skill.stage = usesGunMotion(skill) ? Stage.GUN_FLYING : Stage.ANCHOR_FLYING;
		skill.stageStart = player.level().getGameTime();
		skill.stageEnd = skill.stageStart + ANCHOR_FLIGHT_TIMEOUT_TICKS;
		syncPhase(player, skill, SoulAnchorSkillPayload.ANCHOR_FLIGHT, CLIENT_HOLD_TIMEOUT_TICKS);
		return true;
	}

	private static void observeAnchor(ServerPlayer player, Skill skill, long now) {
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor) || anchor.isRemoved()) {
			finish(player);
			return;
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.FLYING && skill.targetId >= 0) {
			Entity marked = player.serverLevel().getEntity(skill.targetId);
			if (marked instanceof LivingEntity living && living.isAlive()) {
				Vec3 steer = living.getBoundingBox().getCenter().subtract(anchor.position());
				if (steer.lengthSqr() > 1.0E-6D) {
					anchor.setDeltaMovement(steer.normalize().scale(2.75D));
					anchor.hasImpulse = true;
				}
			} else {
				skill.targetId = -1;
			}
		}
		if (anchor.anchorState() != SoulAnchorEntity.State.ANCHORED) {
			if (now >= skill.stageEnd) {
				anchor.retrieve(player);
				resumeAccelerationCountdown(player, skill, now);
				beginStage(player, skill, Stage.GUN_RECALL, recallTicks(skill));
			}
			return;
		}
		if (skill.anchorLandedAt == 0L) {
			skill.anchorLandedAt = now;
			if (usesGunMotion(skill) && anchor.hookedEntityId() >= 0) {
				resumeAccelerationCountdown(player, skill, now);
			}
			syncPhase(player, skill, SoulAnchorSkillPayload.ANCHOR_STALL, ANCHOR_TRANSITION_TICKS);
		}
		if (now < skill.anchorLandedAt + ANCHOR_TRANSITION_TICKS) {
			return;
		}
		Entity hooked = anchor.hookedEntity();
		int hookedId = anchor.hookedEntityId();
		if (hookedId >= 0 && (hooked == null || !hooked.isAlive())) {
			anchor.retrieve(player);
			resumeAccelerationCountdown(player, skill, now);
			beginStage(player, skill, Stage.GUN_RECALL, recallTicks(skill));
		} else if (hooked != null && hooked.isAlive()) {
			if (hooked instanceof LivingEntity living) {
				SoulAnchorMarks.mark(living, player);
			}
			anchor.retrieve(player);
			beginStage(player, skill, usesGunMotion(skill) ? Stage.GUN_ENTITY_DRAG : Stage.ENTITY_DRAG,
					ENTITY_DRAG_TICKS);
		} else if (usesGunMotion(skill)) {
			beginGunSliding(player, skill, anchor);
		} else {
			beginStage(player, skill, Stage.PLAYER_DRAG, PLAYER_DRAG_TIMEOUT_TICKS);
		}
	}

	private static void pullPlayerToAnchor(ServerPlayer player, Skill skill, long now) {
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor) || anchor.isRemoved()) {
			finish(player);
			return;
		}
		Vec3 target = anchor.position().add(0.0D, 0.8D, 0.0D);
		Vec3 delta = target.subtract(player.position());
		if (delta.lengthSqr() < 2.25D || now >= skill.stageEnd) {
			anchor.discard();
			finish(player);
			return;
		}
		Vec3 direction = delta.normalize();
		Vec3 current = player.getDeltaMovement();
		Vec3 accelerated = current.add(
				direction.x * FIREWORK_FORWARD_BIAS
						+ (direction.x * FIREWORK_FORWARD_SPEED - current.x) * FIREWORK_VELOCITY_BLEND,
				direction.y * FIREWORK_FORWARD_BIAS
						+ (direction.y * FIREWORK_FORWARD_SPEED - current.y) * FIREWORK_VELOCITY_BLEND,
				direction.z * FIREWORK_FORWARD_BIAS
						+ (direction.z * FIREWORK_FORWARD_SPEED - current.z) * FIREWORK_VELOCITY_BLEND);
		player.zza = 0.0F;
		player.xxa = 0.0F;
		player.setSprinting(false);
		player.setDeltaMovement(accelerated);
		player.hasImpulse = true;
	}

	private static void beginGunSliding(ServerPlayer player, Skill skill, SoulAnchorEntity anchor) {
		Vec3 delta = anchor.position().subtract(player.position());
		Vec3 direction = new Vec3(delta.x, 0.0D, delta.z);
		if (direction.lengthSqr() < 1.0E-6D) {
			Vec3 look = player.getLookAngle();
			direction = new Vec3(look.x, 0.0D, look.z);
		}
		if (direction.lengthSqr() < 1.0E-6D) {
			direction = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		direction = direction.normalize();
		skill.slideDirX = direction.x;
		skill.slideDirZ = direction.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.setSprinting(false);
		player.zza = 0.0F;
		player.xxa = 0.0F;
		player.yya = 0.0F;
		pauseAccelerationCountdown(player, skill);
		beginStage(player, skill, Stage.GUN_SLIDING, CHAIN_SLIDING_TICKS + 1);
	}

	private static void slidePlayerToAnchor(ServerPlayer player, Skill skill, long now) {
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor) || anchor.isRemoved()) {
			finish(player);
			return;
		}
		Vec3 toAnchor = anchor.position().subtract(player.position());
		double remaining = Math.hypot(toAnchor.x, toAnchor.z);
		if (remaining <= CHAIN_SLIDING_ARRIVAL_DISTANCE) {
			completeGunSliding(player, skill, anchor, now);
			return;
		}
		if (now >= skill.stageEnd) {
			completeGunSliding(player, skill, anchor, now);
			return;
		}
		int step = (int) Math.max(1L, Math.min(CHAIN_SLIDING_TICKS, now - skill.stageStart));
		double before = slideProgress((step - 1) / (double) CHAIN_SLIDING_TICKS);
		double after = slideProgress(step / (double) CHAIN_SLIDING_TICKS);
		double speed = Math.min(remaining, CHAIN_SLIDING_DISTANCE * (after - before));
		Vec3 velocity = player.getDeltaMovement();
		player.setSprinting(false);
		player.zza = 0.0F;
		player.xxa = 0.0F;
		player.yya = 0.0F;
		player.setDeltaMovement(skill.slideDirX * speed, velocity.y, skill.slideDirZ * speed);
		player.hasImpulse = true;
	}

	private static void completeGunSliding(ServerPlayer player, Skill skill,
										   SoulAnchorEntity anchor, long now) {
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(0.0D, velocity.y, 0.0D);
		player.hasImpulse = true;
		player.zza = 0.0F;
		player.xxa = 0.0F;
		anchor.retrieve(player);
		resumeAccelerationCountdown(player, skill, now);
		if (skill.returnToRunAfterSlide) {
			if (TkGun.holdingGun(player)) {
				player.setSprinting(true);
			}
			beginStage(player, skill, Stage.GUN_SLIDING_TO_RUN, CHAIN_SLIDING_TO_RUN_TICKS);
		} else {
			beginStage(player, skill, Stage.GUN_SLIDING_TO_IDLE, CHAIN_SLIDING_TO_IDLE_TICKS);
		}
	}

	private static double slideProgress(double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		return clamped * (2.0D - clamped);
	}

	private static void tickSlidingToIdle(ServerPlayer player, Skill skill, long now) {
		if (now < skill.stageEnd) {
			return;
		}
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor && !anchor.isRemoved()) {
			anchor.discard();
		}
		finish(player);
	}

	private static void tickSlidingToRun(ServerPlayer player, Skill skill, long now) {
		applySlidingToRunMovement(player, skill, now);
		if (now < skill.stageEnd) {
			return;
		}
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor && !anchor.isRemoved()) {
			anchor.discard();
		}
		finish(player);
	}

	private static void applySlidingToRunMovement(ServerPlayer player, Skill skill, long now) {
		if (TkGun.holdingGun(player)) {
			player.setSprinting(true);
		}
		Vec3 direction = new Vec3(player.xxa, 0.0D, player.zza)
				.yRot(-(float) Math.toRadians(player.getYRot()));
		if (direction.lengthSqr() < 1.0E-4D) {
			direction = new Vec3(skill.slideDirX, 0.0D, skill.slideDirZ);
		}
		if (direction.lengthSqr() < 1.0E-6D) {
			return;
		}
		direction = direction.normalize();
		long elapsed = Math.max(1L, Math.min(CHAIN_SLIDING_TO_RUN_TICKS,
				now - skill.stageStart + 1L));
		double progress = elapsed / (double) CHAIN_SLIDING_TO_RUN_TICKS;
		double eased = progress * progress * (3.0D - 2.0D * progress);
		double speed = Math.max(0.28D, skill.slideResumeSpeed) * (0.2D + 0.8D * eased);
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(direction.x * speed, velocity.y, direction.z * speed);
		player.hasImpulse = true;
	}

	private static void pauseAccelerationCountdown(ServerPlayer player, Skill skill) {
		if (!skill.returnToRunAfterSlide || skill.slideDashPaused) {
			return;
		}
		long now = player.level().getGameTime();
		TkGunServer.PState state = TkGunServer.state(player);
		skill.slidePausedDashTicks = Math.max(0L, state.dashUntil - now);
		skill.slidePausedSpeedBoostTicks = Math.max(0L, state.speedBoostUntil - now);
		if (skill.slidePausedDashTicks > 0L) {
			state.dashUntil = now + ACCELERATION_PAUSE_HOLD_TICKS;
		}
		if (skill.slidePausedSpeedBoostTicks > 0L) {
			state.speedBoostUntil = now + ACCELERATION_PAUSE_HOLD_TICKS;
		}
		skill.slideDashPaused = true;
		TkGunServer.rebroadcast(player);
	}

	private static void resumeAccelerationCountdown(ServerPlayer player, Skill skill, long now) {
		if (!skill.slideDashPaused) {
			return;
		}
		skill.slideDashPaused = false;
		TkGunServer.PState state = TkGunServer.state(player);
		state.dashUntil = now + skill.slidePausedDashTicks;
		state.speedBoostUntil = now + skill.slidePausedSpeedBoostTicks;
		TkGunServer.rebroadcast(player);
	}

	private static void observeRecall(ServerPlayer player, Skill skill, long now) {
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (!(entity instanceof SoulAnchorEntity anchor) || anchor.isRemoved()
				|| now >= skill.stageEnd) {
			if (entity instanceof SoulAnchorEntity anchor2) {
				anchor2.discard();
			}
			finish(player);
		}
	}

	private static boolean isDragging(Stage stage) {
		return stage == Stage.PLAYER_DRAG || stage == Stage.ENTITY_DRAG
				|| stage == Stage.GUN_ENTITY_DRAG;
	}

	private static boolean canSneakRecall(Stage stage) {
		return stage == Stage.ANCHOR_FLYING || stage == Stage.GUN_FLYING || isDragging(stage);
	}

	private static void cancelDrag(ServerPlayer player, Skill skill) {
		clearReadyTarget(player, skill);
		Entity entity = player.serverLevel().getEntity(skill.anchorId);
		if (entity instanceof SoulAnchorEntity anchor) {
			anchor.cancelRetrieve(player);
		}
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(0.0D, velocity.y, 0.0D);
		player.hasImpulse = true;
		player.zza = 0.0F;
		player.xxa = 0.0F;
		beginStage(player, skill, Stage.GUN_RECALL, recallTicks(skill));
	}

	private static void selectTarget(ServerPlayer player, Skill skill) {
		LivingEntity target = findAimTarget(player);
		if (target != null && target.getId() == skill.targetId) {
			return;
		}
		clearReadyTarget(player, skill);
		if (target == null) {
			skill.targetId = -1;
			return;
		}
		skill.targetId = target.getId();
		skill.targetHighlighted = true;
		broadcastStatus(player, skill.targetId, SoulAnchorStatusPayload.READY_TARGET,
				CLIENT_HOLD_TIMEOUT_TICKS, SoulAnchorMarks.COLOR);
	}

	private static LivingEntity findAimTarget(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 end = start.add(look.scale(TARGET_RANGE));
		BlockHitResult block = level.clip(new ClipContext(start, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end,
				new AABB(start, end).inflate(1.0D),
				entity -> entity instanceof LivingEntity && entity != player && entity.isAlive()
						&& !entity.isSpectator() && entity.isPickable()
						&& CombatTargeting.isTargetable(entity),
				TARGET_RANGE * TARGET_RANGE);
		double blockDistance = block.getType() == HitResult.Type.MISS
				? TARGET_RANGE : start.distanceTo(block.getLocation());
		if (hit != null && hit.getEntity() instanceof LivingEntity direct
				&& start.distanceTo(hit.getLocation()) <= blockDistance + 0.05D) {
			return direct;
		}
		LivingEntity nearest = null;
		double bestAngle = Math.cos(Math.toRadians(7.5D));
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(TARGET_RANGE),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.isPickable() && CombatTargeting.isTargetable(entity))) {
			Vec3 center = living.getBoundingBox().getCenter();
			Vec3 delta = center.subtract(start);
			double distance = delta.length();
			if (distance < 1.0E-4D || distance > TARGET_RANGE
					|| distance > blockDistance + 0.05D) {
				continue;
			}
			double cosine = delta.scale(1.0D / distance).dot(look);
			if (cosine < bestAngle || cosine == bestAngle && distance >= bestDistance) {
				continue;
			}
			bestAngle = cosine;
			bestDistance = distance;
			nearest = living;
		}
		return nearest;
	}

	private static void clearReadyTarget(ServerPlayer player, Skill skill) {
		if (skill.targetId < 0 || !skill.targetHighlighted) {
			return;
		}
		broadcastStatus(player, skill.targetId, SoulAnchorStatusPayload.CLEAR_READY_TARGET,
				0, SoulAnchorMarks.COLOR);
		skill.targetHighlighted = false;
	}

	private static void beginStage(ServerPlayer player, Skill skill, Stage stage, int duration) {
		skill.stage = stage;
		skill.stageStart = player.level().getGameTime();
		skill.stageEnd = skill.stageStart + duration;
		int phase = switch (stage) {
			case PLAYER_DRAG -> SoulAnchorSkillPayload.DRAG;
			case ENTITY_DRAG -> SoulAnchorSkillPayload.DRAG_ENTITY;
			case GUN_ENTITY_DRAG -> SoulAnchorSkillPayload.GUN_DRAG_ENTITY;
			case GUN_RECALL -> usesGunMotion(skill)
					? SoulAnchorSkillPayload.GUN_RECALL : SoulAnchorSkillPayload.RECALL;
			case GUN_SLIDING -> SoulAnchorSkillPayload.GUN_SLIDING;
			case GUN_SLIDING_TO_IDLE -> SoulAnchorSkillPayload.GUN_SLIDING_TO_IDLE;
			case GUN_SLIDING_TO_RUN -> SoulAnchorSkillPayload.GUN_SLIDING_TO_RUN;
			default -> usesGunMotion(skill) ? SoulAnchorSkillPayload.GUN_THROW
					: SoulAnchorSkillPayload.THROW;
		};
		syncPhase(player, skill, phase, duration + 2);
	}

	private static int recallTicks(Skill skill) {
		return ENTITY_DRAG_TICKS;
	}

	private static boolean usesGunMotion(Skill skill) {
		return skill.gun && !skill.sword;
	}

	private static void syncPhase(ServerPlayer player, Skill skill, int phase, int duration) {
		SoulAnchorSkillPayload payload = new SoulAnchorSkillPayload(player.getId(), phase,
				duration, skill.gun, skill.sword);
		for (ServerPlayer viewer : player.serverLevel().players()) {
			if (viewer.distanceToSqr(player) <= 64.0D * 64.0D) {
				ModNetwork.send(viewer, payload);
			}
		}
	}

	private static void finish(ServerPlayer player) {
		Skill skill = SKILLS.get(player.getUUID());
		if (skill == null) {
			return;
		}
		resumeAccelerationCountdown(player, skill, player.level().getGameTime());
		SKILLS.remove(player.getUUID());
		clearReadyTarget(player, skill);
		Entity anchor = player.serverLevel().getEntity(skill.anchorId);
		if (anchor instanceof SoulAnchorEntity soul && !soul.isRemoved()) {
			soul.discard();
		}
		SoulAnchorSkillPayload payload = new SoulAnchorSkillPayload(player.getId(),
				SoulAnchorSkillPayload.END, 0, skill.gun, skill.sword);
		for (ServerPlayer viewer : player.serverLevel().players()) {
			if (viewer.distanceToSqr(player) <= 64.0D * 64.0D) {
				ModNetwork.send(viewer, payload);
			}
		}
	}

	private static void broadcastStatus(ServerPlayer player, int targetEntityId, int action,
										int durationTicks, int color) {
		SoulAnchorStatusPayload payload = new SoulAnchorStatusPayload(player.getId(),
				targetEntityId, action, durationTicks, color);
		for (ServerPlayer viewer : player.serverLevel().players()) {
			if (viewer.distanceToSqr(player) <= 64.0D * 64.0D) {
				ModNetwork.send(viewer, payload);
			}
		}
	}
}
