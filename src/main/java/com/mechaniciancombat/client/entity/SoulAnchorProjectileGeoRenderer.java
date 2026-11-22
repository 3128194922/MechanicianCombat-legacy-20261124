package com.mechaniciancombat.client.entity;

import com.mechaniciancombat.entity.SoulAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 箭头式世界空间灵魂锚渲染器。 */
public final class SoulAnchorProjectileGeoRenderer extends GeoEntityRenderer<SoulAnchorEntity> {
	public SoulAnchorProjectileGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new SoulAnchorProjectileGeoModel());
	}

	@Override
	public void render(SoulAnchorEntity entity, float yaw, float tickDelta, PoseStack poseStack,
					   MultiBufferSource buffers, int light) {
		super.render(entity, yaw, tickDelta, poseStack, buffers, LightTexture.FULL_BRIGHT);
	}

	@Override
	protected void applyRotations(SoulAnchorEntity entity, PoseStack poseStack, float ageInTicks,
								  float rotationYaw, float partialTick) {
		Vec3 velocity = entity.getDeltaMovement();
		Vec3 direction = entity.anchorState() == SoulAnchorEntity.State.FLYING
				&& velocity.lengthSqr() > 1.0E-6D ? velocity.normalize() : entity.flightDirection();
		poseStack.mulPose(new Quaternionf().rotationTo(
				new Vector3f(0.0F, 1.0F, 0.0F),
				new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)));
	}
}
