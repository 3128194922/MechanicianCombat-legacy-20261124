package com.mechaniciancombat;

import com.mechaniciancombat.item.ArmorPartItem;
import com.mechaniciancombat.item.ArmorUpgradeData;
import com.mechaniciancombat.net.ArmorAbilityPayload;
import com.mechaniciancombat.net.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Mechanician 胸甲模块提供的运行时效果（C1 图腾、C2 护盾）。 */
public final class ArmorAbilities {
	public static final int SHIELD_COOLDOWN_TICKS = 40;
	public static final int SHIELD_DEPLETED_COOLDOWN_TICKS = 600;
	public static final int SHIELD_RECHARGE_WINDOW_TICKS = 200;
	public static final int SHIELD_RECHARGE_INTERVAL_TICKS = 100;
	public static final int SHIELD_RECHARGE_KILLS = 3;
	public static final int TOTEM_COOLDOWN_TICKS = 1200;

	private ArmorAbilities() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ArmorAbilities::onLivingHurt);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ArmorAbilities::onLivingDeath);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				(net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
					if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
							|| event.getServer() == null) {
						return;
					}
					boolean send = event.getServer().getTickCount() % 10 == 0;
					for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
						boolean changed = tickShieldRecovery(player);
						if (send || changed) {
							sendSnapshot(player);
						}
					}
				});
	}

	private static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
		if (event.getAmount() <= 0.0F || !(event.getEntity() instanceof ServerPlayer player)
				|| event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return;
		}
		long now = player.level().getGameTime();
		if (ArmorUpgradeData.shieldCooldownUntil(chest) > now
				|| ArmorUpgradeData.shieldCharges(chest) <= 0) {
			return;
		}
		int nextCharges = ArmorUpgradeData.shieldCharges(chest) - 1;
		ArmorUpgradeData.setShieldCharges(chest, nextCharges);
		if (nextCharges == 0) {
			ArmorUpgradeData.setShieldRechargeKills(chest, 0);
			ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
			ArmorUpgradeData.setShieldRechargeWindowUntil(chest,
					now + SHIELD_RECHARGE_WINDOW_TICKS);
			ArmorUpgradeData.setShieldCooldownUntil(chest,
					now + SHIELD_DEPLETED_COOLDOWN_TICKS);
		} else {
			ArmorUpgradeData.setShieldCooldownUntil(chest, now + SHIELD_COOLDOWN_TICKS);
			ArmorUpgradeData.setShieldRechargeAt(chest, now + SHIELD_RECHARGE_INTERVAL_TICKS);
		}
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
				player.getX() - Math.sin(Math.toRadians(player.getYRot())),
				player.getY() + 1.25D,
				player.getZ() + Math.cos(Math.toRadians(player.getYRot())),
				10, 0.25D, 0.25D, 0.25D, 0.05D);
		player.displayClientMessage(Component.translatable("message.tkgun.shield_blocked")
				.withStyle(ChatFormatting.AQUA), true);
		event.setCanceled(true);
		sendSnapshot(player);
	}

	private static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
		LivingEntity killed = event.getEntity();
		if (killed instanceof ServerPlayer player) {
			if (tryTotemSave(player)) {
				event.setCanceled(true);
				return;
			}
			return;
		}
		if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
			recoverShieldCharge(killed, attacker);
		}
	}

	/** C1 不死图腾：冷却结束后抵消一次死亡。 */
	private static boolean tryTotemSave(ServerPlayer player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C1)) {
			return false;
		}
		long now = player.level().getGameTime();
		if (ArmorUpgradeData.totemCooldownUntil(chest) > now) {
			return false;
		}
		ArmorUpgradeData.setTotemCooldownUntil(chest, now + TOTEM_COOLDOWN_TICKS);
		player.setHealth(1.0F);
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
		ServerLevel level = player.serverLevel();
		level.broadcastEntityEvent(player, (byte) 35);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.displayClientMessage(Component.translatable("message.tkgun.totem_saved")
				.withStyle(ChatFormatting.GOLD), true);
		sendSnapshot(player);
		return true;
	}

	private static void recoverShieldCharge(LivingEntity killed, ServerPlayer player) {
		if (killed instanceof Player) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return;
		}
		if (ArmorUpgradeData.shieldCharges(chest) != 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (ArmorUpgradeData.shieldCooldownUntil(chest) <= now
				|| ArmorUpgradeData.shieldRechargeWindowUntil(chest) < now) {
			return;
		}
		int kills = ArmorUpgradeData.shieldRechargeKills(chest) + 1;
		if (kills >= SHIELD_RECHARGE_KILLS) {
			ArmorUpgradeData.setShieldRechargeKills(chest, 0);
			ArmorUpgradeData.setShieldCooldownUntil(chest, 0L);
			ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
			ArmorUpgradeData.setShieldRechargeWindowUntil(chest, 0L);
			ArmorUpgradeData.setShieldCharges(chest, ArmorUpgradeData.MAX_SHIELD_CHARGES);
		} else {
			ArmorUpgradeData.setShieldRechargeKills(chest, kills);
		}
		sendSnapshot(player);
	}

	private static boolean tickShieldRecovery(ServerPlayer player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return false;
		}
		int charges = ArmorUpgradeData.shieldCharges(chest);
		long now = player.level().getGameTime();
		long cooldown = ArmorUpgradeData.shieldCooldownUntil(chest);
		boolean changed = false;
		if (cooldown > 0L && now >= cooldown) {
			ArmorUpgradeData.setShieldCooldownUntil(chest, 0L);
			changed = true;
		}
		if (charges == 0) {
			if (cooldown > 0L && now >= cooldown) {
				ArmorUpgradeData.setShieldRechargeKills(chest, 0);
				ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
				ArmorUpgradeData.setShieldRechargeWindowUntil(chest, 0L);
				ArmorUpgradeData.setShieldCharges(chest, ArmorUpgradeData.MAX_SHIELD_CHARGES);
				return true;
			}
			return changed;
		}
		if (charges >= ArmorUpgradeData.MAX_SHIELD_CHARGES) {
			if (ArmorUpgradeData.shieldRechargeAt(chest) > 0L) {
				ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
				changed = true;
			}
			return changed;
		}
		long rechargeAt = ArmorUpgradeData.shieldRechargeAt(chest);
		if (rechargeAt <= 0L) {
			ArmorUpgradeData.setShieldRechargeAt(chest, now + SHIELD_RECHARGE_INTERVAL_TICKS);
			return true;
		}
		if (now >= rechargeAt) {
			int nextCharges = charges;
			if (player.getHealth() == player.getMaxHealth()) {
				nextCharges++;
				ArmorUpgradeData.setShieldCharges(chest, nextCharges);
			}
			ArmorUpgradeData.setShieldRechargeAt(chest,
					nextCharges < ArmorUpgradeData.MAX_SHIELD_CHARGES
							? now + SHIELD_RECHARGE_INTERVAL_TICKS : 0L);
			changed = true;
		}
		return changed;
	}

	public static void sendSnapshot(ServerPlayer player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		long now = player.level().getGameTime();
		ModNetwork.send(player, new ArmorAbilityPayload(
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2),
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C1),
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3),
				ArmorUpgradeData.shieldCharges(chest),
				ArmorUpgradeData.shieldRechargeKills(chest),
				(int) Math.max(0, ArmorUpgradeData.shieldRechargeWindowUntil(chest) - now),
				(int) Math.max(0, ArmorUpgradeData.shieldCooldownUntil(chest) - now),
				(int) Math.max(0, ArmorUpgradeData.totemCooldownUntil(chest) - now)));
	}

	public static void clearCooldowns(ServerPlayer player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (chest.isEmpty()) {
			return;
		}
		ArmorUpgradeData.setShieldCooldownUntil(chest, 0L);
		ArmorUpgradeData.setTotemCooldownUntil(chest, 0L);
		ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
		ArmorUpgradeData.setShieldRechargeKills(chest, 0);
		ArmorUpgradeData.setShieldRechargeWindowUntil(chest, 0L);
		if (ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			ArmorUpgradeData.setShieldCharges(chest, ArmorUpgradeData.MAX_SHIELD_CHARGES);
		}
		sendSnapshot(player);
	}
}
