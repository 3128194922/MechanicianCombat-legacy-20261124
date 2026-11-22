package com.mechaniciancombat.screen;

import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.block.BackpackModificationTableBlockEntity;
import com.mechaniciancombat.item.BackpackPartItem;
import com.mechaniciancombat.item.BackpackTableTransition;
import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.item.CombatBackpackItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

/**
 * Persistent backpack component installer.
 *
 * <p>The backpack remains in the right-hand slot. The upper-left component slot
 * accepts only a core, the upper-right accepts only a portable launcher, and the
 * lower two slots remain reserved. Closing the screen never returns the backpack
 * to the player.</p>
 */
public final class BackpackModificationTableScreenHandler extends AbstractContainerMenu {
	public static final int BACKPACK_SLOT_X = 152;
	public static final int BACKPACK_SLOT_Y = 66;
	public static final int BACKPACK_SLOT_INDEX =
			BackpackModificationTableBlockEntity.BACKPACK_SLOT;
	public static final int FIRST_PART_SLOT_INDEX = 1;
	public static final int PLAYER_SLOT_START = 5;
	public static final int HOTBAR_SLOT_START = 32;
	public static final int CORE_PART_SLOT = 0;
	public static final int LAUNCHER_PART_SLOT = 1;
	public static final int MISSILE_PART_SLOT = 3;
	public static final int GOLEM_CAPSULE_PART_SLOT = 3;

	private static final int[][] PART_SLOT_POSITIONS = {
			{8, 30},
			{26, 30},
			{8, 48},
			{26, 48}
	};

	public static MenuType<BackpackModificationTableScreenHandler> TYPE;

	private final BlockPos pos;
	private final Container tableInventory;
	private final Player owner;
	private ItemStack lastBackpack = ItemStack.EMPTY;
	private boolean installing;

