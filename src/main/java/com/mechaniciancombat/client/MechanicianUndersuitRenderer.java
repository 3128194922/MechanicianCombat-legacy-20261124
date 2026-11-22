package com.mechaniciancombat.client;

import com.mechaniciancombat.item.MechanicianArmorPiece;
import com.mechaniciancombat.item.MechanicianUndersuitItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class MechanicianUndersuitRenderer implements ICurioRenderer {
    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbAngle, float limbDistance, float partial, float age, float headYaw, float headPitch) {
        if (parent.getModel() instanceof PlayerModel<?> model) {
            MechanicianArmorGeometry.renderAll(MechanicianArmorPiece.UNDERSUIT, pose, buffers,
                    model, light, context.entity(), limbAngle, limbDistance, headYaw);
            if (MechanicianUndersuitItem.isWearingFullSet(context.entity())) {
                HelmetClientState.Pose helmet = HelmetClientState.pose(context.entity());
                MechanicianArmorGeometry.renderHelmet(pose, buffers, model, light,
                        context.entity(), limbAngle, limbDistance, headYaw,
                        helmet.animation(), helmet.seconds());
            }
        }
    }
}
