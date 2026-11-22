package com.mechaniciancombat.client;

import com.mechaniciancombat.item.MechanicianArmorPiece;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Draws the authored armor in Forge's normal armor layer and foil pass. */
public final class MechanicianArmorClient implements IClientItemExtensions {
    private ArmorModel cachedModel;
    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack,
            EquipmentSlot slot, HumanoidModel<?> original) {
        MechanicianArmorPiece piece = switch (slot) {
            case CHEST -> MechanicianArmorPiece.CHESTPLATE;
            case LEGS -> MechanicianArmorPiece.LEGGINGS;
            case FEET -> MechanicianArmorPiece.BOOTS;
            default -> throw new IllegalArgumentException("Unsupported armor slot " + slot);
        };
        if (cachedModel == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR);
            cachedModel = new ArmorModel(root);
        }
        cachedModel.original = original;
        cachedModel.wearer = wearer;
        cachedModel.piece = piece;
        cachedModel.stack = stack;
        return cachedModel;
    }

    private static final class ArmorModel extends HumanoidModel<LivingEntity> {
        private HumanoidModel<?> original;
        private LivingEntity wearer;
        private MechanicianArmorPiece piece;
        private ItemStack stack = ItemStack.EMPTY;
        private ArmorModel(ModelPart root) { super(root); }
        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            MechanicianArmorGeometry.Part[] parts = piece == MechanicianArmorPiece.CHESTPLATE
                    ? new MechanicianArmorGeometry.Part[]{MechanicianArmorGeometry.Part.BODY,
                        MechanicianArmorGeometry.Part.RIGHT_ARM, MechanicianArmorGeometry.Part.LEFT_ARM}
                    : new MechanicianArmorGeometry.Part[]{MechanicianArmorGeometry.Part.RIGHT_LEG,
                        MechanicianArmorGeometry.Part.LEFT_LEG};
            MechanicianArmorGeometry.render(piece, stack, pose, ignored -> buffer, original, light, wearer,
                    wearer.walkAnimation.position(), wearer.walkAnimation.speed(),
                    wearer.getYHeadRot() - wearer.yBodyRot, parts);
        }
    }
}