	public BackpackModificationTableScreenHandler(
			int containerId,
			Inventory playerInventory,
			BlockPos pos) {
		super(TYPE, containerId);
		this.pos = pos.immutable();
		this.owner = playerInventory.player;
		if (playerInventory.player instanceof ServerPlayer
				&& playerInventory.player.level()
						.getBlockEntity(this.pos)
						instanceof BackpackModificationTableBlockEntity table) {
			tableInventory = table.inventory();
		} else {
			tableInventory = new SimpleContainer(BackpackModificationTableBlockEntity.SIZE);
		}

		addSlot(new Slot(tableInventory, BackpackModificationTableBlockEntity.BACKPACK_SLOT,
				BACKPACK_SLOT_X, BACKPACK_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof CombatBackpackItem;
			}
		});
		for (int index = 0; index < PART_SLOT_POSITIONS.length; index++) {
			int slotIndex = BackpackModificationTableBlockEntity.PART_SLOT_START + index;
			int[] position = PART_SLOT_POSITIONS[index];
			int fixedIndex = index;
			addSlot(new Slot(tableInventory, slotIndex, position[0], position[1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof BackpackPartItem part
							&& acceptsPart(fixedIndex, part.kind())
							&& upgradeSlotAccepts(fixedIndex, part.kind());
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9,
						8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}
		syncTableState();
		normalizeFixedPartSlots();
	}

	@Override
	public MenuType<?> getType() {
		return TYPE;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();
		boolean moved = false;
		if (slotIndex < PLAYER_SLOT_START) {
			moved = moveItemStackTo(
					original,
					PLAYER_SLOT_START,
					slots.size(),
					true);
		} else if (original.getItem() instanceof CombatBackpackItem) {
			moved = moveItemStackTo(
					original,
					BackpackModificationTableBlockEntity.BACKPACK_SLOT,
					BackpackModificationTableBlockEntity.BACKPACK_SLOT + 1,
					true);
		} else if (original.getItem() instanceof BackpackPartItem) {
			BackpackPartItem.Kind kind =
					((BackpackPartItem) original.getItem()).kind();
			int target = BackpackModificationTableBlockEntity.PART_SLOT_START
					+ fixedSlot(kind);
			moved = moveItemStackTo(original, target, target + 1, true);
		} else if (slotIndex < HOTBAR_SLOT_START) {
			moved = moveItemStackTo(original, HOTBAR_SLOT_START, slots.size(), true);
		} else {
			moved = moveItemStackTo(original, PLAYER_SLOT_START, HOTBAR_SLOT_START, true);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		syncTableState();
		return copy;
	}

	@Override
	public void clicked(
			int slotIndex,
			int button,
			ClickType actionType,
			Player player) {
		super.clicked(slotIndex, button, actionType, player);
		syncTableState();
	}

	@Override
	public void slotsChanged(Container inventory) {
		super.slotsChanged(inventory);
		syncTableState();
	}

	private void syncTableState() {
		if (installing || !(owner instanceof ServerPlayer)) {
			return;
		}
		ItemStack backpack = tableInventory.getItem(
				BackpackModificationTableBlockEntity.BACKPACK_SLOT);
		boolean previousBackpackPresent = lastBackpack.getItem() instanceof CombatBackpackItem;
		boolean nextBackpackPresent = backpack.getItem() instanceof CombatBackpackItem;
		boolean sameStack = ItemStack.matches(backpack, lastBackpack);
		if (BackpackTableTransition.shouldDetachPreviousBackpack(
				previousBackpackPresent, nextBackpackPresent, sameStack)) {
			stowInstalledPart(lastBackpack, BackpackPartItem.Kind.CORE);
			stowInstalledLauncher(lastBackpack);
			stowInstalledModule(lastBackpack);
			tableInventory.setChanged();
			broadcastChanges();
		}
		if (!nextBackpackPresent) {
			lastBackpack = ItemStack.EMPTY;
			return;
		}
		installing = true;
		try {
			boolean replaced = !sameStack;
			boolean preserveCore = false;
			boolean preserveLauncher = false;
			if (replaced) {
				if (BackpackUpgradeData.hasCore(backpack)
						&& !hasPart(BackpackPartItem.Kind.CORE)) {
					if (insertPart(
							BackpackPartItem.Kind.CORE)) {
						preserveCore = false;
					} else {
						preserveCore = true;
					}
				}
				if (BackpackUpgradeData.hasLauncher(backpack)
						&& !hasLauncherPart()) {
					if (insertLauncherPart(
							BackpackUpgradeData.launcherType(backpack))) {
						preserveLauncher = false;
					} else {
						preserveLauncher = true;
					}
				}
				if (BackpackUpgradeData.hasMissileBox(backpack)
						&& !hasPart(BackpackPartItem.Kind.MISSILE_BOX)) {
					insertPart(BackpackPartItem.Kind.MISSILE_BOX);
				}
				if (BackpackUpgradeData.hasGolemCapsule(backpack)
						&& !hasPart(BackpackPartItem.Kind.GOLEM_CAPSULE)) {
					insertPart(BackpackPartItem.Kind.GOLEM_CAPSULE);
				}
			}
			int coreSlot = partSlot(BackpackPartItem.Kind.CORE);
			ItemStack launcherPart = launcherPartStack();
			BackpackUpgradeData.LauncherType launcherType =
					launcherPart.getItem() instanceof BackpackPartItem part
							&& part.kind() == BackpackPartItem.Kind.AUTO_LAUNCHER
							? BackpackUpgradeData.LauncherType.AUTO
							: launcherPart.getItem() instanceof BackpackPartItem summonPart
									&& summonPart.kind() == BackpackPartItem.Kind.SUMMON_ANTENNA
									? BackpackUpgradeData.LauncherType.SUMMON_ANTENNA
							: launcherPart.getItem() instanceof BackpackPartItem jumpPart
									&& jumpPart.kind() == BackpackPartItem.Kind.JUMP_THRUSTER
									? BackpackUpgradeData.LauncherType.JUMP_THRUSTER
							: launcherPart.getItem() instanceof BackpackPartItem bannerPart
									&& bannerPart.kind() == BackpackPartItem.Kind.BATTLE_BANNER
									? BackpackUpgradeData.LauncherType.BATTLE_BANNER
							: launcherPart.isEmpty() && preserveLauncher
									? BackpackUpgradeData.launcherType(backpack)
									: BackpackUpgradeData.LauncherType.PORTABLE;
			boolean core = coreSlot >= 0 || preserveCore;
			boolean launcher = core
					&& (!launcherPart.isEmpty() || preserveLauncher);
			BackpackUpgradeData.setCore(backpack, core);
			BackpackUpgradeData.setLauncherType(backpack,
					launcher ? launcherType : BackpackUpgradeData.LauncherType.NONE);
			BackpackUpgradeData.setCoreSlot(
					backpack,
					coreSlot >= 0 ? coreSlot
							: (preserveCore ? BackpackUpgradeData.coreSlot(backpack) : -1));
			BackpackUpgradeData.setLauncherSlot(
					backpack,
					launcher ? LAUNCHER_PART_SLOT
							: (preserveLauncher ? BackpackUpgradeData.launcherSlot(backpack) : -1));
			ItemStack missilePart = tableInventory.getItem(
					BackpackModificationTableBlockEntity.PART_SLOT_START + MISSILE_PART_SLOT);
			boolean missileBox = missilePart.getItem() instanceof BackpackPartItem module
					&& module.kind() == BackpackPartItem.Kind.MISSILE_BOX;
			BackpackUpgradeData.setMissileBox(backpack, missileBox);
			BackpackUpgradeData.setMissileBoxSlot(backpack, missileBox ? MISSILE_PART_SLOT : -1);
			boolean golemCapsule = missilePart.getItem() instanceof BackpackPartItem capsule
					&& capsule.kind() == BackpackPartItem.Kind.GOLEM_CAPSULE;
			BackpackUpgradeData.setGolemCapsule(backpack, golemCapsule);
			BackpackUpgradeData.setGolemCapsuleSlot(backpack,
					golemCapsule ? GOLEM_CAPSULE_PART_SLOT : -1);
			lastBackpack = backpack.copy();
			tableInventory.setChanged();
		} finally {
			installing = false;
		}
		broadcastChanges();
	}

	private void stowInstalledLauncher(ItemStack backpack) {
		if (!BackpackUpgradeData.hasLauncher(backpack)) {
			return;
		}
		for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
				slot < BackpackModificationTableBlockEntity.SIZE; slot++) {
			ItemStack stack = tableInventory.getItem(slot);
			if (stack.getItem() instanceof BackpackPartItem part && part.isLauncherKind()) {
				removeOne(tableInventory, slot, stack);
				return;
			}
		}
	}

	/** 拆背包时把导弹舱/魔像空投舱部件退回槽位。 */
	private void stowInstalledModule(ItemStack backpack) {
		BackpackPartItem.Kind kind = BackpackUpgradeData.hasMissileBox(backpack)
				? BackpackPartItem.Kind.MISSILE_BOX
				: BackpackUpgradeData.hasGolemCapsule(backpack)
						? BackpackPartItem.Kind.GOLEM_CAPSULE : null;
		if (kind == null) {
			return;
		}
		for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
				slot < BackpackModificationTableBlockEntity.SIZE; slot++) {
			ItemStack stack = tableInventory.getItem(slot);
			if (stack.getItem() instanceof BackpackPartItem part
					&& part.kind() == kind) {
				removeOne(tableInventory, slot, stack);
				return;
			}
		}
	}

	private void stowInstalledPart(ItemStack backpack, BackpackPartItem.Kind kind) {
		boolean installed = kind == BackpackPartItem.Kind.CORE
				? BackpackUpgradeData.hasCore(backpack)
				: BackpackUpgradeData.hasLauncher(backpack);
		if (!installed) {
			return;
		}
		for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
				slot < BackpackModificationTableBlockEntity.SIZE;
				slot++) {
			ItemStack stack = tableInventory.getItem(slot);
			if (stack.getItem() instanceof BackpackPartItem part
					&& part.kind() == kind) {
				removeOne(tableInventory, slot, stack);
				return;
			}
		}
	}

