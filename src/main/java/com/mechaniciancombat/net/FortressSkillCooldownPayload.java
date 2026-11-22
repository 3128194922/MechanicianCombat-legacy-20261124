package com.mechaniciancombat.net;
import com.mechaniciancombat.TkGun;
public record FortressSkillCooldownPayload(int remainingTicks) implements ModPacket {
    public static final PacketType<FortressSkillCooldownPayload> ID = new PacketType<>(FortressSkillCooldownPayload.class, TkGun.id("fortress_skill_cooldown"));
    public static final PacketCodec<FortressSkillCooldownPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeVarInt(Math.max(0,value.remainingTicks())), buf -> new FortressSkillCooldownPayload(buf.readVarInt()));
    @Override public PacketType<? extends ModPacket> getId() { return ID; }
}
