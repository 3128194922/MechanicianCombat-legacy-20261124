package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;



/** Authoritative backpack phase, charge count and timed-window state. */
public record CombatBackpackStatusPayload(int ownerEntityId, int targetEntityId,
										  int phase, int charges,
										  int remainingTicks,
										  int launcherType,
										  int missilePhase,
										  int jumpEnergyPixels,
										  boolean jumpEnergyLocked)
		implements ModPacket {
	public static final int PHASE_READY = 0;
	public static final int PHASE_MARKED = 1;
	public static final int PHASE_COOLDOWN = 2;
	public static final int PHASE_AUTO_ACTIVE = 3;
	public static final int PHASE_AUTO_PAUSED = 4;
	public static final int PHASE_SUMMON_DORMANT = 5;
	public static final int PHASE_SUMMON_READY = 6;
	public static final int PHASE_SUMMON_ARMED = 7;
	public static final int PHASE_SUMMON_MARKED = 8;
	public static final int PHASE_SUMMON_CAST = 9;
	public static final int PHASE_READY_TO_FLY = 10;
	public static final int PHASE_FLYING = 11;
	public static final int PHASE_FALLING = 12;
	public static final int PHASE_CLOSE = 13;
	public static final int PHASE_BANNER_READY = 14;
	public static final int PHASE_BANNER_ACTIVE = 15;
	public static final int PHASE_BANNER_CD = 16;
	public static final int MISSILE_NONE = 0;
	public static final int MISSILE_AIM = 1;
	public static final int MISSILE_CD = 2;


	public static final PacketType<CombatBackpackStatusPayload> ID =
			new PacketType<>(CombatBackpackStatusPayload.class, TkGun.id("backpack_status"));

	public static final PacketCodec<CombatBackpackStatusPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.ownerEntityId());
						buf.writeVarInt(value.targetEntityId());
						buf.writeByte(value.phase());
						buf.writeByte(value.charges());
						buf.writeVarInt(value.remainingTicks());
						buf.writeVarInt(value.launcherType());
						buf.writeVarInt(value.missilePhase());
						buf.writeVarInt(value.jumpEnergyPixels());
						buf.writeBoolean(value.jumpEnergyLocked());
					},
					buf -> new CombatBackpackStatusPayload(
							buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(),
							buf.readUnsignedByte(), buf.readVarInt(),
							buf.readableBytes() > 0 ? buf.readVarInt() : 0,
							buf.readableBytes() > 0 ? buf.readVarInt() : 0,
							buf.readableBytes() > 0 ? buf.readVarInt() : 0,
							buf.readableBytes() > 0 && buf.readBoolean()));

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
