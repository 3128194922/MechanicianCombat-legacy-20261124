package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.net.GunRollPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.util.Mth;

/** Post-dodge sprint steering and roll direction alignment. */
public final class SprintDirectionCorrection {
    private static int lastForward, lastSide;
    public static boolean correct(Minecraft client, KeyboardInput input) {
        if (client == null || client.player == null || client.level == null || client.screen != null
                || !GunCamera.active(client) || !ShoulderCouplingState.isSprintDirectionActive(client)
                || GunCamera.aiming(client) || !TkGun.holdingGun(client.player)) { reset(); return false; }
        int id=client.player.getId();
        if (GunClientState.focusish(id)) { reset(); return false; }
        // 翻滚后的加速窗口只给速度效果，不再接管 A/D 转向；否则左右翻滚会被“缓慢转向”拉偏。
        if (GunClientState.dashing(id)) { reset(); return false; }
        var state=GunClientState.get(id);
        if (state != null && switch(state.state) {
            case ROLL, ROLL_TO_IDLE, MELEE, SLIDE, SLIDE_READY, OVERHEAT, OVER, INSPECT, SKILL_JUMP, FALL_ATTACK, FALL_ATTACK_RECOVERY -> true;
            default -> false;
        }) { reset(); return false; }
        if (!DodgeSprintControl.forwardHeld(client.options.keyUp.isDown(), client.options.keyDown.isDown())
                || !client.player.isSprinting()
                || TkGunClient.isRollTriggerTick(client)) { reset(); return false; }
        int side = (client.options.keyLeft.isDown() ? 1 : 0) - (client.options.keyRight.isDown() ? 1 : 0);
        float yaw = DodgeSprintControl.steerYaw(movementCameraYaw(client), side);
        FallbackShoulderCamera.steerSprintView(yaw);
        client.player.setYRot(yaw); client.player.setYBodyRot(yaw); client.player.setYHeadRot(yaw);
        input.leftImpulse=0;
        return true;
    }
    /** Combat Roll reads the previous movement impulses and player yaw during key handling. */
    public static void prepareCombatRoll(Minecraft client) {
        if (client == null || client.player == null || client.player.input == null || !GunCamera.active(client)) return;
        var payload = TkGunClient.rollInput(client);
        client.player.input.forwardImpulse = payload.forward();
        client.player.input.leftImpulse = payload.left();
        client.player.setYRot(payload.yaw());
    }
    /**
     * 翻滚按下的同一帧只保留原始 WASD 输入。
     *
     * <p>Combat Roll 会先读 {@code forwardImpulse/leftImpulse}，再用**当时的玩家 yaw** 旋转这个向量。
     * 如果这里提前把身体转向“翻滚后的方向”，后退（S）会被再旋转一次，表现成“动画向后、位移向前”。
     */
    public static void alignForRoll(Minecraft client, GunRollPayload payload) {
        if (client == null || client.player == null || client.level == null || !GunCamera.active(client)) { reset(); return; }
        if (client.player.input != null) {
            client.player.input.forwardImpulse = payload.forward();
            client.player.input.leftImpulse = payload.left();
        }
        // 不在这里改玩家 yaw：Combat Roll 已经按原始输入计算了位移方向。
        // 提前把身体转向翻滚方向会让后续 S/A/D 变成相对新朝向的输入，造成“后滚向前加速”。
        lastForward = payload.forward();
        lastSide = payload.left();
    }
    public static float movementCameraYaw(Minecraft client) {
        return FallbackShoulderCamera.isFallbackActive(client) ? FallbackShoulderCamera.cameraYaw()
                : Mth.wrapDegrees(client.gameRenderer.getMainCamera().getYRot());
    }
    private static void reset() { lastForward=0; lastSide=0; }
    private SprintDirectionCorrection() { }
}
