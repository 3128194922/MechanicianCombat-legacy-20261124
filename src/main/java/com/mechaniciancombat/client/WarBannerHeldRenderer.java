package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.BackpackPartItem;
import com.mechaniciancombat.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * 战旗施法期间画在玩家手上的旗子。
 *
 * <p>与玩家 rig 用同一套实体空间基（由 {@code PlayerRenderer} 的渲染入口驱动），
 * 所以直接复用玩家渲染矩阵即可和身体对齐。
 * 同时把 `flag_maker` 骨骼的世界坐标报给 {@link WarBannerSkillClientState}，
 * 供服务端决定真正的落点。
 */
public final class WarBannerHeldRenderer
        extends GeoReplacedEntityRenderer<AbstractClientPlayer, BackpackPartItem> {
    private static EntityRendererProvider.Context context;
    private static WarBannerHeldRenderer instance;
    private static AbstractClientPlayer activePlayer;
    private static double seconds;

    private WarBannerHeldRenderer(EntityRendererProvider.Context context, BackpackPartItem item) {
        super(context, new WarBannerHeldGeoModel(), item);
    }

    public static void setContext(EntityRendererProvider.Context ctx) {
        context = ctx;
    }

    public static void renderIfActive(AbstractClientPlayer player, float yaw, float tickDelta,
                                      PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (context == null || player.level() == null
                || !WarBannerSkillClientState.isActive(player.getId())) {
            return;
        }
        if (!(ModRegistries.WAR_BANNER_ITEM.get() instanceof BackpackPartItem item)) {
            return;
        }
        if (instance == null) {
            instance = new WarBannerHeldRenderer(context, item);
        }
        activePlayer = player;
        seconds = WarBannerSkillClientState.animationSeconds(
                player.getId(), player.level().getGameTime(), tickDelta);
        try {
            EntityRenderer<AbstractClientPlayer> renderer = instance;
            renderer.render(player, yaw, tickDelta, poseStack, buffers, light);
        } finally {
            activePlayer = null;
        }
    }

    @Override
    public void actuallyRender(PoseStack poseStack, BackpackPartItem animatable, BakedGeoModel model,
                               RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, true,
                partialTick, LightTexture.FULL_BRIGHT, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, BackpackPartItem animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (activePlayer != null) {
            WarBannerHeldPose.apply(bone, seconds,
                    WarBannerSkillClientState.usesGunAnimation(activePlayer.getId()));
            if ("flag_maker".equals(bone.getName())
                    && activePlayer == net.minecraft.client.Minecraft.getInstance().player) {
                Vector3d world = bone.getWorldPosition();
                WarBannerSkillClientState.updateLocator(activePlayer.getId(),
                        new Vec3(world.x, world.y, world.z));
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        // 只画旗面；其它骨骼仅用于层级与 locator。
        if ("flag".equals(bone.getName())) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
                    red, green, blue, alpha);
        }
    }
}
