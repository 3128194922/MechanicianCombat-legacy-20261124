package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 跳跃推进器的跳跃键输入：按下起跳，按住持续推进。 */
public record JumpBackpackPayload(boolean pressed) implements ModPacket {
    public static final PacketType<JumpBackpackPayload> ID =
            new PacketType<>(JumpBackpackPayload.class, TkGun.id("jump_backpack"));

    public static final PacketCodec<JumpBackpackPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.pressed()),
            buf -> new JumpBackpackPayload(buf.readBoolean()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