	private void removeOne(Container inventory, int slot, ItemStack stack) {
		stack.shrink(1);
		if (stack.isEmpty()) {
			inventory.setItem(slot, ItemStack.EMPTY);
		}
	}

	private boolean hasPart(BackpackPartItem.Kind kind) {
		return partSlot(kind) >= 0;
	}

	private boolean hasLauncherPart() {
		return !launcherPartStack().isEmpty();
	}

	private ItemStack launcherPartStack() {
		ItemStack stack = tableInventory.getItem(
				BackpackModificationTableBlockEntity.PART_SLOT_START + LAUNCHER_PART_SLOT);
		return stack.getItem() instanceof BackpackPartItem part && part.isLauncherKind()
				? stack : ItemStack.EMPTY;
	}

	private int partSlot(BackpackPartItem.Kind kind) {
		ItemStack stack = tableInventory.getItem(
				BackpackModificationTableBlockEntity.PART_SLOT_START + fixedSlot(kind));
		return stack.getItem() instanceof BackpackPartItem part && part.kind() == kind
				? fixedSlot(kind) : -1;
	}

	private boolean insertPart(BackpackPartItem.Kind kind) {
		int fixedIndex = BackpackModificationTableBlockEntity.PART_SLOT_START
				+ fixedSlot(kind);
		if (tableInventory.getItem(fixedIndex).isEmpty()) {
			tableInventory.setItem(fixedIndex, newPart(kind));
			return true;
		}
		return false;
	}

