package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 灵魂锚"已就绪"目标的锁定标记：6 帧、每帧 2 tick，始终朝向相机、不受深度遮挡。
 *
 * <p>这是作者给的瞄准反馈（`anchor_lock_0..5.png`），缺失时灵魂锚技能仍然能放，
 * 但玩家看不到"哪些目标会被牵引"。
 */
public final class AnchorLockParticleRenderer {
	private static final int FRAME_COUNT = 6;
	private static final int TICKS_PER_FRAME = 2;
	private static final RenderType[] LAYERS = new RenderType[FRAME_COUNT];

	static {
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			LAYERS[frame] = createLayer(
					TkGun.id("textures/particle/anchor_lock_" + frame + ".png"));
		}
	}

	private AnchorLockParticleRenderer() {
	}

	private static RenderType createLayer(ResourceLocation texture) {
		return RenderType.entityCutoutNoCull(texture);
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || SoulAnchorMarkClientState.readyMarks().isEmpty()) {
			return;
		}
		int frame = Math.floorMod((int) (client.level.getGameTime() / TICKS_PER_FRAME), FRAME_COUNT);
		RenderType layer = LAYERS[frame];
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
		VertexConsumer buffer = buffers.getBuffer(layer);
		Matrix4f matrix = poseStack.last().pose();
		Camera camera = event.getCamera();
		Vec3 cameraPos = camera.getPosition();
		// `getLeftVector()` 指向相机左侧，取反得到屏幕右方向（作者原实现同样用 right）。
		Vector3f right = new Vector3f(camera.getLeftVector()).mul(-1.0F);
		Vector3f up = new Vector3f(camera.getUpVector());
		for (SoulAnchorMarkClientState.ReadyMark mark : SoulAnchorMarkClientState.readyMarks()) {
			Entity entity = client.level.getEntity(mark.targetEntityId());
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			double centerY = entity.getY() + entity.getBbHeight() * 0.55D;
			Vec3 center = new Vec3(entity.getX(), centerY, entity.getZ()).subtract(cameraPos);
			float size = Mth.clamp(Math.max(entity.getBbWidth(), entity.getBbHeight()) * 1.35F,
					1.8F, 3.4F);
			drawBillboard(matrix, buffer, center, right, up, size);
		}
		buffers.endBatch(layer);
	}

	private static void drawBillboard(Matrix4f matrix, VertexConsumer buffer,
									  Vec3 center, Vector3f right, Vector3f up, float size) {
		float half = size * 0.5F;
		Vector3f left = new Vector3f(right).mul(-half);
		Vector3f rightSide = new Vector3f(right).mul(half);
		Vector3f down = new Vector3f(up).mul(-half);
		Vector3f top = new Vector3f(up).mul(half);
		Vec3 bottomLeft = center.add(left.x + down.x, left.y + down.y, left.z + down.z);
		Vec3 bottomRight = center.add(rightSide.x + down.x, rightSide.y + down.y, rightSide.z + down.z);
		Vec3 topRight = center.add(rightSide.x + top.x, rightSide.y + top.y, rightSide.z + top.z);
		Vec3 topLeft = center.add(left.x + top.x, left.y + top.y, left.z + top.z);
		vertex(matrix, buffer, bottomLeft, 0.0F, 1.0F);
		vertex(matrix, buffer, bottomRight, 1.0F, 1.0F);
		vertex(matrix, buffer, topRight, 1.0F, 0.0F);
		vertex(matrix, buffer, topLeft, 0.0F, 0.0F);
	}

	private static void vertex(Matrix4f matrix, VertexConsumer buffer, Vec3 point, float u, float v) {
		// ⚠️ `RenderType.entityCutoutNoCull` 用的是 `DefaultVertexFormat.NEW_ENTITY`，
		// 元素依次是 POSITION / COLOR / UV0 / **UV1(overlay)** / UV2(light) / **NORMAL**。
		// 少填任何一个，`BufferBuilder#endVertex` 就会抛
		// `IllegalStateException: Not filled all elements of the vertex` —— 而且是**渲染线程崩**，
		// 整个客户端直接挂掉（2026-11-22 实测：灵魂锚一进 READY 就崩）。
		// 这里把 overlay 和 normal 补齐（法线朝上即可，公告板不参与光照）。
		buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
				.color(255, 255, 255, 255)
				.uv(u, v)
				.overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
				.uv2(LightTexture.FULL_BRIGHT)
				.normal(0.0F, 1.0F, 0.0F)
				.endVertex();
	}
}
