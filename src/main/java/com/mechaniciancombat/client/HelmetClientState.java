package com.mechaniciancombat.client;

import com.mechaniciancombat.item.MechanicianUndersuitItem;
import com.mechaniciancombat.net.HelmetStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 头盔展开/折叠的客户端镜像（原版头部层与枪械皮肤层共用）。 */
public final class HelmetClientState {
	private static final float EXPAND_HAIR_HIDE_SECONDS = 0.25F;
	private static final float FOLD_HAIR_REVEAL_SECONDS = 0.38F;

	public record Pose(String animation, float seconds) {
	}

	private record State(boolean expanded, long startedAt, int durationTicks) {
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private HelmetClientState() {
	}

	public static void apply(HelmetStatePayload payload) {
		STATES.put(payload.playerUuid(), new State(payload.expanded(),
				payload.transitionStartedAt(), payload.transitionDurationTicks()));
	}

	public static void reset() {
		STATES.clear();
	}

	public static Pose pose(LivingEntity entity) {
		State state = STATES.get(entity.getUUID());
		if (state == null) {
			return new Pose("收起", 0.625F);
		}
		if (state.expanded()) {
			return state.durationTicks() > 0
					? new Pose("展开", elapsedSeconds(state)) : new Pose("展开", 0.625F);
		}
		return state.durationTicks() > 0
				? new Pose("收起", elapsedSeconds(state)) : new Pose("收起", 0.625F);
	}

	/** 展开前 0.25s 保留第二层头发；折叠到 0.38s 才重新露出。 */
	public static boolean shouldHideHair(LivingEntity entity) {
		if (!MechanicianUndersuitItem.isWearingFullSet(entity)) {
			return false;
		}
		State state = STATES.get(entity.getUUID());
		if (state == null) {
			return false;
		}
		if (state.expanded()) {
			return state.durationTicks() <= 0 || !transitionActive(state)
					|| elapsedSeconds(state) >= EXPAND_HAIR_HIDE_SECONDS;
		}
		return state.durationTicks() > 0 && transitionActive(state)
				&& elapsedSeconds(state) < FOLD_HAIR_REVEAL_SECONDS;
	}

	private static float elapsedSeconds(State state) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return state.durationTicks() / 20.0F;
		}
		float tickDelta = client.getFrameTime();
		float elapsedTicks = client.level.getGameTime() - state.startedAt() + tickDelta;
		return Math.max(0.0F, Math.min(state.durationTicks(), elapsedTicks)) / 20.0F;
	}

	private static boolean transitionActive(State state) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || state.durationTicks() <= 0) {
			return false;
		}
		return client.level.getGameTime() + client.getFrameTime()
				< state.startedAt() + state.durationTicks();
	}
}
