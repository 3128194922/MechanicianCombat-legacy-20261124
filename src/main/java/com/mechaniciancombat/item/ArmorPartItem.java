package com.mechaniciancombat.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 可装入 Mechanician 护甲的模块。 */
public final class ArmorPartItem extends Item {
	public enum Kind {
		C1(Group.CHEST),
		C2(Group.CHEST),
		C3(Group.CHEST),
		T1(Group.LEGS),
		T2(Group.LEGS),
		T3(Group.LEGS),
		F1(Group.FEET),
		F2(Group.FEET),
		F3(Group.FEET);

		public enum Group {
			CHEST(EquipmentSlot.CHEST),
			LEGS(EquipmentSlot.LEGS),
			FEET(EquipmentSlot.FEET);

			private final EquipmentSlot equipmentSlot;

			Group(EquipmentSlot equipmentSlot) {
				this.equipmentSlot = equipmentSlot;
			}

			public EquipmentSlot equipmentSlot() {
				return equipmentSlot;
			}
		}

		private final Group group;

		Kind(Group group) {
			this.group = group;
		}

		public Group group() {
			return group;
		}

		public EquipmentSlot equipmentSlot() {
			return group.equipmentSlot();
		}
	}

	private final Kind kind;

	public ArmorPartItem(Kind kind, Properties properties) {
		super(properties.stacksTo(1));
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public int slotIndex() {
		return kind.ordinal();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
								TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.tkgun.armor_part."
				+ kind.name().toLowerCase(java.util.Locale.ROOT)));
		if (kind == Kind.C3) {
			tooltip.add(Component.translatable("tooltip.tkgun.soul_chain.chain"));
		}
	}
}
