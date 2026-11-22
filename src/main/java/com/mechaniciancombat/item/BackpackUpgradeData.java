package com.mechaniciancombat.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/** Persistent installed-component flags stored on a Combat Backpack item. */
public final class BackpackUpgradeData {
	public enum LauncherType {
		NONE,
		PORTABLE,
		AUTO,
		SUMMON_ANTENNA,
		/** r774 战旗：背包动作在落点召唤增益旗。 */
		BATTLE_BANNER,
		/** r774 跳跃推进器：跳跃键起跳并按住推进。 */
		JUMP_THRUSTER
	}

	private static final String CORE = "tkgun_backpack_core";
	private static final String LAUNCHER = "tkgun_backpack_launcher";
	private static final String LAUNCHER_TYPE = "tkgun_backpack_launcher_type";
	private static final String CORE_SLOT = "tkgun_backpack_core_slot";
	private static final String LAUNCHER_SLOT = "tkgun_backpack_launcher_slot";
	private static final String MISSILE_BOX = "tkgun_backpack_missile_box";
	private static final String MISSILE_BOX_SLOT = "tkgun_backpack_missile_box_slot";
	private static final String GOLEM_CAPSULE = "tkgun_backpack_golem_capsule";
	private static final String GOLEM_CAPSULE_SLOT = "tkgun_backpack_golem_capsule_slot";

	public static boolean hasCore(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(CORE);
	}

	public static boolean hasLauncher(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(LAUNCHER);
	}

	public static boolean canUseLauncher(ItemStack backpack) {
		return hasCore(backpack) && hasLauncher(backpack);
	}

	public static LauncherType launcherType(ItemStack backpack) {
		if (!hasLauncher(backpack)) {
			return LauncherType.NONE;
		}
		int type = customData(backpack).getInt(LAUNCHER_TYPE);
		return switch (type) {
			case 2 -> LauncherType.AUTO;
			case 3 -> LauncherType.SUMMON_ANTENNA;
			case 4 -> LauncherType.JUMP_THRUSTER;
			case 5 -> LauncherType.BATTLE_BANNER;
			default -> LauncherType.PORTABLE;
		};
	}

	/** 背包里装的是战旗部件。 */
	public static boolean canUseWarBanner(ItemStack backpack) {
		return canUseLauncher(backpack) && launcherType(backpack) == LauncherType.BATTLE_BANNER;
	}

	public static boolean canUseJumpThruster(ItemStack backpack) {
		return canUseLauncher(backpack) && launcherType(backpack) == LauncherType.JUMP_THRUSTER;
	}

	/** 导弹舱：需要先装跳跃推进器，再装导弹舱部件。 */
	public static boolean hasMissileBox(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(MISSILE_BOX);
	}

	public static boolean canUseMissileBox(ItemStack backpack) {
		return canUseJumpThruster(backpack) && hasMissileBox(backpack);
	}

	public static boolean hasGolemCapsule(ItemStack backpack) {
		return isBackpack(backpack) && customData(backpack).getBoolean(GOLEM_CAPSULE);
	}

	/** 召唤天线 + 魔像空投舱：允许用背包召唤熔岩傀儡。 */
	public static boolean canSummonGolems(ItemStack backpack) {
		return canUseLauncher(backpack)
				&& launcherType(backpack) == LauncherType.SUMMON_ANTENNA
				&& hasGolemCapsule(backpack);
	}

	public static int coreSlot(ItemStack backpack) {
		return slotValue(backpack, CORE_SLOT);
	}

	public static int launcherSlot(ItemStack backpack) {
		return slotValue(backpack, LAUNCHER_SLOT);
	}

	public static int missileBoxSlot(ItemStack backpack) {
		return slotValue(backpack, MISSILE_BOX_SLOT);
	}

	public static int golemCapsuleSlot(ItemStack backpack) {
		return slotValue(backpack, GOLEM_CAPSULE_SLOT);
	}

	public static void installCore(ItemStack backpack) {
		setCore(backpack, true);
	}

	public static void installLauncher(ItemStack backpack) {
		if (isBackpack(backpack) && hasCore(backpack)) {
			setLauncher(backpack, true);
		}
	}

	public static void setCore(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(CORE, true);
			} else {
				nbt.remove(CORE);
			}
		});
	}

	public static void setLauncher(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(LAUNCHER, true);
			} else {
				nbt.remove(LAUNCHER);
				nbt.remove(LAUNCHER_TYPE);
				nbt.remove(MISSILE_BOX);
				nbt.remove(MISSILE_BOX_SLOT);
				nbt.remove(GOLEM_CAPSULE);
				nbt.remove(GOLEM_CAPSULE_SLOT);
			}
		});
	}

	public static void setLauncherType(ItemStack backpack, LauncherType type) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (type == LauncherType.NONE) {
				nbt.remove(LAUNCHER);
				nbt.remove(LAUNCHER_TYPE);
				nbt.remove(MISSILE_BOX);
				nbt.remove(MISSILE_BOX_SLOT);
				nbt.remove(GOLEM_CAPSULE);
				nbt.remove(GOLEM_CAPSULE_SLOT);
			} else {
				nbt.putBoolean(LAUNCHER, true);
				int encoded = switch (type) {
					case AUTO -> 2;
					case SUMMON_ANTENNA -> 3;
					case JUMP_THRUSTER -> 4;
					case BATTLE_BANNER -> 5;
					default -> 1;
				};
				nbt.putInt(LAUNCHER_TYPE, encoded);
				if (type != LauncherType.JUMP_THRUSTER) {
					nbt.remove(MISSILE_BOX);
					nbt.remove(MISSILE_BOX_SLOT);
				}
				if (type != LauncherType.SUMMON_ANTENNA) {
					nbt.remove(GOLEM_CAPSULE);
					nbt.remove(GOLEM_CAPSULE_SLOT);
				}
			}
		});
	}

	public static void setCoreSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, CORE_SLOT, slot);
	}

	public static void setLauncherSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, LAUNCHER_SLOT, slot);
	}

	public static void setMissileBox(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(MISSILE_BOX, true);
			} else {
				nbt.remove(MISSILE_BOX);
			}
		});
	}

	public static void setMissileBoxSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, MISSILE_BOX_SLOT, slot);
	}

	public static void setGolemCapsule(ItemStack backpack, boolean installed) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (installed) {
				nbt.putBoolean(GOLEM_CAPSULE, true);
			} else {
				nbt.remove(GOLEM_CAPSULE);
				nbt.remove(GOLEM_CAPSULE_SLOT);
			}
		});
	}

	public static void setGolemCapsuleSlot(ItemStack backpack, int slot) {
		setSlotValue(backpack, GOLEM_CAPSULE_SLOT, slot);
	}

	private static int slotValue(ItemStack backpack, String key) {
		if (!isBackpack(backpack)) {
			return -1;
		}
		CompoundTag data = customData(backpack);
		return data.contains(key) ? data.getInt(key) : -1;
	}

	private static void setSlotValue(ItemStack backpack, String key, int slot) {
		if (!isBackpack(backpack)) {
			return;
		}
		StackNbt.update(backpack, nbt -> {
			if (slot >= 0 && slot < 4) {
				nbt.putInt(key, slot);
			} else {
				nbt.remove(key);
			}
		});
	}

	private static CompoundTag customData(ItemStack stack) {
		return StackNbt.copy(stack);
	}

	private static boolean isBackpack(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.getItem() instanceof CombatBackpackItem;
	}

	private BackpackUpgradeData() {
	}
}
