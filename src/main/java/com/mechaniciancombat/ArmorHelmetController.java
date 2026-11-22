package com.mechaniciancombat;

import com.mechaniciancombat.item.ArmorUpgradeData;
import com.mechaniciancombat.item.MechanicianUndersuitItem;
import com.mechaniciancombat.net.HelmetStatePayload;
import com.mechaniciancombat.net.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 全套底衣头盔的展开/折叠状态（服务端权威）。 */
public final class ArmorHelmetController {
	private static final Map<UUID, HelmetSound> HELMET_SOUNDS = new HashMap<>();
	private static final int[] FOLD_SOUND_TICKS = {0, 8, 9};
	private static final net.minecraft.resources.ResourceLocation[] FOLD_SOUNDS = {
			TkGun.SND_HELMET_STEAM_HISS, TkGun.SND_HELMET_MECH5, TkGun.SND_HELMET_MECH3
	};
	private static final int[] EXPAND_SOUND_TICKS = {2, 3, 7};
	private static final net.minecraft.resources.ResourceLocation[] EXPAND_SOUNDS = {
			TkGun.SND_HELMET_MECH5, TkGun.SND_HELMET_MECH2, TkGun.SND_HELMET_MECH3
	};

	private static final class HelmetSound {
		private final long startedAt;
		private final int durationTicks;
		private final boolean expanded;
		private int mask;

		private HelmetSound(long startedAt, int durationTicks, boolean expanded) {
			this.startedAt = startedAt;
			this.durationTicks = durationTicks;
			this.expanded = expanded;
		}
	}

	private ArmorHelmetController() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
					if (event.getEntity() instanceof ServerPlayer player
							&& player.getServer() != null) {
						syncAll(player, player.getServer());
					}
				});
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
					if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
							|| event.getServer() == null) {
						return;
					}
					tickHelmetSounds(event.getServer());
					if (event.getServer().getTickCount() % 20 != 0) {
						return;
					}
					for (ServerPlayer viewer : event.getServer().getPlayerList().getPlayers()) {
						syncAll(viewer, event.getServer());
					}
				});
	}

	public static void toggle(ServerPlayer player) {
		if (player == null || !MechanicianUndersuitItem.isWearingFullSet(player)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		long now = player.level().getGameTime();
		long startedAt = ArmorUpgradeData.helmetTransitionStarted(chest);
		int duration = ArmorUpgradeData.helmetTransitionDuration(chest);
		if (startedAt > 0L && duration > 0 && now < startedAt + duration) {
			TkGun.LOGGER.info("[tkgun] helmet toggle ignored while animation is active (remaining {} ticks)",
					startedAt + duration - now);
			return;
		}
		ArmorUpgradeData.beginHelmetTransition(chest, !ArmorUpgradeData.helmetExpanded(chest), now);
		boolean expanded = ArmorUpgradeData.helmetExpanded(chest);
		HELMET_SOUNDS.put(player.getUUID(), new HelmetSound(now,
				expanded ? ArmorUpgradeData.HELMET_EXPAND_TICKS
						: ArmorUpgradeData.HELMET_FOLD_TICKS, expanded));
		for (ServerPlayer viewer : player.serverLevel().players()) {
			send(viewer, player, chest);
		}
	}

	private static void tickHelmetSounds(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		Iterator<Map.Entry<UUID, HelmetSound>> iterator = HELMET_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, HelmetSound> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			HelmetSound sound = entry.getValue();
			if (player == null) {
				iterator.remove();
				continue;
			}
			long elapsed = now - sound.startedAt;
			if (elapsed > sound.durationTicks + 2L) {
				iterator.remove();
				continue;
			}
			int[] ticks = sound.expanded ? EXPAND_SOUND_TICKS : FOLD_SOUND_TICKS;
			net.minecraft.resources.ResourceLocation[] sounds =
					sound.expanded ? EXPAND_SOUNDS : FOLD_SOUNDS;
			for (int index = 0; index < ticks.length; index++) {
				int bit = 1 << index;
				if (elapsed >= ticks[index] && (sound.mask & bit) == 0) {
					sound.mask |= bit;
					playHelmetSound(player, sounds[index]);
				}
			}
		}
	}

	private static void playHelmetSound(ServerPlayer player,
										net.minecraft.resources.ResourceLocation soundId) {
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1.6D,
				player.getZ(), SoundEvent.createVariableRangeEvent(soundId),
				SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static void syncAll(ServerPlayer viewer, MinecraftServer server) {
		for (ServerPlayer target : server.getPlayerList().getPlayers()) {
			if (viewer.serverLevel() != target.serverLevel()) {
				continue;
			}
			send(viewer, target, ArmorUpgradeData.equippedChestplate(target));
		}
	}

	private static void send(ServerPlayer viewer, ServerPlayer target, ItemStack chest) {
		ModNetwork.send(viewer, new HelmetStatePayload(target.getUUID(),
				ArmorUpgradeData.helmetExpanded(chest),
				ArmorUpgradeData.helmetTransitionStarted(chest),
				ArmorUpgradeData.helmetTransitionDuration(chest)));
	}
}
