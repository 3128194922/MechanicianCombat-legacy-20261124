package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 猎矛部署施法开始（服务端权威）。 */
public record HunterSpearSkillPayload(int playerEntityId, long startedAt, int durationTicks)
        implements ModPacket {
    public static final PacketType<HunterSpearSkillPayload> ID =
            new PacketType<>(HunterSpearSkillPayload.class, TkGun.id("hunter_spear_skill"));

    public static final PacketCodec<HunterSpearSkillPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.playerEntityId());
                buf.writeLong(value.startedAt());
                buf.writeVarInt(value.durationTicks());
            },
            buf -> new HunterSpearSkillPayload(buf.readVarInt(), buf.readLong(), buf.readVarInt()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
