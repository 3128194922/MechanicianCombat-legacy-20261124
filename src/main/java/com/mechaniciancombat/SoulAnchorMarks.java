package com.mechaniciancombat;

import com.mechaniciancombat.net.ModNetwork;
import com.mechaniciancombat.net.SoulAnchorStatusPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端权威的 15 秒灵魂锚目标标记（发光 + 客户端颜色描边）。 */
public final class SoulAnchorMarks {
	public static final int DURATION_TICKS = 15 * 20;
	public static final int COLOR = 0xA9FCEA;
	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private record Mark(ServerLevel level, UUID entityId, long expiresAt, boolean wasGlowing) {
	}

	private SoulAnchorMarks() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SoulAnchorMarks::onServerTick);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.server.ServerStoppedEvent event) -> clear());
	}

	public static void clear() {
		MARKS.clear();
	}

	public static void mark(LivingEntity target, ServerPlayer owner) {
		if (target == null || owner == null || target.isRemoved() || !target.isAlive()) {
			return;
		}
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long expiresAt = now + DURATION_TICKS;
		Mark previous = MARKS.get(target.getUUID());
		boolean wasGlowing = previous != null ? previous.wasGlowing() : target.isCurrentlyGlowing();
		MARKS.put(target.getUUID(), new Mark(level, target.getUUID(), expiresAt, wasGlowing));
		target.setGlowingTag(true);
		broadcast(level, target, new SoulAnchorStatusPayload(owner.getId(), target.getId(),
				SoulAnchorStatusPayload.GLOW_TARGET, DURATION_TICKS, COLOR));
	}

	public static boolean isMarked(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		Mark mark = MARKS.get(entity.getUUID());
		return mark != null && mark.level() == entity.level()
				&& mark.expiresAt() > entity.level().getGameTime();
	}

	private static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
		if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || MARKS.isEmpty()) {
			return;
		}
		MinecraftServer server = event.getServer();
		if (server == null) {
			return;
		}
		long now = server.overworld() == null ? 0L : server.overworld().getGameTime();
		for (Mark mark : new ArrayList<>(MARKS.values())) {
			if (mark.expiresAt() > now) {
				continue;
			}
			MARKS.remove(mark.entityId());
			Entity entity = mark.level().getEntity(mark.entityId());
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (!mark.wasGlowing()) {
				living.setGlowingTag(false);
			}
			broadcast(mark.level(), living, new SoulAnchorStatusPayload(-1, living.getId(),
					SoulAnchorStatusPayload.CLEAR_GLOW_TARGET, 0, COLOR));
		}
	}

	private static void broadcast(ServerLevel level, Entity target, SoulAnchorStatusPayload payload) {
		Vec3 center = target.getBoundingBox().getCenter();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(center) <= 64.0D * 64.0D) {
				ModNetwork.send(viewer, payload);
			}
		}
	}
}
