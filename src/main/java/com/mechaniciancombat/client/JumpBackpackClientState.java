package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.item.CombatBackpackItem;
import com.mechaniciancombat.net.CombatBackpackStatusPayload;
import com.mechaniciancombat.net.JumpBackpackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * 跳跃推进器的客户端镜像：转发跳跃键 + 按服务端相位驱动玩家空中动作。
 *
 * <p>按键必须在原版移动处理之前上报，否则服务端收到时玩家已经被标记离地，
 * 会被"必须在地面才能起跳"的判定拒绝。
 */
public final class JumpBackpackClientState {
    public enum Phase {
        IDLE, READY_TO_FLY, FLYING, FALLING, CLOSE, COOLDOWN
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static boolean sentJumpPressed;
    private static boolean previousJumpKey;

    private static final class Entry {
        Phase phase = Phase.IDLE;
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            if (sentJumpPressed) {
                ClientNetwork.send(new JumpBackpackPayload(false));
                sentJumpPressed = false;
            }
            previousJumpKey = false;
            return;
        }
        handleJumpKey(client, client.screen == null && client.options.keyJump.isDown());
        if (TkGun.holdingGun(player)) {
            // 持枪时身体由枪械 rig 接管，空中动作让位。
            JumpBackpackPlayerAnimation.clear(player.getUUID());
        } else {
            JumpBackpackPlayerAnimation.apply(player, phase(player.getId()));
        }
    }

    /** 跳跃键边沿检测：按下起跳，松开/切枪时补发释放。 */
    public static void handleJumpKey(Minecraft client, boolean jumpPressed) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        boolean equipped = BackpackUpgradeData.canUseJumpThruster(
                CombatBackpackItem.equippedStack(player));
        boolean rawPressed = client.screen == null && jumpPressed;
        boolean risingEdge = rawPressed && !previousJumpKey;
        previousJumpKey = rawPressed;
        boolean eligible = equipped && !TkGun.holdingGun(player);
        if (eligible && rawPressed && risingEdge && !sentJumpPressed) {
            ClientNetwork.send(new JumpBackpackPayload(true));
            sentJumpPressed = true;
        } else if ((!eligible || !rawPressed) && sentJumpPressed) {
            ClientNetwork.send(new JumpBackpackPayload(false));
            sentJumpPressed = false;
        }
    }

    public static void applyStatus(int playerId, int statusPhase) {
        Phase next = switch (statusPhase) {
            case CombatBackpackStatusPayload.PHASE_READY_TO_FLY -> Phase.READY_TO_FLY;
            case CombatBackpackStatusPayload.PHASE_FLYING -> Phase.FLYING;
            case CombatBackpackStatusPayload.PHASE_FALLING -> Phase.FALLING;
            case CombatBackpackStatusPayload.PHASE_CLOSE -> Phase.CLOSE;
            case CombatBackpackStatusPayload.PHASE_COOLDOWN -> Phase.COOLDOWN;
            default -> Phase.IDLE;
        };
        Entry entry = ENTRIES.computeIfAbsent(playerId, ignored -> new Entry());
        entry.phase = next;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null
                && client.level.getEntity(playerId) instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            if (TkGun.holdingGun(player)) {
                JumpBackpackPlayerAnimation.clear(player.getUUID());
            } else {
                JumpBackpackPlayerAnimation.apply(player, next);
            }
        }
        if (next == Phase.IDLE) {
            ENTRIES.remove(playerId);
        }
    }

    public static boolean isActive(int playerId) {
        Phase phase = phase(playerId);
        return phase == Phase.READY_TO_FLY || phase == Phase.FLYING || phase == Phase.FALLING;
    }

    public static Phase phase(int playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry == null ? Phase.IDLE : entry.phase;
    }

    public static void reset(int playerId) {
        ENTRIES.remove(playerId);
        Minecraft client = Minecraft.getInstance();
        if (client.level != null
                && client.level.getEntity(playerId) instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            JumpBackpackPlayerAnimation.clear(player.getUUID());
        }
    }

    public static void resetAll() {
        ENTRIES.clear();
        JumpBackpackPlayerAnimation.clearAll();
        sentJumpPressed = false;
        previousJumpKey = false;
    }

    private JumpBackpackClientState() { }
}
