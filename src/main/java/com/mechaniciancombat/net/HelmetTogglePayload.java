package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;

/** 客户端请求展开/收起机械师底衣头盔。 */
public record HelmetTogglePayload() implements ModPacket {
	public static final PacketType<HelmetTogglePayload> ID =
			new PacketType<>(HelmetTogglePayload.class, TkGun.id("helmet_toggle"));

	public static final PacketCodec<HelmetTogglePayload> CODEC = PacketCodec.of(
			(value, buffer) -> {
			},
			buffer -> new HelmetTogglePayload());

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
