package com.mechaniciancombat.screen;

import com.mechaniciancombat.ArmorAbilities;
import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.item.ArmorPartItem;
import com.mechaniciancombat.item.ArmorUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * 装备查看 + 胸甲/护腿/靴子模块安装台。
 *
 * <p>三个护甲槽只是当前装备的镜像，不可取出/放入；九个模块槽按 C/T/F 分组，
 * 内容会写回对应护甲物品的 NBT。</p>
 */
public final class ArmorModificationTableScreenHandler extends AbstractContainerMenu {
	public static final int ARMOR_SLOT_START = 0;
	public static final int ARMOR_SLOT_COUNT = 3;
	public static final int PART_SLOT_START = ARMOR_SLOT_COUNT;
	public static final int PART_SLOT_COUNT = ArmorPartItem.Kind.values().length;
	public static final int PLAYER_SLOT_START = PART_SLOT_START + PART_SLOT_COUNT;
	public static final int HOTBAR_SLOT_START = PLAYER_SLOT_START + 27;

	private static final EquipmentSlot[] DISPLAY_SLOTS = {
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final int[][] ARMOR_SLOT_POSITIONS = {{7, 11}, {7, 29}, {7, 47}};
	private static final int[][] PART_SLOT_POSITIONS = {
			{26, 11}, {44, 11}, {62, 11},
			{26, 29}, {44, 29}, {62, 29},
			{26, 47}, {44, 47}, {62, 47}
	};
	private static final ArmorPartItem.Kind[] PART_SECTIONS = {
			ArmorPartItem.Kind.C1, ArmorPartItem.Kind.T1, ArmorPartItem.Kind.F1
	};

	public static MenuType<ArmorModificationTableScreenHandler> TYPE;

	private final BlockPos pos;
	private final Player owner;
	private final Container armorInventory = new SimpleContainer(ARMOR_SLOT_COUNT);
	private final Container partInventory = new SimpleContainer(PART_SLOT_COUNT);
	private final ItemStack[] lastPartContainers = {
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};
	private boolean syncing;

	public ArmorModificationTableScreenHandler(int containerId, Inventory playerInventory,
											   BlockPos pos) {
		super(TYPE, containerId);
		this.pos = pos.immutable();
		this.owner = playerInventory.player;

		for (int index = 0; index < ARMOR_SLOT_COUNT; index++) {
			int[] position = ARMOR_SLOT_POSITIONS[index];
			addSlot(new Slot(armorInventory, index, position[0], position[1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player player) {
					return false;
				}
			});
		}
		for (int index = 0; index < PART_SLOT_COUNT; index++) {
			int[] position = PART_SLOT_POSITIONS[index];
			int fixedIndex = index;
			addSlot(new Slot(partInventory, index, position[0], position[1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					if (!(stack.getItem() instanceof ArmorPartItem part)
							|| part.slotIndex() != fixedIndex) {
						return false;
					}
					return !ArmorUpgradeData.equippedPartContainer(
							owner, part.kind()).isEmpty();
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
	}

	@Override
	public void broadcastChanges() {
		syncTableState();
		super.broadcastChanges();
	}

	private void syncTableState() {
		if (syncing || !(owner instanceof net.minecraft.server.level.ServerPlayer player)) {
			return;
		}
		syncing = true;
		try {
			for (int index = 0; index < DISPLAY_SLOTS.length; index++) {
				ItemStack equipped = player.getItemBySlot(DISPLAY_SLOTS[index]);
				ItemStack displayed = armorInventory.getItem(index);
				if (!ItemStack.matches(displayed, equipped)) {
					armorInventory.setItem(index, equipped.isEmpty() ? ItemStack.EMPTY : equipped.copy());
				}
			}
			boolean chestChanged = false;
			for (int section = 0; section < PART_SECTIONS.length; section++) {
				ArmorPartItem.Kind sectionKind = PART_SECTIONS[section];
				ItemStack container = ArmorUpgradeData.equippedPartContainer(player, sectionKind);
				if (!ItemStack.matches(container, lastPartContainers[section])) {
					loadPartSlots(container, section);
				}
				boolean changed = persistPartSlots(container, section);
				if (changed) {
					player.setItemSlot(sectionKind.equipmentSlot(), container.copy());
					if (sectionKind.group() == ArmorPartItem.Kind.Group.CHEST) {
						chestChanged = true;
					}
				}
				lastPartContainers[section] = container.isEmpty()
						? ItemStack.EMPTY : container.copy();
			}
			if (chestChanged) {
				ArmorAbilities.sendSnapshot(player);
			}
		} finally {
			syncing = false;
		}
	}

	private void loadPartSlots(ItemStack container, int section) {
		int start = section * 3;
		for (int offset = 0; offset < 3; offset++) {
			ArmorPartItem.Kind kind = ArmorPartItem.Kind.values()[start + offset];
			ItemStack stored = ArmorUpgradeData.storedPart(container, kind);
			if (stored.isEmpty()) {
				stored = stackForId(ArmorUpgradeData.partId(container, kind), kind);
			}
			partInventory.setItem(start + offset, stored);
		}
	}

	private boolean persistPartSlots(ItemStack container, int section) {
		if (container.isEmpty()) {
			return false;
		}
		boolean changed = false;
		int start = section * 3;
		for (int offset = 0; offset < 3; offset++) {
			ArmorPartItem.Kind kind = ArmorPartItem.Kind.values()[start + offset];
			ItemStack stack = partInventory.getItem(start + offset);
			boolean valid = stack.getItem() instanceof ArmorPartItem part && part.kind() == kind;
			ItemStack desired = valid ? stack.copyWithCount(1) : ItemStack.EMPTY;
			String installedId = valid
					? net.minecraftforge.registries.ForgeRegistries.ITEMS
							.getKey(stack.getItem()).toString() : "";
			ItemStack stored = ArmorUpgradeData.storedPart(container, kind);
			if (stored.isEmpty()) {
				stored = stackForId(ArmorUpgradeData.partId(container, kind), kind);
			}
			boolean same = ItemStack.matches(stored, desired)
					&& Objects.equals(ArmorUpgradeData.partId(container, kind), installedId);
			if (!same) {
				changed = true;
				ArmorUpgradeData.setStoredPart(container, kind, desired);
			}
		}
		return changed;
	}

	private static ItemStack stackForId(String itemId, ArmorPartItem.Kind kind) {
		if (itemId == null || itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		net.minecraft.resources.ResourceLocation id =
				net.minecraft.resources.ResourceLocation.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
		return item instanceof ArmorPartItem part && part.kind() == kind
				? new ItemStack(item) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < ARMOR_SLOT_START || slotIndex >= slots.size()
				|| (slotIndex >= ARMOR_SLOT_START && slotIndex < PART_SLOT_START)) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();
		boolean moved;
		if (slotIndex >= PART_SLOT_START && slotIndex < PLAYER_SLOT_START) {
			moved = moveItemStackTo(original, PLAYER_SLOT_START, slots.size(), true);
		} else if (original.getItem() instanceof ArmorPartItem part) {
			int target = PART_SLOT_START + part.slotIndex();
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
		return copy;
	}

	@Override
	public MenuType<?> getType() {
		return TYPE;
	}

	@Override
	public void removed(Player player) {
		syncTableState();
		partInventory.clearContent();
		super.removed(player);
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().getBlockState(pos)
				.is(TkGunServer.ARMOR_MODIFICATION_TABLE)
				&& player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
						pos.getZ() + 0.5D) <= 64.0D;
	}
}
