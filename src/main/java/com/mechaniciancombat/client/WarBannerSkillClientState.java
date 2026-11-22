package com.mechaniciancombat.client;

import com.mechaniciancombat.net.WarBannerAnchorPayload;
import com.mechaniciancombat.net.WarBannerSkillPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 跟踪战旗施法：驱动镜头 FOV 拉远，并在施法末段把客户端量到的 locator 报给服务端。
 *
 * <p>落点必须用**渲染出来的** locator，否则第三人称视差会让旗子插偏；
 * 服务端只用 5 tick 内的样本，过期就退回按相机 yaw 推算。
 */
public final class WarBannerSkillClientState {
    private record Entry(UUID playerUuid, long startedAt, long endsAt, boolean gunHeld) {
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<Integer, Vec3> LOCATORS = new HashMap<>();

    public static void apply(WarBannerSkillPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null
                || !(client.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player)) {
            return;
        }
        ENTRIES.put(payload.playerEntityId(), new Entry(player.getUUID(),
                payload.startedAt(), payload.startedAt() + payload.durationTicks(), payload.gunHeld()));
        LOCATORS.remove(payload.playerEntityId());
        if (player == client.player && payload.gunHeld()) {
            // 手持枪械的施法：收到包的当帧就停住移动，不等下一次移动同步。
            player.setSprinting(false);
            player.zza = 0.0F;
            player.xxa = 0.0F;
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, velocity.y, 0.0D);
        }
    }

    /** 渲染器每帧量到旗杆 locator 时调用。 */
    public static void updateLocator(int playerId, Vec3 position) {
        LOCATORS.put(playerId, position);
    }

    public static boolean isActive(int playerId) {
        return ENTRIES.containsKey(playerId);
    }

    public static boolean usesGunAnimation(int playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry != null && entry.gunHeld();
    }

    /** 本次施法的起始 game time（第一人称渲染器据此判断"是不是新的一次施法"）。 */
    public static long startedAt(int playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry == null ? -1L : entry.startedAt();
    }

    /** 施法进度（秒），用于镜头与动作插值。 */
    public static double animationSeconds(int playerId, long now, float partialTick) {
        Entry entry = ENTRIES.get(playerId);
        if (entry == null) return 0.0D;
        return Math.max(0.0D, Math.min(1.125D,
                (now - entry.startedAt() + partialTick) / 20.0D));
    }

    /** 每 tick 清理过期条目，并在末段上报 locator。 */
    public static void tick(long now) {
        Minecraft client = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Entry> item = iterator.next();
            Entry entry = item.getValue();
            if (client.player != null && client.player.getId() == item.getKey()
                    && now >= entry.endsAt() - 3L && now < entry.endsAt()) {
                Vec3 locator = LOCATORS.get(item.getKey());
                if (locator != null) {
                    ClientNetwork.send(new WarBannerAnchorPayload(locator.x, locator.y, locator.z,
                            client.gameRenderer.getMainCamera().getYRot()));
                }
            }
            if (now < entry.endsAt()) {
                continue;
            }
            LOCATORS.remove(item.getKey());
            iterator.remove();
        }
    }

    public static void reset() {
        ENTRIES.clear();
        LOCATORS.clear();
    }

    private WarBannerSkillClientState() { }
}
