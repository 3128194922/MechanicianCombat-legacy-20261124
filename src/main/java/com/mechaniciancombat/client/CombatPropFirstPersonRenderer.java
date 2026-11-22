package com.mechaniciancombat.client;

import com.mechaniciancombat.item.CombatPropFpaItem;
import com.mechaniciancombat.item.CombatPropFpaRenderState;
import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.registry.ModRegistries;
import com.mechaniciancombat.net.CombatBackpackStatusPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 猎矛部署期间的第一人称相机空间道具。
 *
 * <p>作者的 `hunter_anchor_fpa` 只画 `spear` 一根骨骼（身体骨骼是给定位/对齐用的空骨）。
 * 这里沿用灵魂锚第一人称渲染的同一套"只补头部枢轴平移"的相机空间策略。
 */
public final class CombatPropFirstPersonRenderer extends GeoItemRenderer<CombatPropFpaItem> {
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static CombatPropFirstPersonRenderer hunterAnchor;
	private static CombatPropFirstPersonRenderer portableTurret;
	private static CombatPropFirstPersonRenderer autoTurret;
	private static LocalPlayer activePlayer;
	private static long lastStartedAt = -1L;
	private static double seconds;
	/** 当前渲染的炮台资源名（`portable_turret_fpa` / `auto_turret_fpa`）。 */
	private static String currentTurretAsset = "portable_turret_fpa";

	private CombatPropFirstPersonRenderer(CombatPropFpaItem item, String assetName) {
		super(new CombatPropFpaGeoModel(assetName));
		this.animatable = item;
	}

