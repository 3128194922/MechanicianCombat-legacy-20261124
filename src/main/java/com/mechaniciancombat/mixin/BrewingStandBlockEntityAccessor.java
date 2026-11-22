package com.mechaniciancombat.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取/写入酿造台的私有字段，供自定义物品酿造使用。 */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
	@Accessor("items")
	NonNullList<ItemStack> tkgun$items();

	@Accessor("brewTime")
	int tkgun$brewTime();

	@Accessor("brewTime")
	void tkgun$setBrewTime(int value);

	@Accessor("fuel")
	int tkgun$fuel();

	@Accessor("fuel")
	void tkgun$setFuel(int value);
}
