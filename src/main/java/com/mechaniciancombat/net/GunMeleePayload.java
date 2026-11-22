package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/** Press/release of the Veritas close-combat attack key. */
public record GunMeleePayload(boolean pressed, boolean movingForward) implements ModPacket {
    public static final PacketType<GunMeleePayload> ID = new PacketType<>(GunMeleePayload.class, TkGun.id("gun_melee"));
    public static final PacketCodec<GunMeleePayload> CODEC = PacketCodec.of(
            (value, buf) -> { buf.writeBoolean(value.pressed()); buf.writeBoolean(value.movingForward()); },
            buf -> new GunMeleePayload(buf.readBoolean(), buf.readBoolean()));
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
