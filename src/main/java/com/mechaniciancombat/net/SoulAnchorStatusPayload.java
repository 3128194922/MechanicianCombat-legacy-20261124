package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 灵魂锚瞄准目标标记与命中发光同步。 */
public record SoulAnchorStatusPayload(int ownerEntityId, int targetEntityId, int action,
                                      int durationTicks, int color) implements ModPacket {
    public static final int READY_TARGET = 0;
    public static final int CLEAR_READY_TARGET = 1;
    public static final int GLOW_TARGET = 2;
    public static final int CLEAR_GLOW_TARGET = 3;

    public static final PacketType<SoulAnchorStatusPayload> ID =
            new PacketType<>(SoulAnchorStatusPayload.class, TkGun.id("soul_anchor_status"));

    public static final PacketCodec<SoulAnchorStatusPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.ownerEntityId());
                buf.writeVarInt(value.targetEntityId());
                buf.writeVarInt(value.action());
                buf.writeVarInt(Math.max(0, value.durationTicks()));
                buf.writeInt(value.color());
            },
            buf -> new SoulAnchorStatusPayload(buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readInt()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
