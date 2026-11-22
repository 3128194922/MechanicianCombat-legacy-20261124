package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/** The client requests the held weapon's jump-key action; the server validates it. */
public record GunJumpPayload() implements ModPacket {
    public static final PacketType<GunJumpPayload> ID = new PacketType<>(GunJumpPayload.class, TkGun.id("gun_jump"));
    public static final PacketCodec<GunJumpPayload> CODEC = PacketCodec.of((value, buf) -> { }, buf -> new GunJumpPayload());
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