	public static boolean renderHunterAnchor(LocalPlayer player, float tickDelta, PoseStack poseStack,
											 MultiBufferSource.BufferSource buffers, int light) {
		if (!HunterSpearSkillClientState.isActive(player.getId())) {
			return false;
		}
		if (!(ModRegistries.HUNTER_ANCHOR_FPA_MODEL.get() instanceof CombatPropFpaItem item)) {
			return false;
		}
		if (hunterAnchor == null) {
			hunterAnchor = new CombatPropFirstPersonRenderer(item, "hunter_anchor_fpa");
		}
		ItemStack stack = new ItemStack(item);
		hunterAnchor.currentItemStack = stack;
		CombatPropFpaRenderState.set(CombatPropFpaRenderState.Mode.HUNTER_ANCHOR);
		long startedAt = HunterSpearSkillClientState.startedAt(player.getId());
		if (startedAt != lastStartedAt) {
			lastStartedAt = startedAt;
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(hunterAnchor.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		activePlayer = player;
		seconds = HunterSpearSkillClientState.animationSeconds(
				player.getId(), player.level().getGameTime(), tickDelta);
		try {
			hunterAnchor.renderByItem(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
					poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			buffers.endBatch();
		} finally {
			activePlayer = null;
			CombatPropFpaRenderState.set(CombatPropFpaRenderState.Mode.NONE);
		}
		return true;
	}

	/**
	 * 背包炮台的第一人称道具（便携 / 自动两种）。
	 *
	 * <p>只在背包炮台激活时画；动画按 `ready / aim / CD` 三档切换。
	 */
	public static boolean renderTurret(LocalPlayer player, float tickDelta, PoseStack poseStack,
									   MultiBufferSource.BufferSource buffers, int light) {
		BackpackUpgradeData.LauncherType type = CombatBackpackClientState.launcherType(player.getId());
		boolean portable = type == BackpackUpgradeData.LauncherType.PORTABLE;
		boolean auto = type == BackpackUpgradeData.LauncherType.AUTO;
		if (!portable && !auto) {
			return false;
		}
		// 只有炮台真正展开之后才显示；否则装着模块时会一直举着一门炮。
		if (!CombatBackpackClientState.turretActivated(player.getId())) {
			return false;
		}
		CombatPropFpaItem item = (portable
				? ModRegistries.PORTABLE_TURRET_FPA_MODEL.get()
				: ModRegistries.AUTO_TURRET_FPA_MODEL.get()) instanceof CombatPropFpaItem prop ? prop : null;
		if (item == null) {
			return false;
		}
		CombatPropFpaRenderState.Mode mode = turretMode(player.getId());
		if (mode == CombatPropFpaRenderState.Mode.NONE) {
			return false;
		}
		CombatPropFirstPersonRenderer renderer;
		if (portable) {
			if (portableTurret == null) {
				portableTurret = new CombatPropFirstPersonRenderer(item, "portable_turret_fpa");
			}
			renderer = portableTurret;
			currentTurretAsset = "portable_turret_fpa";
		} else {
			if (autoTurret == null) {
				autoTurret = new CombatPropFirstPersonRenderer(item, "auto_turret_fpa");
			}
			renderer = autoTurret;
			currentTurretAsset = "auto_turret_fpa";
		}
		ItemStack stack = new ItemStack(item);
		renderer.currentItemStack = stack;
		CombatPropFpaRenderState.set(mode);
		activePlayer = player;
		// 炮台自己的时钟：READY 从"展开那一刻"算，CD 从"停机那一刻"算，AIM 常驻循环。
		int startedAt = mode == CombatPropFpaRenderState.Mode.CD
				? CombatBackpackClientState.turretCdStartedAt(player.getId())
				: CombatBackpackClientState.turretActivationAt(player.getId());
		if (startedAt < 0) {
			seconds = 0.0D;
		} else {
			seconds = Math.max(0.0D,
					CombatBackpackClientState.clientTicks() - startedAt + tickDelta) / 20.0D;
		}
		try {
			renderer.renderByItem(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
					poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			buffers.endBatch();
		} finally {
			activePlayer = null;
			CombatPropFpaRenderState.set(CombatPropFpaRenderState.Mode.NONE);
		}
		return true;
	}

	static String currentTurretAsset() {
		return currentTurretAsset;
	}

	private static CombatPropFpaRenderState.Mode turretMode(int playerId) {
		if (CombatBackpackClientState.turretCdLatched(playerId)) {
			return CombatPropFpaRenderState.Mode.CD;
		}
		return switch (CombatBackpackClientState.statusPhase(playerId)) {
			case CombatBackpackStatusPayload.PHASE_COOLDOWN -> CombatPropFpaRenderState.Mode.CD;
			case CombatBackpackStatusPayload.PHASE_MARKED,
					CombatBackpackStatusPayload.PHASE_AUTO_ACTIVE,
					CombatBackpackStatusPayload.PHASE_AUTO_PAUSED -> CombatPropFpaRenderState.Mode.AIM;
			default -> CombatPropFpaRenderState.Mode.READY;
		};
	}

	@Override
	public void preRender(PoseStack poseStack, CombatPropFpaItem animatable, BakedGeoModel model,
						  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
						  float partialTick, int packedLight, int packedOverlay,
						  float red, float green, float blue, float alpha) {
		itemRenderTranslations = new Matrix4f(poseStack.last().pose());
		scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model,
				isReRender, partialTick, packedLight, packedOverlay);
		poseStack.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderRecursively(PoseStack poseStack, CombatPropFpaItem animatable, GeoBone bone,
								  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
								  boolean isReRender, float partialTick, int packedLight,
								  int packedOverlay, float red, float green, float blue, float alpha) {
		if (activePlayer != null) {
			CombatPropFpaRenderState.Mode mode = CombatPropFpaRenderState.mode();
			if (mode == CombatPropFpaRenderState.Mode.HUNTER_ANCHOR) {
				WarBannerHeldPose.applyHunterFirstPerson(bone, seconds);
			} else {
				WarBannerHeldPose.applyTurretFirstPerson(bone, seconds, mode);
			}
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
				isReRender, partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
				red, green, blue, alpha);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay,
								  float red, float green, float blue, float alpha) {
		CombatPropFpaRenderState.Mode mode = CombatPropFpaRenderState.mode();
		boolean visible = mode == CombatPropFpaRenderState.Mode.HUNTER_ANCHOR
				? "spear".equals(bone.getName())
				: "C3".equals(bone.getName());
		if (visible) {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
					red, green, blue, alpha);
		}
	}
}
