package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/**
 * 动能剑右键意图：切换“立场”。
 *
 * <p>客户端只报“右键被按了一下”，是否真的能开/关由服务端按当前热量、
 * 是否持剑、是否在不可操作状态里判定。
 */
public record KineticStancePayload() implements ModPacket {
    public static final PacketType<KineticStancePayload> ID =
            new PacketType<>(KineticStancePayload.class, TkGun.id("kinetic_stance"));

    public static final PacketCodec<KineticStancePayload> CODEC = PacketCodec.of(
            (value, buf) -> { },
            buf -> new KineticStancePayload());

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
