package com.mechaniciancombat.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

/** Netherite-stat armor requires the equipped Curios undersuit. */
public final class MechanicianArmorItem extends ArmorItem {
    public MechanicianArmorItem(Type type, Properties properties) { super(ArmorMaterials.NETHERITE, type, properties.stacksTo(1)); }

    /** 护甲部件系统使用的槽位分类。 */
    public MechanicianArmorPiece piece() {
        return switch (getType()) {
            case CHESTPLATE -> MechanicianArmorPiece.CHESTPLATE;
            case LEGGINGS -> MechanicianArmorPiece.LEGGINGS;
            case BOOTS -> MechanicianArmorPiece.BOOTS;
            default -> MechanicianArmorPiece.UNDERSUIT;
        };
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new com.mechaniciancombat.client.MechanicianArmorClient());
    }

    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
            net.minecraft.world.entity.EquipmentSlot slot, String type) {
        return "tkgun:textures/entity/mechanician_downsuit.png";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!MechanicianUndersuitItem.isEquipped(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.tkgun.requires_undersuit"), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.tkgun.mechanician_" + getType().getName()));
    }
}
