package com.mechaniciancombat.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** 保存在 Mechanician 护甲上的模块、充能与冷却状态。 */
public final class ArmorUpgradeData {
	public static final int MAX_SHIELD_CHARGES = 3;
	public static final int HELMET_EXPAND_TICKS = 15;
	public static final int HELMET_FOLD_TICKS = 15;
	private static final String SHIELD_COOLDOWN_UNTIL = "tkgun_shield_cooldown_until";
	private static final String TOTEM_COOLDOWN_UNTIL = "tkgun_totem_cooldown_until";
	private static final String SHIELD_CHARGES = "tkgun_shield_charges";
	private static final String SHIELD_RECHARGE_KILLS = "tkgun_shield_recharge_kills";
	private static final String SHIELD_RECHARGE_AT = "tkgun_shield_recharge_at";
	private static final String SHIELD_RECHARGE_WINDOW_UNTIL = "tkgun_shield_recharge_window_until";
	private static final String HELMET_EXPANDED = "tkgun_helmet_expanded";
	private static final String HELMET_TRANSITION_STARTED = "tkgun_helmet_transition_started";
	private static final String HELMET_TRANSITION_DURATION = "tkgun_helmet_transition_duration";
	private static final String PART_STACKS = "tkgun_armor_part_stacks";

	private ArmorUpgradeData() {
	}

	public static ItemStack equippedChestplate(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		return isChestplate(chest) ? chest : ItemStack.EMPTY;
	}

	public static boolean isChestplate(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.getItem() instanceof MechanicianArmorItem armor
				&& armor.piece() == MechanicianArmorPiece.CHESTPLATE;
	}

	public static String partId(ItemStack stack, ArmorPartItem.Kind kind) {
		if (!isPartContainer(stack, kind)) {
			return "";
		}
		return StackNbt.copy(stack).getString(partKey(kind));
	}

	public static boolean hasPart(ItemStack stack, ArmorPartItem.Kind kind) {
		return !partId(stack, kind).isEmpty();
	}

	public static void setPartId(ItemStack stack, ArmorPartItem.Kind kind, String itemId) {
		if (!isPartContainer(stack, kind)) {
			return;
		}
		StackNbt.update(stack, tag -> {
			if (itemId == null || itemId.isEmpty()) {
				tag.remove(partKey(kind));
			} else {
				tag.putString(partKey(kind), itemId);
			}
		});
	}

	/** C 部件在胸甲、T 部件在护腿、F 部件在靴子。 */
	public static ItemStack equippedPartContainer(Player player, ArmorPartItem.Kind kind) {
		ItemStack stack = player.getItemBySlot(kind.equipmentSlot());
		return isPartContainer(stack, kind) ? stack : ItemStack.EMPTY;
	}

	public static boolean isPartContainer(ItemStack stack, ArmorPartItem.Kind kind) {
		if (stack == null || stack.isEmpty()
				|| !(stack.getItem() instanceof MechanicianArmorItem armor)) {
			return false;
		}
		return switch (kind.group()) {
			case CHEST -> armor.piece() == MechanicianArmorPiece.CHESTPLATE;
			case LEGS -> armor.piece() == MechanicianArmorPiece.LEGGINGS;
			case FEET -> armor.piece() == MechanicianArmorPiece.BOOTS;
		};
	}

	/** 同时保存完整模块栈，拆下/重新装备护甲后仍然保留。 */
	public static void setStoredPart(ItemStack container, ArmorPartItem.Kind kind, ItemStack stack) {
		if (!isPartContainer(container, kind)) {
			return;
		}
		boolean valid = !stack.isEmpty() && stack.getItem() instanceof ArmorPartItem part
				&& part.kind() == kind;
		StackNbt.update(container, tag -> {
			if (valid) {
				tag.putString(partKey(kind), net.minecraftforge.registries.ForgeRegistries.ITEMS
						.getKey(stack.getItem()).toString());
			} else {
				tag.remove(partKey(kind));
			}
			CompoundTag parts = tag.getCompound(PART_STACKS);
			if (valid) {
				parts.put(kind.name(), stack.copyWithCount(1).save(new CompoundTag()));
			} else {
				parts.remove(kind.name());
			}
			if (parts.isEmpty()) {
				tag.remove(PART_STACKS);
			} else {
				tag.put(PART_STACKS, parts);
			}
		});
	}

