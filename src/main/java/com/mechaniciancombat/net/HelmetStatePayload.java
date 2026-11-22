package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** 服务端权威的头盔动画状态，广播给附近所有观察者。 */
public record HelmetStatePayload(UUID playerUuid, boolean expanded,
								 long transitionStartedAt,
								 int transitionDurationTicks) implements ModPacket {
	public static final PacketType<HelmetStatePayload> ID =
			new PacketType<>(HelmetStatePayload.class, TkGun.id("helmet_state"));

	public static final PacketCodec<HelmetStatePayload> CODEC = PacketCodec.of(
			(value, buffer) -> {
				buffer.writeUUID(value.playerUuid());
				buffer.writeBoolean(value.expanded());
				buffer.writeLong(value.transitionStartedAt());
				buffer.writeVarInt(Math.max(0, value.transitionDurationTicks()));
			},
			buffer -> new HelmetStatePayload(buffer.readUUID(), buffer.readBoolean(),
					buffer.readLong(), buffer.readVarInt()));

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
