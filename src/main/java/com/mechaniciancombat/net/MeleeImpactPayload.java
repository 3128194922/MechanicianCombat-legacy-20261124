package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/** Server-authoritative hitstop event for observing clients. */
public record MeleeImpactPayload(int attackerId, int[] targetIds, int ticks, boolean critical, boolean lockAttacker, boolean cameraShake) implements ModPacket {
    public static final PacketType<MeleeImpactPayload> ID = new PacketType<>(MeleeImpactPayload.class, TkGun.id("melee_impact"));
    public static final PacketCodec<MeleeImpactPayload> CODEC = PacketCodec.of(
            (v,b) -> { b.writeVarInt(v.attackerId()); b.writeVarInt(v.targetIds().length); for (int id : v.targetIds()) b.writeVarInt(id); b.writeVarInt(v.ticks()); b.writeBoolean(v.critical()); b.writeBoolean(v.lockAttacker()); b.writeBoolean(v.cameraShake()); },
            b -> { int attacker = b.readVarInt(); int count = b.readVarInt(); if (count < 0 || count > 1024) throw new IllegalArgumentException("invalid hit target count"); int[] ids = new int[count]; for (int i=0; i<count; i++) ids[i] = b.readVarInt(); return new MeleeImpactPayload(attacker,ids,b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean()); });
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
