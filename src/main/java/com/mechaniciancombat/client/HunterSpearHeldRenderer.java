package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.item.MechanicianHunterSpearItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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

/** 猎矛部署期间画在玩家手上的猎矛，并上报 `flag_maker` 落点。 */
public final class HunterSpearHeldRenderer
        extends GeoReplacedEntityRenderer<AbstractClientPlayer, MechanicianHunterSpearItem> {
    private static EntityRendererProvider.Context context;
    private static HunterSpearHeldRenderer instance;
    private static AbstractClientPlayer activePlayer;
    private static double seconds;

    private HunterSpearHeldRenderer(EntityRendererProvider.Context context,
                                    MechanicianHunterSpearItem item) {
        super(context, new HunterSpearHeldGeoModel(), item);
    }

    public static void setContext(EntityRendererProvider.Context ctx) {
        context = ctx;
    }

    public static void renderIfActive(AbstractClientPlayer player, float yaw, float tickDelta,
                                      PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (context == null || player.level() == null
                || !HunterSpearSkillClientState.isActive(player.getId())
                || !(TkGunServer.HUNTER_SPEAR instanceof MechanicianHunterSpearItem item)) {
            return;
        }
        if (instance == null) {
            instance = new HunterSpearHeldRenderer(context, item);
        }
        activePlayer = player;
        seconds = HunterSpearSkillClientState.animationSeconds(
                player.getId(), player.level().getGameTime(), tickDelta);
        try {
            EntityRenderer<AbstractClientPlayer> renderer = instance;
            renderer.render(player, yaw, tickDelta, poseStack, buffers, light);
        } finally {
            activePlayer = null;
        }
    }

    @Override
    public void actuallyRender(PoseStack poseStack, MechanicianHunterSpearItem animatable,
                               BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                true, partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, MechanicianHunterSpearItem animatable,
                                  GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        if (activePlayer != null) {
            HunterSpearHeldPose.apply(bone, seconds);
            if ("flag_maker".equals(bone.getName())
                    && activePlayer == Minecraft.getInstance().player) {
                Vector3d world = bone.getWorldPosition();
                HunterSpearSkillClientState.updateMarker(activePlayer.getId(),
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
        if ("spear".equals(bone.getName())) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
                    red, green, blue, alpha);
        }
    }
}
