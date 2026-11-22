package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 客户端量到的猎矛 locator 世界坐标 + 相机 yaw。 */
public record HunterSpearAnchorPayload(double x, double y, double z, float cameraYaw)
        implements ModPacket {
    public static final PacketType<HunterSpearAnchorPayload> ID =
            new PacketType<>(HunterSpearAnchorPayload.class, TkGun.id("hunter_spear_anchor"));

    public static final PacketCodec<HunterSpearAnchorPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
                buf.writeFloat(value.cameraYaw());
            },
            buf -> new HunterSpearAnchorPayload(buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
