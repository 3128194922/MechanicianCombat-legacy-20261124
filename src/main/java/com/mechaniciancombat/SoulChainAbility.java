package com.mechaniciancombat;

import com.mechaniciancombat.item.ArmorPartItem;
import com.mechaniciancombat.item.ArmorUpgradeData;
import com.mechaniciancombat.net.ModNetwork;
import com.mechaniciancombat.net.SoulChainPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * C3 灵魂锁链：命中后把附近目标连成一条持续 5 秒的锁链。
 *
 * <p>锁链存在期间，任何一名成员受到的伤害都会按距离衰减传递给其他成员；
 * 客户端按实体实时位置绘制链条，因此成员移动时链条会一直跟随。
 */
public final class SoulChainAbility {
	private static final double LINK_DIAMETER = 10.0D;
	private static final double LINK_RADIUS = LINK_DIAMETER * 0.5D;
	private static final double LINK_CHANCE = 0.40D;
	private static final float SECONDARY_DAMAGE = 0.70F;
	private static final float TERTIARY_DAMAGE = 0.50F;
	private static final float MINIMUM_DAMAGE_FACTOR = 0.50F;
	private static final int NORMAL_MAX_TARGETS = 3;
	private static final int MARKED_MAX_TARGETS = 6;
	/** 链接持续时间：5 秒 = 100 tick。 */
	public static final int LINK_TICKS = 100;
	private static boolean resolving;
	private static long lastCleanupTick = -1L;
	private static final List<Link> LINKS = new ArrayList<>();

	private record Link(List<UUID> members, long expiresAt, boolean marked) {
	}

	private SoulChainAbility() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				SoulChainAbility::onLivingDamage);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				SoulChainAbility::onServerTick);
	}

	private static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
		if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
				|| event.getServer() == null) {
			return;
		}
		long now = event.getServer().overworld().getGameTime();
		if (now == lastCleanupTick) {
			return;
		}
		lastCleanupTick = now;
		LINKS.removeIf(link -> link.expiresAt() <= now);
	}

	private static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource source = event.getSource();
		float damageTaken = event.getAmount();
		if (resolving || damageTaken <= 0.0F
				|| !(target.level() instanceof ServerLevel level)) {
			return;
		}
		// 1) 已有锁链：把这次伤害传给链上的其他成员。
		boolean linked = propagate(level, target, source, damageTaken);
		if (linked || containsMember(target.getUUID())) {
			return;
		}
		// 2) 只有 C3 玩家造成的新命中才会开始一条新锁链。
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		boolean marked = SoulAnchorMarks.isMarked(target);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3)
				|| !marked && player.getRandom().nextDouble() >= LINK_CHANCE) {
			return;
		}
		double radius = marked ? LINK_RADIUS * 2.0D : LINK_RADIUS;
		int maxTargets = marked ? MARKED_MAX_TARGETS : NORMAL_MAX_TARGETS;
		List<LivingEntity> targets = new ArrayList<>(maxTargets);
		List<UUID> excluded = new ArrayList<>(maxTargets + 1);
		targets.add(target);
		excluded.add(target.getUUID());
		excluded.add(player.getUUID());
		LivingEntity current = target;
		while (targets.size() < maxTargets) {
			LivingEntity next = nearest(level, current, player, excluded, radius);
			if (next == null) {
				break;
			}
			targets.add(next);
			excluded.add(next.getUUID());
			current = next;
		}
		if (targets.size() < 2) {
			return;
		}
		// 起始命中立即按 70%/50% 传递一次；之后 5 秒内的任何伤害继续沿锁链传递。
		resolving = true;
		try {
			for (int index = 1; index < targets.size(); index++) {
				float factor = marked
						? Math.max(MINIMUM_DAMAGE_FACTOR, 0.70F - 0.10F * (index - 1))
						: index == 1 ? SECONDARY_DAMAGE : TERTIARY_DAMAGE;
				targets.get(index).hurt(player.damageSources().playerAttack(player),
						damageTaken * factor);
			}
		} finally {
			resolving = false;
		}
		long now = level.getGameTime();
		List<UUID> members = targets.stream().map(Entity::getUUID).toList();
		LINKS.add(new Link(members, now + LINK_TICKS, marked));
		broadcast(level, target, targets);
	}

	/** 把命中伤害沿所有包含该实体的锁链传递一次；返回是否命中过锁链。 */
	private static boolean propagate(ServerLevel level, LivingEntity hit,
									 DamageSource source, float damage) {
		List<Link> links = new ArrayList<>();
		for (Link link : LINKS) {
			if (link.members().contains(hit.getUUID())) {
				links.add(link);
			}
		}
		if (links.isEmpty()) {
			return false;
		}
		resolving = true;
		try {
			Set<UUID> alreadyHit = new HashSet<>();
			alreadyHit.add(hit.getUUID());
			for (Link link : links) {
				int hitIndex = link.members().indexOf(hit.getUUID());
				for (int index = 0; index < link.members().size(); index++) {
					UUID memberId = link.members().get(index);
					if (alreadyHit.contains(memberId)) {
						continue;
					}
					LivingEntity member = findLiving(level, memberId);
					if (member == null || !member.isAlive()) {
						continue;
					}
					int distance = Math.abs(index - hitIndex);
					float factor = link.marked()
							? Math.max(MINIMUM_DAMAGE_FACTOR, 0.70F - 0.10F * (distance - 1))
							: distance == 1 ? SECONDARY_DAMAGE : TERTIARY_DAMAGE;
					member.hurt(source, damage * factor);
					alreadyHit.add(memberId);
				}
			}
		} finally {
			resolving = false;
		}
		return true;
	}

	private static boolean containsMember(UUID uuid) {
		for (Link link : LINKS) {
			if (link.members().contains(uuid)) {
				return true;
			}
		}
		return false;
	}

	private static LivingEntity findLiving(ServerLevel level, UUID uuid) {
		net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(uuid);
		if (player != null) {
			return player;
		}
		Entity entity = level.getEntities().get(uuid);
		return entity instanceof LivingEntity living ? living : null;
	}

	private static LivingEntity nearest(ServerLevel level, LivingEntity center,
										ServerPlayer owner, List<UUID> excluded,
										double radius) {
		Vec3 position = center.position();
		AABB bounds = new AABB(
				position.x - radius, position.y - radius, position.z - radius,
				position.x + radius, position.y + radius, position.z + radius);
		return level.getEntitiesOfClass(LivingEntity.class, bounds, entity ->
						entity != owner && entity.isAlive() && entity.isPickable()
								&& entity.distanceToSqr(position) <= radius * radius
								&& !excluded.contains(entity.getUUID())
								&& CombatTargeting.isTargetable(entity))
				.stream()
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(position)))
				.orElse(null);
	}

	private static void broadcast(ServerLevel level, LivingEntity anchor,
								  List<LivingEntity> members) {
		List<SoulChainPayload.ChainNode> points = new ArrayList<>(members.size());
		for (LivingEntity linked : members) {
			Vec3 point = effectPoint(linked);
			points.add(new SoulChainPayload.ChainNode(linked.getId(),
					point.x, point.y, point.z));
		}
		SoulChainPayload payload = new SoulChainPayload(List.copyOf(points), LINK_TICKS);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(anchor.position()) <= 64.0D * 64.0D) {
				ModNetwork.send(viewer, payload);
			}
		}
	}

	private static Vec3 effectPoint(Entity entity) {
		return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.55D,
				entity.getZ());
	}
}
