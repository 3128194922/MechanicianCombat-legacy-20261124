package com.mechaniciancombat.client;

import com.mechaniciancombat.net.SoulChainPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 短时灵魂锁链特效（有序节点 + 淡出）。 */
public final class SoulChainClientState {
	public record Node(int entityId, Vec3 fallback) {
	}

	public record Effect(List<Node> nodes, long expiresAt, long startedAt) {
		public float alphaAt(long now) {
			long elapsed = Math.max(0L, now - startedAt);
			if (elapsed <= 10L) {
				return 1.0F;
			}
			return Math.max(0.0F, 1.0F - (elapsed - 10L) / 20.0F);
		}
	}

	private static final List<Effect> EFFECTS = new ArrayList<>();

	public static void apply(SoulChainPayload payload, long now) {
		List<Node> nodes = payload.nodes().stream()
				.map(node -> new Node(node.entityId(),
						new Vec3(node.x(), node.y(), node.z())))
				.toList();
		EFFECTS.add(new Effect(nodes, now + payload.durationTicks(), now));
	}

	public static void tick(long now) {
		Iterator<Effect> iterator = EFFECTS.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().expiresAt() <= now) {
				iterator.remove();
			}
		}
	}

	public static List<Effect> effects() {
		return EFFECTS;
	}

	public static void clear() {
		EFFECTS.clear();
	}

	private SoulChainClientState() {
	}
}
