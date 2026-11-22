package com.mechaniciancombat.client.entity;

import com.mechaniciancombat.client.HunterTurretGeoModel;
import com.mechaniciancombat.entity.HunterTurretEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 按服务端目标旋转炮台肩部与前臂，并在开火时补枪口辉光。 */
public final class HunterTurretEntityRenderer extends GeoEntityRenderer<HunterTurretEntity> {
    private final Map<UUID, Integer> lastMuzzleSequence = new HashMap<>();

    public HunterTurretEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HunterTurretGeoModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, HunterTurretEntity animatable, GeoBone bone,
                                  RenderType type, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, float red, float green, float blue, float alpha) {
        spawnMuzzleGlowAtLocator(poseStack, animatable, bone);
        if (!animatable.isSummoning() && "shoulder".equals(bone.getName())) {
            float base = bone.getInitialSnapshot() == null ? 0.0F
                    : bone.getInitialSnapshot().getRotY();
            bone.setRotY(base - (float) Math.toRadians(animatable.shoulderYaw()));
        } else if (!animatable.isSummoning() && "forearm".equals(bone.getName())) {
            float base = bone.getInitialSnapshot() == null ? 0.0F
                    : bone.getInitialSnapshot().getRotX();
            bone.setRotX(base + (float) Math.toRadians(animatable.forearmPitch()));
        }
        super.renderRecursively(poseStack, animatable, bone, type, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private void spawnMuzzleGlowAtLocator(PoseStack poseStack, HunterTurretEntity entity, GeoBone bone) {
        if (!"gunmode".equals(bone.getName())) {
            return;
        }
        int sequence = entity.fireSequence();
        UUID uuid = entity.getUUID();
        Integer previous = lastMuzzleSequence.get(uuid);
        if (previous == null) {
            lastMuzzleSequence.put(uuid, sequence);
            return;
        }
        if (sequence <= previous || entity.isSummoning()) {
            return;
        }
        int fireMode = entity.fireMode();
        if (fireMode != 2 && fireMode != 3) {
            return;
        }
        double localY = (fireMode == 2 ? 17.5D : 14.5D) / 16.0D;
        double localZ = -14.5D / 16.0D;
        Vec3 position;
        poseStack.pushPose();
        try {
            RenderUtils.translateAndRotateMatrixForBone(poseStack, bone);
            Vector4f rendered = poseStack.last().pose()
                    .transform(new Vector4f(0.0F, (float) localY, (float) localZ, 1.0F));
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            position = new Vec3(camera.x + rendered.x, camera.y + rendered.y, camera.z + rendered.z);
        } finally {
            poseStack.popPose();
        }
        lastMuzzleSequence.put(uuid, sequence);
        entity.level().addParticle(com.mechaniciancombat.TkGunParticles.MUZZLE_GLOW,
                position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        // 旧的 F1/F2/F3 命中反馈组不再画（由 SteelArrowImpactEntity 负责）。
        if ("F1".equals(bone.getName()) || "F2".equals(bone.getName())
                || "F3".equals(bone.getName())) {
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
                red, green, blue, alpha);
    }
}
