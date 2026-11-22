package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 把现有动能剑状态机解析成第一人称渲染器要播的动画。
 * 不改变任何玩法状态。
 */
public final class KineticSwordFirstPersonState {
	public record Snapshot(String animation, long revision, double speed) {
	}

	private static volatile Snapshot snapshot = new Snapshot("idle", 0L, 1.0D);

	public static Snapshot resolve(LocalPlayer player) {
		int id = player.getId();
		GunClientState.Entry gun = GunClientState.get(id);
		SoulAnchorSkillClientState.Entry anchor = SoulAnchorSkillClientState.get(id);

		if (anchor != null && anchor.swordSkill()) {
			String name = SoulAnchorSkillClientState.animationName(id);
			if (name != null) {
				return new Snapshot(name, anchor.revision(), 1.0D);
			}
		}

		if (SoulAnchorSkillClientState.postSwordDragActive(id)) {
			if (player.onGround()) {
				SoulAnchorSkillClientState.clearPostSwordDrag(id);
			} else {
				String name = player.getDeltaMovement().y > 0.01D
						? "chain_drag_sword" : "chain_drag_sword_fall";
				return new Snapshot(name, gun == null ? 0L : gun.shotSeq, 1.0D);
			}
		}

		long revision = gun == null ? 0L : gun.shotSeq;
		String name;
		if (gun != null && (gun.state == TkGun.State.ROLL || gun.state == TkGun.State.ROLL_TO_IDLE)) {
			// 手持动能剑翻滚：真实翻滚位移，动画用持剑奔跑动作。
			name = gun.state == TkGun.State.ROLL ? "GS_run" : "idle";
		} else if (gun != null && gun.state == TkGun.State.MELEE) {
			int step = Math.max(1, Math.min(6, gun.shotIndex));
			if (gun.kineticDraft) {
				name = step == 2 ? "GS_ATK_2-3_draft" : "GS_ATK_4-5_draft";
			} else {
				name = "GS_ATK_" + step + (gun.meleeReturning ? "_back-idle" : "");
			}
		} else if (!player.onGround()) {
			boolean runJump = gun != null && gun.kineticRunJump;
			name = player.getDeltaMovement().y > 0.01D
					? (runJump ? "GS_run_jump" : "jump")
					: (runJump ? "GS_run_fall" : "fall");
		} else if (gun != null && gun.state == TkGun.State.IDLE
				&& TkGunClient.ticks() < gun.kineticSwordLandingIdleUntil) {
			// r993/r994：落地后 6 tick 停在收势尾帧，否则第一人称会立刻切回 idle/GS_run。
			name = "fall_back-idle";
		} else {
			String walk = TkGunClient.walkAnimation(id);
			boolean moving = TkGunClient.isMoving(id) || TkGunClient.hasMovementInput(id);
			boolean sprintInput = player.isSprinting();
			Minecraft client = Minecraft.getInstance();
			if (player == client.player) {
				sprintInput = sprintInput || client.options.keySprint.isDown();
			}
			double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
			boolean running = moving && ("run".equals(walk) || sprintInput || horizontalSpeed >= 0.25D);
			if (running) {
				name = "GS_run";
			} else if (walk != null && walk.startsWith("walk_")) {
				name = "GS_" + walk;
			} else {
				name = "idle";
			}
		}
		return new Snapshot(name, revision, speedFor(id, name, gun));
	}

	private static double speedFor(int id, String name, GunClientState.Entry gun) {
		if (MeleeImpactClientState.freezesAnimation(id)) {
			return 0.0D;
		}
		// 翻滚按 Combat Roll 的实际时长压缩播放，和第三人称 `roll` 的换算一致。
		if ("roll".equals(name)) {
			return 12.0D / Math.max(1, com.mechaniciancombat.CombatRollHook.ROLL_DURATION);
		}
		// 真实翻滚期间播放持剑奔跑片段：按 1 倍速循环，避免走路采样为 0 时冻帧。
		if ("GS_run".equals(name) && gun != null
				&& (gun.state == TkGun.State.ROLL || gun.state == TkGun.State.ROLL_TO_IDLE)) {
			return 1.0D;
		}
		if (gun != null && gun.kineticRollCombo) {
			return TkGun.KINETIC_SWORD_ROLL_COMBO_SPEED;
		}
		if (name.startsWith("GS_walk_") || "GS_run".equals(name)) {
			return TkGunClient.walkAnimationSpeed(id);
		}
		// r993/r994：近战片段按"基础时长 / 本段实际时长"调速，加速段与 draft 保持 1.0。
		if (name.startsWith("GS_ATK_") && gun != null) {
			if (gun.kineticAccelerationAttack || gun.kineticDraft) {
				return 1.0D;
			}
			if (gun.state == TkGun.State.MELEE && gun.shotIndex >= 2 && gun.shotIndex <= 5) {
				int baseTicks = TkGun.KINETIC_SWORD_ATTACK_TICKS[gun.shotIndex - 1];
				return baseTicks / (double) Math.max(1, gun.total);
			}
		}
		return 1.0D;
	}

	public static void update(Snapshot value) {
		snapshot = value == null ? new Snapshot("idle", 0L, 1.0D) : value;
	}

	public static String animationName() {
		return snapshot.animation();
	}

	public static long revision() {
		return snapshot.revision();
	}

	public static double animationSpeed() {
		return snapshot.speed();
	}

	private KineticSwordFirstPersonState() {
	}
}
