package com.mechaniciancombat.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/** r611 undersuit; active in any equipped Curios slot, tagged for charm by default. */
public final class MechanicianUndersuitItem extends Item implements ICurioItem {
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("49ce0789-2f5c-455b-8a81-c0d40ae3dbe6");
    private static final String ARMOR_MODIFIER_NAME = "tkgun.undersuit_armor";

    private static AttributeModifier armorModifier() {
        return new AttributeModifier(ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME,
                ArmorMaterials.NETHERITE.getDefenseForType(ArmorItem.Type.HELMET), AttributeModifier.Operation.ADDITION);
    }
    public MechanicianUndersuitItem(Properties properties) { super(properties.stacksTo(1)); }

    public static boolean isEquipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve()
                .map(inventory -> inventory.isEquipped(stack -> stack.getItem() instanceof MechanicianUndersuitItem))
                .orElse(false);
    }

    /** Mirrors CuriosLayer: cosmetic stack wins; otherwise the render toggle controls the functional stack. */
    public static boolean isVisiblyEquipped(LivingEntity entity) {
        var inventory = CuriosApi.getCuriosInventory(entity).resolve().orElse(null);
        if (inventory == null) return false;
        for (var slot : inventory.getCurios().values()) {
            var functional = slot.getStacks();
            var cosmetic = slot.getCosmeticStacks();
            var renders = slot.getRenders();
            for (int i = 0; i < functional.getSlots(); i++) {
                ItemStack visible = cosmetic.getStackInSlot(i);
                if (visible.isEmpty() && i < renders.size() && renders.get(i))
                    visible = functional.getStackInSlot(i);
                if (visible.getItem() instanceof MechanicianUndersuitItem) return true;
            }
        }
        return false;
    }

    public static boolean isWearingFullSet(LivingEntity entity) {
        return isEquipped(entity)
            && isPiece(entity, EquipmentSlot.CHEST, ArmorItem.Type.CHESTPLATE)
            && isPiece(entity, EquipmentSlot.LEGS, ArmorItem.Type.LEGGINGS)
            && isPiece(entity, EquipmentSlot.FEET, ArmorItem.Type.BOOTS);
    }

    private static boolean isPiece(LivingEntity entity, EquipmentSlot slot, ArmorItem.Type type) {
        return entity.getItemBySlot(slot).getItem() instanceof MechanicianArmorItem armor && armor.getType() == type;
    }

    public static boolean canUseArmorDodge(LivingEntity entity) {
        return isWearingFullSet(entity) && BackpackUpgradeData.hasCore(CombatBackpackItem.equippedStack(entity));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext context, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        modifiers.put(Attributes.ARMOR, armorModifier());
        return modifiers;
    }

    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        LivingEntity entity = context.entity();
        if (entity.level().isClientSide || context.cosmetic()) return;
        var armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.getModifier(ARMOR_MODIFIER_ID) == null && isEquipped(entity))
            armor.addTransientModifier(armorModifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.tkgun.mechanician_undersuit"));
    }
}
