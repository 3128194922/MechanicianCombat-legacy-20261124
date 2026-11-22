package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 战旗施法开始（服务端权威）：客户端据此播玩家施法动作与镜头 FOV 变化。
 *
 * @param playerEntityId 施法玩家
 * @param startedAt      服务端世界 tick
 * @param durationTicks  施法总时长
 * @param gunHeld        施法时是否手持枪械（手持时锁移动）
 */
public record WarBannerSkillPayload(int playerEntityId, long startedAt,
                                    int durationTicks, boolean gunHeld)
        implements ModPacket {
    public static final PacketType<WarBannerSkillPayload> ID =
            new PacketType<>(WarBannerSkillPayload.class, TkGun.id("war_banner_skill"));

    public static final PacketCodec<WarBannerSkillPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.playerEntityId());
                buf.writeLong(value.startedAt());
                buf.writeVarInt(value.durationTicks());
                buf.writeBoolean(value.gunHeld());
            },
            buf -> new WarBannerSkillPayload(buf.readVarInt(), buf.readLong(),
                    buf.readVarInt(), buf.readBoolean()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
