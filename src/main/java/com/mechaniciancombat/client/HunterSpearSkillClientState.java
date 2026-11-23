package com.mechaniciancombat.client;

import com.mechaniciancombat.net.HunterSpearAnchorPayload;
import com.mechaniciancombat.net.HunterSpearSkillPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 猎矛部署：驱动玩家动作，并在末段把真实 locator 报给服务端决定炮台落点。 */
public final class HunterSpearSkillClientState {
    private record Entry(UUID playerUuid, long startedAt, long endsAt) {
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<Integer, Vec3> MARKERS = new HashMap<>();

    public static void apply(HunterSpearSkillPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null
                || !(client.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player)) {
            return;
        }
        ENTRIES.put(payload.playerEntityId(), new Entry(player.getUUID(),
                payload.startedAt(), payload.startedAt() + payload.durationTicks()));
        MARKERS.remove(payload.playerEntityId());
        HunterSpearPlayerAnimation.play(player);
    }

    /** 手持猎矛渲染层每帧量到 locator 时调用。 */
    public static void updateMarker(int playerId, Vec3 marker) {
        MARKERS.put(playerId, marker);
    }

    public static boolean isActive(int playerId) {
        return ENTRIES.containsKey(playerId);
    }

    /** 本次部署的起始 game time（第一人称渲染器据此判断"是不是新的一次部署"）。 */
    public static long startedAt(int playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry == null ? -1L : entry.startedAt();
    }

    public static double animationSeconds(int playerId, long now, float partialTick) {
        Entry entry = ENTRIES.get(playerId);
        if (entry == null) return 0.0D;
        return Math.max(0.0D, Math.min(1.15D,
                (now - entry.startedAt() + partialTick) / 20.0D));
    }

    public static void tick(long now) {
        Minecraft client = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Entry> item = iterator.next();
            Entry entry = item.getValue();
            if (client.player != null && client.player.getId() == item.getKey()
                    && now >= entry.endsAt() - 3L && now < entry.endsAt()) {
                Vec3 marker = MARKERS.get(item.getKey());
                if (marker != null) {
                    ClientNetwork.send(new HunterSpearAnchorPayload(
                            marker.x, marker.y, marker.z, client.player.getYRot()));
                }
            }
            if (now < entry.endsAt()) {
                continue;
            }
            HunterSpearPlayerAnimation.clear(entry.playerUuid());
            MARKERS.remove(item.getKey());
            iterator.remove();
        }
    }

    public static void reset() {
        ENTRIES.clear();
        MARKERS.clear();
        HunterSpearPlayerAnimation.clearAll();
    }

    private HunterSpearSkillClientState() { }
}