	public static ItemStack storedPart(ItemStack container, ArmorPartItem.Kind kind) {
		if (!isPartContainer(container, kind)) {
			return ItemStack.EMPTY;
		}
		CompoundTag parts = StackNbt.copy(container).getCompound(PART_STACKS);
		if (!parts.contains(kind.name(), CompoundTag.TAG_COMPOUND)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = ItemStack.of(parts.getCompound(kind.name()));
		return stack.getItem() instanceof ArmorPartItem part && part.kind() == kind
				? stack : ItemStack.EMPTY;
	}

	public static long shieldCooldownUntil(ItemStack chest) {
		return longValue(chest, SHIELD_COOLDOWN_UNTIL);
	}

	public static long totemCooldownUntil(ItemStack chest) {
		return longValue(chest, TOTEM_COOLDOWN_UNTIL);
	}

	public static long shieldRechargeAt(ItemStack chest) {
		return longValue(chest, SHIELD_RECHARGE_AT);
	}

	public static int shieldCharges(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		CompoundTag data = StackNbt.copy(chest);
		return data.contains(SHIELD_CHARGES)
				? Math.max(0, Math.min(MAX_SHIELD_CHARGES, data.getInt(SHIELD_CHARGES)))
				: MAX_SHIELD_CHARGES;
	}

	public static boolean helmetExpanded(ItemStack chest) {
		return isChestplate(chest) && StackNbt.copy(chest).getBoolean(HELMET_EXPANDED);
	}

	public static long helmetTransitionStarted(ItemStack chest) {
		return longValue(chest, HELMET_TRANSITION_STARTED);
	}

	public static int helmetTransitionDuration(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		return Math.max(0, StackNbt.copy(chest).getInt(HELMET_TRANSITION_DURATION));
	}

	public static void beginHelmetTransition(ItemStack chest, boolean expanded, long startedAt) {
		if (!isChestplate(chest)) {
			return;
		}
		int duration = expanded ? HELMET_EXPAND_TICKS : HELMET_FOLD_TICKS;
		StackNbt.update(chest, tag -> {
			tag.putBoolean(HELMET_EXPANDED, expanded);
			tag.putLong(HELMET_TRANSITION_STARTED, startedAt);
			tag.putInt(HELMET_TRANSITION_DURATION, duration);
		});
	}

	public static int shieldRechargeKills(ItemStack chest) {
		if (!isChestplate(chest)) {
			return 0;
		}
		CompoundTag data = StackNbt.copy(chest);
		return data.contains(SHIELD_RECHARGE_KILLS)
				? Math.max(0, Math.min(3, data.getInt(SHIELD_RECHARGE_KILLS))) : 0;
	}

	public static long shieldRechargeWindowUntil(ItemStack chest) {
		return longValue(chest, SHIELD_RECHARGE_WINDOW_UNTIL);
	}

	public static void setShieldCooldownUntil(ItemStack chest, long until) {
		setLong(chest, SHIELD_COOLDOWN_UNTIL, until);
	}

	public static void setTotemCooldownUntil(ItemStack chest, long until) {
		setLong(chest, TOTEM_COOLDOWN_UNTIL, until);
	}

	public static void setShieldRechargeAt(ItemStack chest, long until) {
		setLong(chest, SHIELD_RECHARGE_AT, until);
	}

	public static void setShieldCharges(ItemStack chest, int charges) {
		if (!isChestplate(chest)) {
			return;
		}
		int clamped = Math.max(0, Math.min(MAX_SHIELD_CHARGES, charges));
		StackNbt.update(chest, tag -> tag.putInt(SHIELD_CHARGES, clamped));
	}

	public static void setShieldRechargeKills(ItemStack chest, int kills) {
		if (!isChestplate(chest)) {
			return;
		}
		int clamped = Math.max(0, Math.min(3, kills));
		StackNbt.update(chest, tag -> {
			if (clamped == 0) {
				tag.remove(SHIELD_RECHARGE_KILLS);
			} else {
				tag.putInt(SHIELD_RECHARGE_KILLS, clamped);
			}
		});
	}

	public static void setShieldRechargeWindowUntil(ItemStack chest, long until) {
		setLong(chest, SHIELD_RECHARGE_WINDOW_UNTIL, until);
	}

	private static String partKey(ArmorPartItem.Kind kind) {
		return "tkgun_armor_part_" + kind.name().toLowerCase(Locale.ROOT);
	}

	private static long longValue(ItemStack stack, String key) {
		if (!isChestplate(stack)) {
			return 0L;
		}
		CompoundTag data = StackNbt.copy(stack);
		return data.contains(key) ? data.getLong(key) : 0L;
	}

	private static void setLong(ItemStack stack, String key, long value) {
		if (!isChestplate(stack)) {
			return;
		}
		StackNbt.update(stack, tag -> {
			if (value > 0L) {
				tag.putLong(key, value);
			} else {
				tag.remove(key);
			}
		});
	}
}
