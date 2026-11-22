package com.mechaniciancombat.client.entity;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.entity.HunterSpearReturnEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 忠诚回归的猎矛：直接用猎矛 3D 物品模型渲染，并让长轴对准飞行方向。 */
public final class HunterSpearReturnEntityRenderer extends EntityRenderer<HunterSpearReturnEntity> {
    private static final ResourceLocation TEXTURE =
            TkGun.id("textures/item/mechanician_hunter_spear.png");

    public HunterSpearReturnEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HunterSpearReturnEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 direction = velocity.lengthSqr() < 1.0E-6D
                ? entity.getViewVector(1.0F) : velocity.normalize();
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)));
        ItemStack stack = TkGunServer.HUNTER_SPEAR == null
                ? ItemStack.EMPTY : new ItemStack(TkGunServer.HUNTER_SPEAR);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HunterSpearReturnEntity entity) {
        return TEXTURE;
    }
}
