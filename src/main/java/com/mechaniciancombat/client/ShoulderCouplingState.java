package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import net.minecraft.client.Minecraft;

/**
 * 相机与玩家朝向的耦合策略。蹲射时身体直接跟随准心，翻滚和滑铲保持解耦。
 */
public final class ShoulderCouplingState {
	private static boolean registered;

	public static boolean shoulderCouplingRegistered() {
		return registered;
	}

	static void markRegistered() {
		registered = true;
	}

	public static boolean shouldCoupleCamera(Minecraft client) {
		if (client == null || client.player == null || client.screen != null) {
			return false;
		}
		if (!TkGun.holdingGun(client.player)) {
			// ShoulderSurfing may expose vanilla THIRD_PERSON_BACK underneath
			// its own perspective. Keep non-item shoulder use decoupled.
			if (net.minecraftforge.fml.ModList.get().isLoaded("shouldersurfing") && ShoulderSurfBridge.isOverShoulderActive()
					&& !client.player.isUsingItem()) {
				return false;
			}
			// Outside gun handling, vanilla third-person should keep camera and
			// player coupled instead of inheriting the gun's decoupled mode.
			return client.options.getCameraType()
					== net.minecraft.client.CameraType.THIRD_PERSON_BACK;
		}
        GunClientState.Entry skill = GunClientState.get(client.player.getId());
        if (fortressCoupled(skill)) return true;
        // 空格快速六连斩：相机与身体解耦，可 360° 环视但不改变身体朝向。
        if (GunClientState.kineticComboLocksMovement(client.player.getId())) {
            return false;
        }
        if (skill != null && skill.state == TkGun.State.MELEE) return !skill.meleeHoldOver;
		if (client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT) {
			return false;
		}
		int id = client.player.getId();
		GunClientState.Entry state = GunClientState.get(id);
		if (GunClientState.focusish(id)) {
			return focusFollowsCamera(true, state == null ? null : state.state);
		}
		if (client.player.isUsingItem()) return false;
		if (state == null) {
			return true;
		}
		if (state.state == TkGun.State.IDLE && !TkGunClient.isMoving(id)
				&& !TkGunClient.hasMovementInput(id)
				&& !com.mechaniciancombat.client.rig.BowgunRig.recentUpperOperation(client.player)) return false;
		return switch (state.state) {
			case ROLL, ROLL_TO_IDLE, SLIDE, SLIDE_READY, OVERHEAT, OVER, INSPECT, SKILL_JUMP, FALL_ATTACK, FALL_ATTACK_RECOVERY -> false;
			default -> true;
		};
	}

	static boolean focusFollowsCamera(boolean focus, TkGun.State state) {
		return focus && state != TkGun.State.ROLL && state != TkGun.State.ROLL_TO_IDLE
				&& state != TkGun.State.SLIDE && state != TkGun.State.SLIDE_READY;
	}

	/**
	 * True when the camera/body split must survive a held right-click.
	 * ShoulderSurfing's when_using_item=ALWAYS path would otherwise turn the body.
	 */
	public static boolean requiresDecoupledView(Minecraft client) {
		return decoupledReason(client) != 0;
	}

	/**
	 * Identifies which decoupled state currently owns the camera. Returning the
	 * actual reason lets aiming start inside one state, then release only when a
	 * different decoupled state is entered while right-click is still held.
	 */
	public static int decoupledReason(Minecraft client) {
		if (client == null || client.player == null || client.screen != null
				|| !TkGun.holdingGun(client.player)) {
			return 0;
		}
		int id = client.player.getId();
        GunClientState.Entry skill = GunClientState.get(id);
        if (skill != null && skill.state == TkGun.State.FORTRESS_SKILL_HIT) return 17;
        if (fortressCoupled(skill)) return 0;
        // 空格快速六连斩：解耦视角（身体朝向锁定）。
        if (GunClientState.kineticComboLocksMovement(id)) return 16;
        if (skill != null && skill.state == TkGun.State.MELEE) return skill.meleeHoldOver ? 13 : 0;
        if (skill != null && skill.state == TkGun.State.SKILL_JUMP) return 15;
        if (skill != null && skill.state == TkGun.State.FALL_ATTACK) return 18;
        if (skill != null && skill.state == TkGun.State.FALL_ATTACK_RECOVERY) return 19;
		if (client.player.isSprinting()) {
			return 1;
		}
		if (client.player.isUsingItem()
				&& com.mechaniciancombat.TkGun.isGun(client.player.getUseItem())) {
			return 12;
		}
		if (GunClientState.focusish(id)) {
			return 3;
		}
		GunClientState.Entry e = GunClientState.get(id);
		if (e == null) {
			return GunClientState.aimLocked(id) ? 12 : 0;
		}
		return switch (e.state) {
			case ROLL -> 4;
			case ROLL_TO_IDLE -> 5;
			case SLIDE -> 7;
			case SLIDE_READY -> 8;
			case OVERHEAT -> 9;
			case OVER -> 10;
			case INSPECT -> 11;
			default -> GunClientState.aimLocked(id) ? 12 : 0;
		};
	}

    public static boolean isSprintDirectionActive(Minecraft client) {
        if (client == null || client.player == null) return false;
        boolean moving = client.options.keyUp.isDown() || client.options.keyDown.isDown()
                || client.options.keyLeft.isDown() || client.options.keyRight.isDown();
        return client.player.isSprinting() || (client.options.keySprint.isDown() && moving);
    }
    private static boolean fortressCoupled(GunClientState.Entry state) {
        return state != null && (state.state == TkGun.State.FORTRESS_SKILL_LOAD
                || state.state == TkGun.State.FORTRESS_SKILL_READY_GATHER || state.state == TkGun.State.FORTRESS_SKILL_GATHER);
    }
	private ShoulderCouplingState() {
	}
}
