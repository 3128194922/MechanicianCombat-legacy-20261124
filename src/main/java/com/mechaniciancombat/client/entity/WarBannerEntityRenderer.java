package com.mechaniciancombat.client.entity;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.WarBannerGeoModel;
import com.mechaniciancombat.entity.WarBannerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 战旗渲染：模型 + 地面范围指示圈。
 *
 * <p>实体根节点在 locator 下方 {@link WarBannerEntity#LOCATOR_HEIGHT}，
 * 所以这里只把**视觉模型**抬回去，旗杆底部正好贴地。
 * 范围圈用作者给的 8 帧序列贴图，按 2 tick/帧循环。
 */
public final class WarBannerEntityRenderer extends GeoEntityRenderer<WarBannerEntity> {
    private static final ResourceLocation RANGE_TEXTURE =
            TkGun.id("textures/entity/war_banner_effect.png");
    private static final RenderType RANGE_LAYER =
            RenderType.entityTranslucentEmissive(RANGE_TEXTURE, false);
    private static final int RANGE_FRAME_COUNT = 8;
    private static final int RANGE_FRAME_TICKS = 2;
    private static final float RANGE_HALF_SIZE = (float) WarBannerEntity.AURA_RADIUS;
    private static final float RANGE_HEIGHT = 0.25F / 16.0F;

    public WarBannerEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WarBannerGeoModel());
    }

    @Override
    public void preRender(PoseStack poseStack, WarBannerEntity animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.translate(0.0D, WarBannerEntity.LOCATOR_HEIGHT, 0.0D);
        renderRangeIndicator(poseStack, animatable, bufferSource);
    }

    @Override
    public boolean shouldRender(WarBannerEntity entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        return frustum.isVisible(entity.getBoundingBox().inflate(WarBannerEntity.AURA_RADIUS + 1.0D));
    }

    @Override
    protected void applyRotations(WarBannerEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        // 战旗是静态实体：用同步过来的精确 yaw，而不是被量化到 1/256 圈的实体角度。
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - animatable.visualYaw()));
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer,
                           int packedLight, int packedOverlay, float red, float green,
                           float blue, float alpha) {
        // 旗面自带明暗；用全亮避免夜里发黑。
        super.renderCube(poseStack, cube, buffer, LightTexture.FULL_BRIGHT,
                packedOverlay, red, green, blue, alpha);
    }

    private static void renderRangeIndicator(PoseStack poseStack, WarBannerEntity entity,
                                             MultiBufferSource consumers) {
        int frame = (entity.tickCount / RANGE_FRAME_TICKS) % RANGE_FRAME_COUNT;
        float v0 = (float) frame / RANGE_FRAME_COUNT;
        float v1 = (float) (frame + 1) / RANGE_FRAME_COUNT;
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = consumers.getBuffer(RANGE_LAYER);
        rangeVertex(buffer, matrix, -RANGE_HALF_SIZE, RANGE_HEIGHT, -RANGE_HALF_SIZE, 0.0F, v0);
        rangeVertex(buffer, matrix, -RANGE_HALF_SIZE, RANGE_HEIGHT, RANGE_HALF_SIZE, 0.0F, v1);
        rangeVertex(buffer, matrix, RANGE_HALF_SIZE, RANGE_HEIGHT, RANGE_HALF_SIZE, 1.0F, v1);
        rangeVertex(buffer, matrix, RANGE_HALF_SIZE, RANGE_HEIGHT, -RANGE_HALF_SIZE, 1.0F, v0);
    }

    private static void rangeVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y,
                                    float z, float u, float v) {
        buffer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
