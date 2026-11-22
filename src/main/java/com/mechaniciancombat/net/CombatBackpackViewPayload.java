package com.mechaniciancombat.net;
import com.mechaniciancombat.TkGun;
/** Camera position and orientation for the automatic backpack's visible-target filter. */
public record CombatBackpackViewPayload(double x,double y,double z,double yaw,double pitch) implements ModPacket {
    public static final PacketType<CombatBackpackViewPayload> ID = new PacketType<>(CombatBackpackViewPayload.class,TkGun.id("backpack_view"));
    public static final PacketCodec<CombatBackpackViewPayload> CODEC = PacketCodec.of(
        (v,b)->{ b.writeDouble(v.x()); b.writeDouble(v.y()); b.writeDouble(v.z()); b.writeDouble(v.yaw()); b.writeDouble(v.pitch()); },
        b->new CombatBackpackViewPayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
