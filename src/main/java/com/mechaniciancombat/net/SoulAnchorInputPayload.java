package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 灵魂锚按键：按住蓄力、松开投掷、潜行取消。 */
public record SoulAnchorInputPayload(boolean held, boolean cancel, boolean sprinting)
        implements ModPacket {
    public static final PacketType<SoulAnchorInputPayload> ID =
            new PacketType<>(SoulAnchorInputPayload.class, TkGun.id("soul_anchor_input"));

    public static final PacketCodec<SoulAnchorInputPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.held());
                buf.writeBoolean(value.cancel());
                buf.writeBoolean(value.sprinting());
            },
            buf -> new SoulAnchorInputPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
