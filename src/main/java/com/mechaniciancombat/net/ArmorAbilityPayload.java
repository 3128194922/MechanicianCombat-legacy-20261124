package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 护甲模块充能与冷却（服务端权威，用于右侧 HUD）。 */
public record ArmorAbilityPayload(boolean shieldInstalled, boolean totemInstalled,
								  boolean soulChainInstalled, int shieldCharges,
								  int shieldRechargeKills, int shieldRechargeWindowTicks,
								  int shieldCooldownTicks, int totemCooldownTicks) implements ModPacket {
	public static final PacketType<ArmorAbilityPayload> ID =
			new PacketType<>(ArmorAbilityPayload.class, TkGun.id("armor_ability"));

	public static final PacketCodec<ArmorAbilityPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeBoolean(value.shieldInstalled());
				buf.writeBoolean(value.totemInstalled());
				buf.writeBoolean(value.soulChainInstalled());
				buf.writeVarInt(Math.max(0, value.shieldCharges()));
				buf.writeVarInt(Math.max(0, value.shieldRechargeKills()));
				buf.writeVarInt(Math.max(0, value.shieldRechargeWindowTicks()));
				buf.writeVarInt(Math.max(0, value.shieldCooldownTicks()));
				buf.writeVarInt(Math.max(0, value.totemCooldownTicks()));
			},
			buf -> new ArmorAbilityPayload(buf.readBoolean(), buf.readBoolean(),
					buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
