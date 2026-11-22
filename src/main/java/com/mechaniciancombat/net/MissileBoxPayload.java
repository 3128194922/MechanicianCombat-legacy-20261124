package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 导弹舱输入：按下开始锁定、松开齐射；附带相机视锥与六个发射口位置。
 *
 * <p>端口坐标由客户端背包渲染层量出来，服务端用它决定每枚导弹的出膛点；
 * 没量到就退回玩家包围盒中心（仍然能打，只是不从舱口出来）。
 */
public record MissileBoxPayload(boolean release,
                                double cameraX, double cameraY, double cameraZ,
                                double cameraYaw, double cameraPitch,
                                double halfHorizontalTan, double halfVerticalTan,
                                double[] ports) implements ModPacket {
    public static final PacketType<MissileBoxPayload> ID =
            new PacketType<>(MissileBoxPayload.class, TkGun.id("missile_box_action"));
    private static final int PORT_VALUES = 18;

    public static final PacketCodec<MissileBoxPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.release());
                buf.writeDouble(value.cameraX());
                buf.writeDouble(value.cameraY());
                buf.writeDouble(value.cameraZ());
                buf.writeDouble(value.cameraYaw());
                buf.writeDouble(value.cameraPitch());
                buf.writeDouble(value.halfHorizontalTan());
                buf.writeDouble(value.halfVerticalTan());
                for (int index = 0; index < PORT_VALUES; index++) {
                    buf.writeDouble(value.ports() != null && value.ports().length > index
                            ? value.ports()[index] : 0.0D);
                }
            },
            buf -> {
                boolean release = buf.readBoolean();
                double cx = buf.readDouble();
                double cy = buf.readDouble();
                double cz = buf.readDouble();
                double yaw = buf.readDouble();
                double pitch = buf.readDouble();
                double horizontal = buf.readDouble();
                double vertical = buf.readDouble();
                double[] ports = new double[PORT_VALUES];
                for (int index = 0; index < ports.length; index++) {
                    ports[index] = buf.readDouble();
                }
                return new MissileBoxPayload(release, cx, cy, cz, yaw, pitch,
                        horizontal, vertical, ports);
            });

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
