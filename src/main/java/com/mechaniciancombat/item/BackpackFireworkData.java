package com.mechaniciancombat.item;

import net.minecraft.world.item.ItemStack;

/** Marks only the firework rockets launched by the combat backpack. */
public final class BackpackFireworkData {
	private static final String NO_SELF_DAMAGE = "tkgun_backpack_no_self_damage";
	private static final String MISSILE_VISUAL = "tkgun_missile_box_visual";

	public static void markNoSelfDamage(ItemStack rocket) {
		StackNbt.update(rocket, nbt -> nbt.putBoolean(NO_SELF_DAMAGE, true));
	}

	public static boolean preventsSelfDamage(ItemStack rocket) {
		return StackNbt.copy(rocket).getBoolean(NO_SELF_DAMAGE);
	}

	/** 标记"这发是导弹舱冷发射的火箭"，渲染层据此换成潜影贝弹外观。 */
	public static void markMissileVisual(ItemStack rocket) {
		StackNbt.update(rocket, nbt -> nbt.putBoolean(MISSILE_VISUAL, true));
	}

	public static boolean usesMissileVisual(ItemStack rocket) {
		return StackNbt.copy(rocket).getBoolean(MISSILE_VISUAL);
	}

	private BackpackFireworkData() {
	}
}
