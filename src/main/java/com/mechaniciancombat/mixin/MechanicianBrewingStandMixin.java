package com.mechaniciancombat.mixin;

import com.mechaniciancombat.item.MechanicianBrewingRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让酿造台支持“物品 + 试剂 → 物品”的自定义配方。 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class MechanicianBrewingStandMixin {
	private static final int CUSTOM_BREW_TICKS = 400;

	@Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
	private static void tkgun$tickCustomBrewing(Level level, BlockPos pos, BlockState state,
												BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
		if (level.isClientSide) {
			return;
		}
		BrewingStandBlockEntityAccessor access = (BrewingStandBlockEntityAccessor) (Object) blockEntity;
		NonNullList<ItemStack> inventory = access.tkgun$items();
		ItemStack template = MechanicianBrewingRecipes.find(inventory.get(3), inventory.get(0));
		if (template.isEmpty()) {
			return;
		}
		ItemStack output = inventory.get(2);
		if (!output.isEmpty() && (!ItemStack.isSameItemSameTags(output, template)
				|| output.getCount() + template.getCount() > output.getMaxStackSize())) {
			access.tkgun$setBrewTime(0);
			ci.cancel();
			return;
		}
		ItemStack fuelStack = inventory.get(4);
		if (access.tkgun$fuel() <= 0 && fuelStack.is(Items.BLAZE_POWDER)) {
			access.tkgun$setFuel(20);
			fuelStack.shrink(1);
		}
		if (access.tkgun$fuel() <= 0) {
			updateBottleState(level, pos, state, inventory);
			blockEntity.setChanged();
			ci.cancel();
			return;
		}
		if (access.tkgun$brewTime() <= 0) {
			access.tkgun$setBrewTime(CUSTOM_BREW_TICKS);
			access.tkgun$setFuel(access.tkgun$fuel() - 1);
		} else {
			access.tkgun$setBrewTime(access.tkgun$brewTime() - 1);
		}
		if (access.tkgun$brewTime() <= 0) {
			craft(level, pos, inventory, template);
		}
		updateBottleState(level, pos, state, inventory);
		blockEntity.setChanged();
		level.updateNeighbourForOutputSignal(pos, state.getBlock());
		ci.cancel();
	}

	@Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
	private void tkgun$allowCustomInputs(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		BrewingStandBlockEntityAccessor access = (BrewingStandBlockEntityAccessor) (Object) this;
		NonNullList<ItemStack> inventory = access.tkgun$items();
		if (slot == 3 && MechanicianBrewingRecipes.acceptsInput(stack)) {
			cir.setReturnValue(true);
		} else if (slot == 0 && MechanicianBrewingRecipes.acceptsReagent(inventory.get(3), stack)) {
			cir.setReturnValue(true);
		}
	}

	private static void craft(Level level, BlockPos pos, NonNullList<ItemStack> inventory,
							  ItemStack template) {
		inventory.get(3).shrink(1);
		inventory.get(0).shrink(1);
		ItemStack output = inventory.get(2);
		if (output.isEmpty()) {
			inventory.set(2, template.copy());
		} else {
			output.grow(template.getCount());
		}
		level.levelEvent(1035, pos, 0);
	}

	private static void updateBottleState(Level level, BlockPos pos, BlockState state,
										  NonNullList<ItemStack> inventory) {
		BlockState updated = state;
		for (int index = 0; index < 3; index++) {
			updated = updated.setValue(BrewingStandBlock.HAS_BOTTLE[index],
					!inventory.get(index).isEmpty());
		}
		if (updated != state) {
			level.setBlock(pos, updated, 2);
		}
	}
}
