package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 动能剑的移动意图（用于冲刺加速判定）。 */
public record KineticSwordMovementPayload(boolean moving) implements ModPacket {
    public static final PacketType<KineticSwordMovementPayload> ID =
            new PacketType<>(KineticSwordMovementPayload.class, TkGun.id("kinetic_sword_movement"));

    public static final PacketCodec<KineticSwordMovementPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.moving()),
            buf -> new KineticSwordMovementPayload(buf.readBoolean()));

    @Override
    public PacketType<? extends ModPacket> getId() {
        return ID;
    }
}
