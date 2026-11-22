package com.mechaniciancombat.client;

import com.mechaniciancombat.item.WarBannerFpaItem;
import com.mechaniciancombat.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 战旗施法期间的第一人称相机空间渲染。
 *
 * <p>作者单独导出了 `war_banner_fpa`：模型本来就是按玩家模型坐标画的，
 * 所以这里只补"头部枢轴 → 相机"的平移，不吃普通物品渲染器那套 +0.5 居中偏移。
 * `flag` 骨骼每帧把世界坐标报给 {@link WarBannerSkillClientState}，服务端据此决定落点。
 */
public final class WarBannerFirstPersonRenderer extends GeoItemRenderer<WarBannerFpaItem> {
	/** 第一人称作者坐标的相机高度 = 玩家头部枢轴 y=24px。 */
	private static final float CAMERA_HEAD_HEIGHT = 24.0F / 16.0F;
	private static WarBannerFirstPersonRenderer instance;
	private static LocalPlayer activePlayer;
	private static long lastStartedAt = -1L;
	private static double seconds;

	private WarBannerFirstPersonRenderer(WarBannerFpaItem item) {
		super(new WarBannerFpaGeoModel());
		this.animatable = item;
	}

	public static void render(LocalPlayer player, float tickDelta, PoseStack poseStack,
							  MultiBufferSource.BufferSource buffers, int light) {
		if (!(ModRegistries.WAR_BANNER_FPA_MODEL.get() instanceof WarBannerFpaItem item)) {
			return;
		}
		if (instance == null) {
			instance = new WarBannerFirstPersonRenderer(item);
		}
		ItemStack stack = new ItemStack(item);
		// GeoItemRenderer#getInstanceId 走 currentItemStack；先赋值再复位控制器，
		// 否则第一帧可能在 currentItemStack 还是 null 时抛 NPE。
		instance.currentItemStack = stack;
		long startedAt = WarBannerSkillClientState.startedAt(player.getId());
		if (startedAt != lastStartedAt) {
			lastStartedAt = startedAt;
			var manager = item.getAnimatableInstanceCache()
					.getManagerForId(instance.getInstanceId(item));
			var controller = manager.getAnimationControllers().get("fpa");
			if (controller != null) {
				controller.forceAnimationReset();
			}
			manager.clearSnapshotCache();
		}
		activePlayer = player;
		seconds = WarBannerSkillClientState.animationSeconds(
				player.getId(), player.level().getGameTime(), tickDelta);
		try {
			instance.renderByItem(stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
					poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			buffers.endBatch();
		} finally {
			activePlayer = null;
		}
	}

	@Override
	public void preRender(PoseStack poseStack, WarBannerFpaItem animatable, BakedGeoModel model,
						  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
						  float partialTick, int packedLight, int packedOverlay,
						  float red, float green, float blue, float alpha) {
		// renderByItem 会额外加 +0.5/+0.51/+0.5 的居中偏移；FPA 文件已经是玩家模型坐标，
		// 这里只保留头部枢轴到相机的平移。
		itemRenderTranslations = new Matrix4f(poseStack.last().pose());
		scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model,
				isReRender, partialTick, packedLight, packedOverlay);
		poseStack.translate(0.0F, -CAMERA_HEAD_HEIGHT, 0.0F);
	}

	@Override
	public void renderRecursively(PoseStack poseStack, WarBannerFpaItem animatable, GeoBone bone,
								  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
								  boolean isReRender, float partialTick, int packedLight,
								  int packedOverlay, float red, float green, float blue, float alpha) {
		if (activePlayer != null) {
			// 控制器负责推进时间，但最终骨骼矩阵仍强制写入转换后的 FPA 通道，
			// 避免物品渲染器丢掉 position/scale 通道、只剩旋转。
			WarBannerHeldPose.applyFirstPerson(bone, seconds);
			if ("flag".equals(bone.getName())) {
				Vector3d local = bone.getWorldPosition();
				Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
				WarBannerSkillClientState.updateLocator(activePlayer.getId(),
						camera.add(local.x, local.y, local.z));
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
		if ("flag".equals(bone.getName())) {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay,
					red, green, blue, alpha);
		}
	}
}
