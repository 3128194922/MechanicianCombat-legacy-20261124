package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/** 服务端向客户端同步的武器状态、弹药、热量与瞄准输入锁。 */
public record GunStatePayload(int playerId, byte state, int ticks, int heat, int shotIndex, int mag,
							  boolean reloading, boolean focus, int shotSeq, boolean dashing,
							  int dashTicks, int aimLockTicks, int heatLockTicks, String magAmmo,
							  int rollCooldownTicks, boolean meleeDash, boolean meleeReturning,
							  boolean meleeDashReturn, boolean meleeReturnRun, boolean meleeHoldOver,
							  boolean fortressSlide, boolean airborneIdle,
							  boolean kineticDraft, boolean kineticAccelerationAttack,
							  boolean kineticRunJump, boolean kineticReturn,
							  boolean kineticStance, int kineticHeat,
							  boolean kineticRollCombo) implements ModPacket {
	public GunStatePayload(int playerId, byte state, int ticks, int heat, int shotIndex, int mag,
						   boolean reloading, boolean focus, int shotSeq, boolean dashing,
						   int dashTicks, int aimLockTicks, int heatLockTicks, String magAmmo,
						   int rollCooldownTicks) {
		this(playerId, state, ticks, heat, shotIndex, mag, reloading, focus, shotSeq, dashing,
				dashTicks, aimLockTicks, heatLockTicks, magAmmo, rollCooldownTicks,
				false, false, false, false, false, false, false, false, false, false, false,
				false, 0, false);
	}

	public GunStatePayload(int playerId, byte state, int ticks, int heat, int shotIndex, int mag,
						   boolean reloading, boolean focus, int shotSeq, boolean dashing,
						   int dashTicks, int aimLockTicks, int heatLockTicks, String magAmmo,
						   int rollCooldownTicks, boolean meleeDash, boolean meleeReturning,
						   boolean meleeDashReturn, boolean meleeReturnRun, boolean meleeHoldOver,
						   boolean fortressSlide, boolean airborneIdle) {
		this(playerId, state, ticks, heat, shotIndex, mag, reloading, focus, shotSeq, dashing,
				dashTicks, aimLockTicks, heatLockTicks, magAmmo, rollCooldownTicks, meleeDash,
				meleeReturning, meleeDashReturn, meleeReturnRun, meleeHoldOver, fortressSlide,
				airborneIdle, false, false, false, false, false, 0, false);
	}

	public static final PacketType<GunStatePayload> ID =
			new PacketType<>(GunStatePayload.class, TkGun.id("state"));

	public static final PacketCodec<GunStatePayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.playerId());
				buf.writeByte(value.state());
				buf.writeVarInt(value.ticks());
				buf.writeVarInt(value.heat());
				buf.writeVarInt(value.shotIndex());
				buf.writeVarInt(value.mag());
				buf.writeBoolean(value.reloading());
				buf.writeBoolean(value.focus());
				buf.writeVarInt(value.shotSeq());
				buf.writeBoolean(value.dashing());
				buf.writeVarInt(value.dashTicks());
				buf.writeVarInt(value.aimLockTicks());
				buf.writeVarInt(value.heatLockTicks());
				buf.writeUtf(value.magAmmo());
				buf.writeVarInt(value.rollCooldownTicks());
				buf.writeBoolean(value.meleeDash());
				buf.writeBoolean(value.meleeReturning());
				buf.writeBoolean(value.meleeDashReturn());
				buf.writeBoolean(value.meleeReturnRun());
				buf.writeBoolean(value.meleeHoldOver());
				buf.writeBoolean(value.fortressSlide());
				buf.writeBoolean(value.airborneIdle());
				buf.writeBoolean(value.kineticDraft());
				buf.writeBoolean(value.kineticAccelerationAttack());
				buf.writeBoolean(value.kineticRunJump());
				buf.writeBoolean(value.kineticReturn());
				buf.writeBoolean(value.kineticStance());
				buf.writeVarInt(value.kineticHeat());
				buf.writeBoolean(value.kineticRollCombo());
			},
			buf -> new GunStatePayload(buf.readVarInt(), buf.readByte(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					// 宽读：旧服务端缺少尾部字段时不越界。
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readUtf(32767) : "",
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 && buf.readBoolean()));

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
