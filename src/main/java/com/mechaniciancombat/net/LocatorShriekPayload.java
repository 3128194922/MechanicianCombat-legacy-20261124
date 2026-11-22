package com.mechaniciancombat.net;
import com.mechaniciancombat.TkGun;
public record LocatorShriekPayload(int playerId,double x,double y,double z) implements ModPacket {
    public static final PacketType<LocatorShriekPayload> ID = new PacketType<>(LocatorShriekPayload.class,TkGun.id("locator_shriek"));
    public static final PacketCodec<LocatorShriekPayload> CODEC = PacketCodec.of(
        (v,b) -> { b.writeVarInt(v.playerId()); b.writeDouble(v.x()); b.writeDouble(v.y()); b.writeDouble(v.z()); },
        b -> new LocatorShriekPayload(b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
