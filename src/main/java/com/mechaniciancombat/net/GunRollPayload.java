package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;



/**
 * 客户端翻滚意图：按键方向和触发时玩家朝向。服务端校验输入后决定状态与位移；
 * 可选 Combat Roll 未提供物理翻滚时，使用同一方向进行兜底。
 */
public record GunRollPayload(int forward, int left, float yaw) implements ModPacket {
	public static final PacketType<GunRollPayload> ID =
			new PacketType<>(GunRollPayload.class, TkGun.id("roll_key"));

	public static final PacketCodec<GunRollPayload> CODEC = PacketCodec.of(
			(value, buf) -> { buf.writeByte(value.forward); buf.writeByte(value.left); buf.writeFloat(value.yaw); },
			buf -> new GunRollPayload(buf.readByte(), buf.readByte(), buf.readFloat()));

	public boolean valid() {
		return forward >= -1 && forward <= 1 && left >= -1 && left <= 1
				&& Float.isFinite(yaw) && Math.abs(yaw) <= 36000.0F;
	}

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
