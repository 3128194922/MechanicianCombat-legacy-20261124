package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.rig.BowgunRigRenderer;
import com.mechaniciancombat.entity.SoulAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 连接施法者与锚尾的世界空间锁链带。 */
public final class SoulAnchorRopeRenderer {
	private static final ResourceLocation HOLD_TEXTURE =
			TkGun.id("textures/entity/connect_chain.png");
	private static final ResourceLocation[] FLIGHT_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_flight_0.png"),
			TkGun.id("textures/entity/soul_anchor_flight_1.png"),
			TkGun.id("textures/entity/soul_anchor_flight_2.png"),
			TkGun.id("textures/entity/soul_anchor_flight_3.png")
	};
	private static final ResourceLocation[] THROW_END_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_hit_0.png"),
			TkGun.id("textures/entity/soul_anchor_hit_1.png"),
			TkGun.id("textures/entity/soul_anchor_hit_2.png")
	};
	private static final ResourceLocation[] HIT_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_3_0.png"),
			TkGun.id("textures/entity/soul_anchor_3_1.png"),
			TkGun.id("textures/entity/soul_anchor_3_2.png"),
			TkGun.id("textures/entity/soul_anchor_3_3.png"),
			TkGun.id("textures/entity/soul_anchor_3_4.png")
	};
	private static final Map<Integer, Long> HIT_STARTED_AT = new HashMap<>();
	private static final Set<Integer> STALL_TRANSITIONED_ANCHORS = new HashSet<>();
	private static final double GIF_TEXTURE_BLOCKS = 3.0D;
	private static final double IMPACT_TEXTURE_BLOCKS = 2.0D;
	private static final double CHAIN_TEXTURE_BLOCKS = 1.0D;
	private static final int FLIGHT_FRAME_TICKS = 2;
	private static final int THROW_END_FRAME_TICKS = 2;
	private static final int FLIGHT_CYCLES = 1;
	private static final int HIT_TRANSITION_TICKS = 6;

	private SoulAnchorRopeRenderer() {
	}

	public static void register() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SoulAnchorRopeRenderer::onRenderLevel);
	}

	public static void clear() {
		HIT_STARTED_AT.clear();
		STALL_TRANSITIONED_ANCHORS.clear();
	}

	private static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Vec3 camera = event.getCamera().getPosition();
		long now = client.level.getGameTime();
		float tickDelta = event.getPartialTick();
		AABB search = new AABB(camera, camera).inflate(192.0D);
		Set<Integer> visibleAnchorIds = new HashSet<>();
		MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
		for (SoulAnchorEntity anchor : client.level.getEntitiesOfClass(SoulAnchorEntity.class,
				search, entity -> !entity.isRemoved())) {
			visibleAnchorIds.add(anchor.getId());
			Entity owner = anchor.trackedOwner();
			if (owner == null || !owner.isAlive()) {
				HIT_STARTED_AT.remove(anchor.getId());
				continue;
			}
			Vec3 ownerPos = owner.getPosition(tickDelta);
			Vec3 start = SoulAnchorSkillClientState.active(owner.getId())
					? BowgunRigRenderer.armLeftPosition(owner.getId()) : null;
			if (start == null) {
				start = ownerPos.add(0.0D, owner.getBbHeight() * 0.62D, 0.0D);
			}
			Vec3 end = anchor.getPosition(tickDelta);
			ResourceLocation texture = textureFor(anchor, owner.getId(), now);
			double textureBlocks = textureBlocks(texture);
			drawRibbon(event.getPoseStack(),
					buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)),
					start.subtract(camera), end.subtract(camera), textureBlocks);
		}
		HIT_STARTED_AT.keySet().removeIf(id -> !visibleAnchorIds.contains(id));
		STALL_TRANSITIONED_ANCHORS.removeIf(id -> !visibleAnchorIds.contains(id));
	}

	private static ResourceLocation textureFor(SoulAnchorEntity anchor, int ownerId, long now) {
		if (SoulAnchorSkillClientState.anchorStall(ownerId)) {
			STALL_TRANSITIONED_ANCHORS.add(anchor.getId());
			long started = HIT_STARTED_AT.computeIfAbsent(anchor.getId(), ignored -> now);
			return hitTransitionAtElapsed(Math.max(0L, now - started));
		}
		if (STALL_TRANSITIONED_ANCHORS.contains(anchor.getId())) {
			long started = HIT_STARTED_AT.getOrDefault(anchor.getId(), now);
			return hitTransitionAtElapsed(Math.max(0L, now - started));
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.FLYING) {
			return throwTransitionAtElapsed(SoulAnchorSkillClientState.elapsedTicks(ownerId));
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.RETRACTING
				&& anchor.hookedEntity() == null) {
			return HIT_STARTED_AT.containsKey(anchor.getId())
					? impactTransitionOrHold(anchor, now) : HOLD_TEXTURE;
		}
		return impactTransitionOrHold(anchor, now);
	}

	private static ResourceLocation impactTransitionOrHold(SoulAnchorEntity anchor, long now) {
		long started = HIT_STARTED_AT.computeIfAbsent(anchor.getId(), ignored -> now);
		return hitTransitionAtElapsed(Math.max(0L, now - started));
	}

	private static ResourceLocation throwTransitionAtElapsed(long elapsed) {
		if (elapsed == Long.MAX_VALUE) {
			return HOLD_TEXTURE;
		}
		int cycleLength = FLIGHT_TEXTURES.length * FLIGHT_FRAME_TICKS;
		int flightLength = cycleLength * FLIGHT_CYCLES;
		if (elapsed < flightLength) {
			return FLIGHT_TEXTURES[(int) ((elapsed / FLIGHT_FRAME_TICKS) % FLIGHT_TEXTURES.length)];
		}
		long endElapsed = elapsed - flightLength;
		int endLength = THROW_END_TEXTURES.length * THROW_END_FRAME_TICKS;
		if (endElapsed < endLength) {
			return THROW_END_TEXTURES[(int) Math.min(THROW_END_TEXTURES.length - 1,
					endElapsed / THROW_END_FRAME_TICKS)];
		}
		return HOLD_TEXTURE;
	}

	private static ResourceLocation hitTransitionAtElapsed(long elapsed) {
		if (elapsed < HIT_TRANSITION_TICKS) {
			int frame = (int) Math.min(HIT_TEXTURES.length - 1,
					elapsed * HIT_TEXTURES.length / HIT_TRANSITION_TICKS);
			return HIT_TEXTURES[frame];
		}
		return HOLD_TEXTURE;
	}

	private static double textureBlocks(ResourceLocation texture) {
		if (texture == HOLD_TEXTURE) {
			return CHAIN_TEXTURE_BLOCKS;
		}
		for (ResourceLocation impact : HIT_TEXTURES) {
			if (texture == impact) {
				return IMPACT_TEXTURE_BLOCKS;
			}
		}
		return GIF_TEXTURE_BLOCKS;
	}

	private static void drawRibbon(PoseStack poseStack, VertexConsumer buffer,
								   Vec3 start, Vec3 end, double textureBlocks) {
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
		double halfWidth = textureBlocks * 0.5D;
		Vec3 side = direction.cross(view).normalize().scale(halfWidth);
		if (side.lengthSqr() < 1.0E-6D) {
			side = new Vec3(halfWidth, 0.0D, 0.0D);
		}
		int segments = Math.max(1, (int) Math.ceil(length / textureBlocks));
		Matrix4f matrix = poseStack.last().pose();
		PoseStack.Pose entry = poseStack.last();
		for (int index = 0; index < segments; index++) {
			double offset0 = index * textureBlocks;
			double offset1 = Math.min(length, offset0 + textureBlocks);
			double t0 = offset0 / length;
			double t1 = offset1 / length;
			float v1 = (float) Math.min(1.0D, (offset1 - offset0) / textureBlocks);
			Vec3 p0 = start.add(delta.scale(t0));
			Vec3 p1 = start.add(delta.scale(t1));
			vertex(matrix, entry, buffer, p0.subtract(side), 0.0F, 0.0F);
			vertex(matrix, entry, buffer, p0.add(side), 1.0F, 0.0F);
			vertex(matrix, entry, buffer, p1.add(side), 1.0F, v1);
			vertex(matrix, entry, buffer, p1.subtract(side), 0.0F, v1);
		}
	}

	private static void vertex(Matrix4f matrix, PoseStack.Pose entry, VertexConsumer buffer,
							   Vec3 point, float u, float v) {
		buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
				.color(1.0F, 1.0F, 1.0F, 1.0F)
				.uv(u, v)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(LightTexture.FULL_BRIGHT)
				.normal(entry.normal(), 0.0F, 1.0F, 0.0F)
				.endVertex();
	}
}
