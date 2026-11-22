package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端量到的战旗 locator 世界坐标 + 相机 yaw。
 *
 * <p>战旗落点要用模型上真正渲染出来的 locator 位置，否则第三人称视差会让旗子插偏。
 * 服务端只用 5 tick 内的最新样本，过期就退回按身体朝向推算。
 */
public record WarBannerAnchorPayload(double x, double y, double z, float cameraYaw)
        implements ModPacket {
    public static final PacketType<WarBannerAnchorPayload> ID =
            new PacketType<>(WarBannerAnchorPayload.class, TkGun.id("war_banner_anchor"));

    public static final PacketCodec<WarBannerAnchorPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
                buf.writeFloat(value.cameraYaw());
            },
            buf -> new WarBannerAnchorPayload(buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
