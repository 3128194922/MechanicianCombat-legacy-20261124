package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** 在相邻目标之间绘制面向相机的锁链带。 */
public final class SoulChainWorldRenderer {
	private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/connect_chain.png");
	private static final double SEGMENT_LENGTH = 1.0D;
	private static final double HALF_WIDTH = 0.5D;

	private SoulChainWorldRenderer() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
				SoulChainWorldRenderer::onRenderLevel);
	}

	private static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (SoulChainClientState.effects().isEmpty() || client.level == null) {
			return;
		}
		ClientLevel level = client.level;
		long now = level.getGameTime();
		float tickDelta = event.getPartialTick();
		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
		VertexConsumer buffer = buffers.getBuffer(
				RenderType.entityTranslucentEmissive(TEXTURE));
		PoseStack.Pose entry = poseStack.last();
		Matrix4f matrix = entry.pose();
		for (SoulChainClientState.Effect effect : SoulChainClientState.effects()) {
			float alpha = effect.alphaAt(now);
			Vec3 previous = null;
			for (SoulChainClientState.Node node : effect.nodes()) {
				Vec3 point = entityPoint(level, node.entityId(), node.fallback(), tickDelta);
				if (previous != null) {
					drawEdge(matrix, entry, buffer, previous, point, camera, alpha);
				}
				previous = point;
				alpha *= 0.9F;
			}
		}
	}

	private static Vec3 entityPoint(ClientLevel level, int entityId, Vec3 fallback,
									float tickDelta) {
		if (entityId < 0) {
			return fallback;
		}
		Entity entity = level.getEntity(entityId);
		if (entity == null) {
			return fallback;
		}
		double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
		double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
		double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
		return new Vec3(x, y + entity.getBbHeight() * 0.55D, z);
	}

	private static void drawEdge(Matrix4f matrix, PoseStack.Pose entry, VertexConsumer buffer,
								 Vec3 worldStart, Vec3 worldEnd, Vec3 camera, float alpha) {
		Vec3 start = worldStart.subtract(camera);
		Vec3 end = worldEnd.subtract(camera);
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0E-5D) {
			return;
		}
		Vec3 direction = delta.scale(1.0D / length);
		Vec3 view = start.add(end).scale(0.5D).scale(-1.0D);
		if (view.lengthSqr() < 1.0E-6D) {
			view = new Vec3(0.0D, 0.0D, 1.0D);
		}
		Vec3 side = direction.cross(view).normalize().scale(HALF_WIDTH);
		if (side.lengthSqr() < 1.0E-6D) {
			side = new Vec3(HALF_WIDTH, 0.0D, 0.0D);
		}
		int segments = Math.max(1, (int) Math.ceil(length / SEGMENT_LENGTH));
		for (int index = 0; index < segments; index++) {
			double t0 = index / (double) segments;
			double t1 = (index + 1) / (double) segments;
			Vec3 p0 = start.add(delta.scale(t0));
			Vec3 p1 = start.add(delta.scale(t1));
			vertex(matrix, entry, buffer, p0.subtract(side), 0.0F, 0.0F, alpha);
			vertex(matrix, entry, buffer, p0.add(side), 1.0F, 0.0F, alpha);
			vertex(matrix, entry, buffer, p1.add(side), 1.0F, 1.0F, alpha);
			vertex(matrix, entry, buffer, p1.subtract(side), 0.0F, 1.0F, alpha);
		}
	}

	private static void vertex(Matrix4f matrix, PoseStack.Pose entry, VertexConsumer buffer,
							   Vec3 point, float u, float v, float alpha) {
		buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
				.color(1.0F, 1.0F, 1.0F, alpha)
				.uv(u, v)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(LightTexture.FULL_BRIGHT)
				.normal(entry.normal(), 0.0F, 1.0F, 0.0F)
				.endVertex();
	}
}