	private boolean insertLauncherPart(BackpackUpgradeData.LauncherType type) {
		if (type == BackpackUpgradeData.LauncherType.NONE) {
			return false;
		}
		return insertPart(switch (type) {
			case AUTO -> BackpackPartItem.Kind.AUTO_LAUNCHER;
			case SUMMON_ANTENNA -> BackpackPartItem.Kind.SUMMON_ANTENNA;
			case JUMP_THRUSTER -> BackpackPartItem.Kind.JUMP_THRUSTER;
			case BATTLE_BANNER -> BackpackPartItem.Kind.BATTLE_BANNER;
			default -> BackpackPartItem.Kind.LAUNCHER;
		});
	}

	private void normalizeFixedPartSlots() {
		if (installing || !(owner instanceof ServerPlayer)) {
			return;
		}
		installing = true;
		try {
			ItemStack core = ItemStack.EMPTY;
			ItemStack launcher = ItemStack.EMPTY;
			ItemStack module = ItemStack.EMPTY;
			for (int slot = BackpackModificationTableBlockEntity.PART_SLOT_START;
					slot < BackpackModificationTableBlockEntity.SIZE; slot++) {
				ItemStack stack = tableInventory.getItem(slot);
				if (stack.isEmpty()) {
					continue;
				}
				BackpackPartItem.Kind kind = stack.getItem() instanceof BackpackPartItem part
						? part.kind() : null;
				if (kind == BackpackPartItem.Kind.CORE && core.isEmpty()) {
					core = stack.copy();
				} else if ((kind == BackpackPartItem.Kind.LAUNCHER
						|| kind == BackpackPartItem.Kind.AUTO_LAUNCHER
						|| kind == BackpackPartItem.Kind.SUMMON_ANTENNA
						|| kind == BackpackPartItem.Kind.JUMP_THRUSTER
						|| kind == BackpackPartItem.Kind.BATTLE_BANNER)
						&& launcher.isEmpty()) {
					launcher = stack.copy();
				} else if ((kind == BackpackPartItem.Kind.MISSILE_BOX
						|| kind == BackpackPartItem.Kind.GOLEM_CAPSULE)
						&& module.isEmpty()) {
					module = stack.copy();
				} else {
					owner.getInventory().placeItemBackInInventory(stack.copy());
				}
				tableInventory.setItem(slot, ItemStack.EMPTY);
			}
			tableInventory.setItem(
					BackpackModificationTableBlockEntity.PART_SLOT_START + CORE_PART_SLOT,
					core);
			tableInventory.setItem(
					BackpackModificationTableBlockEntity.PART_SLOT_START + LAUNCHER_PART_SLOT,
					launcher);
			tableInventory.setItem(
					BackpackModificationTableBlockEntity.PART_SLOT_START + MISSILE_PART_SLOT,
					module);
			tableInventory.setChanged();
		} finally {
			installing = false;
		}
		broadcastChanges();
	}

