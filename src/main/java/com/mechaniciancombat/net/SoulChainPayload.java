package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** 灵魂锁链命中的有序节点（客户端绘制锁链带）。 */
public record SoulChainPayload(List<ChainNode> nodes, int durationTicks) implements ModPacket {
	public record ChainNode(int entityId, double x, double y, double z) {
	}

	public static final PacketType<SoulChainPayload> ID =
			new PacketType<>(SoulChainPayload.class, TkGun.id("soul_chain_effect"));

	public static final PacketCodec<SoulChainPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.nodes().size());
				for (ChainNode node : value.nodes()) {
					buf.writeVarInt(node.entityId());
					buf.writeDouble(node.x());
					buf.writeDouble(node.y());
					buf.writeDouble(node.z());
				}
				buf.writeVarInt(Math.max(1, value.durationTicks()));
			},
			buf -> {
				int count = buf.readVarInt();
				List<ChainNode> nodes = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					nodes.add(new ChainNode(buf.readVarInt(), buf.readDouble(),
							buf.readDouble(), buf.readDouble()));
				}
				return new SoulChainPayload(List.copyOf(nodes), buf.readVarInt());
			});

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