	private static int fixedSlot(BackpackPartItem.Kind kind) {
		return switch (kind) {
			case CORE -> CORE_PART_SLOT;
			case MISSILE_BOX, GOLEM_CAPSULE -> MISSILE_PART_SLOT;
			default -> LAUNCHER_PART_SLOT;
		};
	}

	private static boolean acceptsPart(int slot, BackpackPartItem.Kind kind) {
		return switch (slot) {
			case CORE_PART_SLOT -> kind == BackpackPartItem.Kind.CORE;
			case LAUNCHER_PART_SLOT ->
					kind == BackpackPartItem.Kind.LAUNCHER
							|| kind == BackpackPartItem.Kind.AUTO_LAUNCHER
							|| kind == BackpackPartItem.Kind.SUMMON_ANTENNA
							|| kind == BackpackPartItem.Kind.BATTLE_BANNER
							|| kind == BackpackPartItem.Kind.JUMP_THRUSTER;
			case MISSILE_PART_SLOT -> kind == BackpackPartItem.Kind.MISSILE_BOX
					|| kind == BackpackPartItem.Kind.GOLEM_CAPSULE;
			default -> false;
		};
	}

	/** 第三格附加模块必须与已装发射器匹配：跳跃推进器→导弹舱，召唤天线→魔像空投舱。 */
	private boolean upgradeSlotAccepts(int slot, BackpackPartItem.Kind kind) {
		if (slot != MISSILE_PART_SLOT) {
			return true;
		}
		return switch (kind) {
			case MISSILE_BOX -> hasJumpThrusterInstalled();
			case GOLEM_CAPSULE -> hasSummonAntennaInstalled();
			default -> false;
		};
	}

	private boolean hasJumpThrusterInstalled() {
		return launcherType(launcherPartStack()) == BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
	}

	private boolean hasSummonAntennaInstalled() {
		return launcherType(launcherPartStack()) == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
	}

	private static BackpackUpgradeData.LauncherType launcherType(ItemStack stack) {
		if (!(stack.getItem() instanceof BackpackPartItem part)) {
			return BackpackUpgradeData.LauncherType.NONE;
		}
		return switch (part.kind()) {
			case AUTO_LAUNCHER -> BackpackUpgradeData.LauncherType.AUTO;
			case SUMMON_ANTENNA -> BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
			case JUMP_THRUSTER -> BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
			case BATTLE_BANNER -> BackpackUpgradeData.LauncherType.BATTLE_BANNER;
			case LAUNCHER -> BackpackUpgradeData.LauncherType.PORTABLE;
			default -> BackpackUpgradeData.LauncherType.NONE;
		};
	}

	private ItemStack newPart(BackpackPartItem.Kind kind) {
		return switch (kind) {
			case CORE -> new ItemStack(TkGunServer.BACKPACK_CORE);
			case LAUNCHER -> new ItemStack(TkGunServer.PORTABLE_LAUNCHER);
			case AUTO_LAUNCHER -> new ItemStack(TkGunServer.AUTO_PORTABLE_LAUNCHER);
			case SUMMON_ANTENNA -> new ItemStack(TkGunServer.SUMMON_ANTENNA);
			case BATTLE_BANNER -> new ItemStack(
					com.mechaniciancombat.registry.ModRegistries.WAR_BANNER_ITEM.get());
			case JUMP_THRUSTER -> new ItemStack(
					com.mechaniciancombat.registry.ModRegistries.JUMP_BACKPACK_THRUSTER.get());
			case MISSILE_BOX -> new ItemStack(
					com.mechaniciancombat.registry.ModRegistries.MISSILE_BOX.get());
			case GOLEM_CAPSULE -> new ItemStack(
					com.mechaniciancombat.registry.ModRegistries.GOLEM_DISPATCH_CAPSULE.get());
		};
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().getBlockState(pos)
				.is(TkGunServer.BACKPACK_MODIFICATION_TABLE)
				&& player.distanceToSqr(
						pos.getX() + 0.5D,
						pos.getY() + 0.5D,
						pos.getZ() + 0.5D) <= 64.0D;
	}
}
