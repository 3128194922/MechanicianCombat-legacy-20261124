package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.CombatBackpackItem;
import com.mechaniciancombat.item.CombatBackpackAnimation;
import com.mechaniciancombat.item.BackpackUpgradeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.List;

/** Renders the wearable backpack at an already-transformed chest attachment point. */
public final class CombatBackpackGeoRenderer extends GeoItemRenderer<CombatBackpackItem> {
	/** 跳跃推进器喷口火焰：作者给的 3 帧 16×16 动画贴图。 */
	private static final ResourceLocation FLAME_TEXTURE =
			TkGun.id("textures/entity/combat_backpack_flame.png");
	/** 底模是 48×48 图集，火焰每帧只有 16×16 —— 采样时要放大 3 倍。 */
	private static final float FLAME_UV_SCALE = 3.0F;
	/** 当前这一遍是不是"只画火焰骨骼"的那一遍。 */
	private boolean flamePass;

	private static CombatBackpackGeoRenderer accessoryInstance;
	private static CombatBackpackGeoRenderer rigInstance;
	private static CombatBackpackGeoRenderer previewInstance;
	private static final java.util.Map<Integer, Vec3> FRONT_POSITIONS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3> FRONT_LOCAL_OFFSETS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> FRONT_SAMPLE_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3> REAR_POSITIONS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3> REAR_LOCAL_OFFSETS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> REAR_SAMPLE_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3> SUMMON_LOCATORS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> SUMMON_LOCATOR_TICKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Vec3> CANONICAL_FRONT_OFFSETS =
			new java.util.HashMap<>();
	private static final java.util.Map<Integer, AimFilter> AIM_FILTERS =
			new java.util.HashMap<>();
	private static final double AIM_DEADBAND_COS =
			Math.cos(Math.toRadians(2.0D));
	private static final double AIM_SMOOTHING = 0.25D;
	private LivingEntity currentEntity;
	private boolean suppressC3Cubes;
	private boolean attachedCoreInstalled;
	/** 当前这一遍是不是装了跳跃推进器（决定是否追加火焰 pass / 显示 jet 骨骼）。 */
	private boolean attachedJumpThruster;
	private BackpackUpgradeData.LauncherType attachedLauncherType =
			BackpackUpgradeData.LauncherType.NONE;

	private static final class AimFilter {
		int targetId = -1;
		long tick = -1L;
		Vec3 direction = Vec3.ZERO;
	}

	private CombatBackpackGeoRenderer(GeoModel<CombatBackpackItem> model) {
		super(model);
	}

	public static CombatBackpackGeoRenderer getAccessory() {
		if (accessoryInstance == null) {
			accessoryInstance = new CombatBackpackGeoRenderer(new CombatBackpackGeoModel());
		}
		return accessoryInstance;
	}

	public static CombatBackpackGeoRenderer getRig() {
		if (rigInstance == null) {
			rigInstance = new CombatBackpackGeoRenderer(new CombatBackpackRigGeoModel());
		}
		return rigInstance;
	}

	public static CombatBackpackGeoRenderer getPreview() {
		if (previewInstance == null) {
			previewInstance = new CombatBackpackGeoRenderer(new CombatBackpackGeoModel());
		}
		return previewInstance;
	}

	/**
	 * Draws the selected backpack through GeckoLib's item renderer while forcing
	 * the Maintenance animation. The caller supplies the GUI transform and scissor.
	 */
	public void renderMaintenancePreview(
			ItemStack stack,
			PoseStack matrices,
			MultiBufferSource consumers,
			int light,
			int overlay) {
		if (!(stack.getItem() instanceof CombatBackpackItem)) {
			return;
		}
		this.attachedLauncherType = BackpackUpgradeData.canUseLauncher(stack)
                ? BackpackUpgradeData.launcherType(stack) : BackpackUpgradeData.LauncherType.NONE;
        this.attachedCoreInstalled = BackpackUpgradeData.hasCore(stack);
        this.attachedJumpThruster = BackpackUpgradeData.canUseJumpThruster(stack);
        CombatBackpackAnimation.beginModificationPreview();
		try {
			super.renderByItem(stack, ItemDisplayContext.GUI, matrices, consumers, light, overlay);
		} finally {
			doPostRenderCleanup();
			CombatBackpackAnimation.endModificationPreview();
			this.attachedLauncherType = BackpackUpgradeData.LauncherType.NONE;
			this.attachedCoreInstalled = false;
			this.attachedJumpThruster = false;
		}
	}

	public void renderAttached(ItemStack stack, LivingEntity entity, PoseStack matrices,
							   MultiBufferSource consumers, int light, float tickDelta) {
		if (!(stack.getItem() instanceof CombatBackpackItem item)) {
			return;
		}
		this.currentEntity = entity;
		CombatBackpackAnimation.setRenderingPlayer(entity.getId());
		this.animatable = item;
		this.currentItemStack = stack;
		this.attachedLauncherType = BackpackUpgradeData.canUseLauncher(stack)
				? BackpackUpgradeData.launcherType(stack)
				: BackpackUpgradeData.LauncherType.NONE;
		this.attachedCoreInstalled = BackpackUpgradeData.hasCore(stack);
		this.attachedJumpThruster = BackpackUpgradeData.canUseJumpThruster(stack);
		this.renderPerspective = ItemDisplayContext.NONE;
		try {
			ResourceLocation texture = getTextureLocation(item);
			RenderType layer = getRenderType(item, texture, consumers, tickDelta);
			VertexConsumer buffer = consumers.getBuffer(layer);
			defaultRender(matrices, item, consumers, layer, buffer, tickDelta, tickDelta, light);
			// 第二遍：只画 *_flame 骨骼，换用作者的火焰动画贴图。
			// GeoItemRenderer 会在一遍渲染后清掉这些字段，必须重新赋值。
			if (this.attachedJumpThruster) {
				this.animatable = item;
				this.currentItemStack = stack;
				this.currentEntity = entity;
				this.renderPerspective = ItemDisplayContext.NONE;
				this.flamePass = true;
				RenderType flameLayer = RenderType.entityCutoutNoCull(FLAME_TEXTURE);
				VertexConsumer flameBuffer = consumers.getBuffer(flameLayer);
				defaultRender(matrices, item, consumers, flameLayer, flameBuffer,
						tickDelta, tickDelta, light);
			}
		} finally {
			this.flamePass = false;
			CombatBackpackAnimation.clearRenderingPlayer();
			this.currentEntity = null;
			this.attachedLauncherType = BackpackUpgradeData.LauncherType.NONE;
			this.attachedCoreInstalled = false;
			this.attachedJumpThruster = false;
			doPostRenderCleanup();
		}
	}

	public static Vec3 frontPosition(int playerId) {
		Minecraft client = Minecraft.getInstance();
		net.minecraft.world.entity.Entity entity = client.level == null ? null
				: client.level.getEntity(playerId);
		Vec3 canonical = CANONICAL_FRONT_OFFSETS.get(playerId);
		if (canonical != null && entity != null) {
			return entity.position().add(yRot(canonical, modelYaw(entity)));
		}
		Vec3 exact = FRONT_POSITIONS.get(playerId);
		Long sampleTick = FRONT_SAMPLE_TICKS.get(playerId);
		if (entity != null && exact != null && sampleTick != null
				&& Math.abs(client.level.getGameTime() - sampleTick) <= 2L) {
			return exact;
		}
		Vec3 local = FRONT_LOCAL_OFFSETS.get(playerId);
		if (local != null && entity != null) {
			return entity.position().add(yRot(local, modelYaw(entity)));
		}
		return exact;
	}

	/** Frozen portable-launcher, non-aim origin shared by every launcher state. */
	public static Vec3 canonicalLaunchPosition(int playerId) {
		Minecraft client = Minecraft.getInstance();
		net.minecraft.world.entity.Entity entity = client.level == null ? null
				: client.level.getEntity(playerId);
		Vec3 canonical = CANONICAL_FRONT_OFFSETS.get(playerId);
		if (canonical != null && entity != null) {
			return entity.position().add(yRot(canonical, modelYaw(entity)));
		}
		return frontPosition(playerId);
	}

	public static Vec3 rearPosition(int playerId) {
		Minecraft client = Minecraft.getInstance();
		net.minecraft.world.entity.Entity entity = client.level == null ? null
				: client.level.getEntity(playerId);
		Vec3 exact = REAR_POSITIONS.get(playerId);
		Long sampleTick = REAR_SAMPLE_TICKS.get(playerId);
		if (entity != null && exact != null && sampleTick != null
				&& Math.abs(client.level.getGameTime() - sampleTick) <= 2L) {
			return exact;
		}
		Vec3 local = REAR_LOCAL_OFFSETS.get(playerId);
		if (local != null && entity != null) {
			return entity.position().add(yRot(local, modelYaw(entity)));
		}
		return exact;
	}

	public static Vec3 summonLocatorPosition(int playerId) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		Long tick = SUMMON_LOCATOR_TICKS.get(playerId);
		if (tick == null || Math.abs(client.level.getGameTime() - tick) > 3L) {
			return null;
		}
		return SUMMON_LOCATORS.get(playerId);
	}

	@Override
	public void renderRecursively(PoseStack matrices, CombatBackpackItem animatable, GeoBone bone,
								  RenderType renderType, MultiBufferSource consumers,
								  VertexConsumer buffer, boolean isReRender, float tickDelta,
							  int packedLight, int packedOverlay,
							  float red, float green, float blue, float alpha) {
		boolean coreInstalled = attachedCoreInstalled;
		boolean launcherInstalled = coreInstalled
				&& attachedLauncherType != BackpackUpgradeData.LauncherType.NONE;
		boolean autoLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.AUTO;
		boolean summonLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
		boolean jumpThruster = attachedLauncherType
				== BackpackUpgradeData.LauncherType.JUMP_THRUSTER;
		boolean battleBanner = attachedLauncherType
				== BackpackUpgradeData.LauncherType.BATTLE_BANNER;
		if ((jumpThruster || battleBanner) && currentEntity != null) {
			// 跳跃推进器 / 战旗是作者手绘的状态动画，必须手动采样写骨骼：
			// 依赖物品动画管理器的 tick 归属在本地/远端玩家共用渲染器时不稳定。
			double seconds = CombatBackpackClientState.animationAgeTicks(
					currentEntity.getId(), tickDelta) / 20.0D;
			CombatBackpackPoseAnimation.apply(bone,
					CombatBackpackAnimation.phase(currentEntity.getId()), seconds, jumpThruster);
			CombatBackpackPoseAnimation.applyMissileOverlay(bone,
					CombatBackpackAnimation.missilePhase(currentEntity.getId()),
					CombatBackpackClientState.missileAnimationAgeTicks(
							currentEntity.getId(), tickDelta) / 20.0D);
		}
		Quaternionf aimRotation = null;
		if (currentEntity != null && launcherInstalled
				&& CombatBackpackAnimation.phase(currentEntity.getId())
				== CombatBackpackAnimation.Phase.AIM) {
			if (autoLauncher && "C3".equals(bone.getName())) {
				bone.updateRotation(0.0F, 0.0F, 0.0F);
			} else if ((!autoLauncher && "C3".equals(bone.getName()))
					|| (autoLauncher && "auto_C3".equals(bone.getName()))) {
				aimRotation = calculateAimRotation(matrices, currentEntity, bone);
				if (aimRotation != null) {
					BackpackAimRotation.apply(bone, aimRotation);
				}
			}
		}
		switch (bone.getName()) {
			case "core" -> bone.setHidden(!coreInstalled);
			case "C1", "C2", "C3" -> bone.setHidden(!launcherInstalled);
			case "auto_C1", "auto_C2", "auto_C3" -> bone.setHidden(!autoLauncher);
			case "auto_ejector", "auto_box", "auto_sight", "auto_barrel" ->
					bone.setHidden(!autoLauncher);
			case "summon_C1", "summon_C2", "summon_C3", "summon_locator" ->
					bone.setHidden(!summonLauncher);
			// 跳跃推进器：机体与三处喷口火焰只在装了推进器时显示。
			case "jet_F", "jet_F2", "jet_right", "jet_left" -> bone.setHidden(!jumpThruster);
			case "jet_F2_flame", "jet_right_flame", "jet_left_flame" ->
					bone.setHidden(!jumpThruster);
			default -> {
			}
		}
		if (aimRotation != null) {
			renderC3Independently(matrices, bone, buffer, packedLight, packedOverlay,
					red, green, blue, alpha, aimRotation);
			boolean previousSuppress = suppressC3Cubes;
			suppressC3Cubes = true;
			try {
				super.renderRecursively(matrices, animatable, bone, renderType, consumers,
						buffer, isReRender, tickDelta, packedLight, packedOverlay,
						red, green, blue, alpha);
			} finally {
				suppressC3Cubes = previousSuppress;
			}
		} else {
			super.renderRecursively(matrices, animatable, bone, renderType, consumers, buffer,
					isReRender, tickDelta, packedLight, packedOverlay,
					red, green, blue, alpha);
		}
		if (currentEntity != null) {
			recordLocator(matrices, bone, currentEntity, autoLauncher);
			if ("summon_locator".equals(bone.getName())) {
				Vector3d position = bone.getWorldPosition();
				SUMMON_LOCATORS.put(currentEntity.getId(),
						new Vec3(position.x, position.y, position.z));
				SUMMON_LOCATOR_TICKS.put(currentEntity.getId(),
						currentEntity.level().getGameTime());
			}
		}
	}

	@Override
	public void renderCubesOfBone(PoseStack matrices, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay,
								  float red, float green, float blue, float alpha) {
		// 火焰骨骼只在火焰 pass 画，机体骨骼只在机体 pass 画 —— 两遍互不重复。
		boolean flameBone = bone.getName().endsWith("_flame");
		if (flamePass != flameBone) {
			return;
		}
		boolean autoLauncher = attachedLauncherType
				== BackpackUpgradeData.LauncherType.AUTO;
		boolean summonLauncher = attachedLauncherType == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA;
		if (!BackpackBoneVisibility.draw(bone.getName(), autoLauncher, summonLauncher, suppressC3Cubes)) {
			return;
		}
		super.renderCubesOfBone(matrices, bone, buffer, packedLight, packedOverlay,
				red, green, blue, alpha);
	}

	@Override
	public void renderCube(PoseStack matrices, GeoCube cube, VertexConsumer buffer,
						   int packedLight, int packedOverlay,
						   float red, float green, float blue, float alpha) {
		if (flamePass) {
			// 火焰贴图是 16×16 单帧，底模图集是 48×48 —— UV 放大 3 倍才落在正确区域，
			// 并且用全亮，避免夜里喷口火焰被压暗。
			super.renderCube(matrices, scaleFlameUvs(cube), buffer,
					net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, packedOverlay,
					red, green, blue, alpha);
			return;
		}
		super.renderCube(matrices, cube, buffer, packedLight, packedOverlay,
				red, green, blue, alpha);
	}

	@Override
	public void updateAnimatedTextureFrame(CombatBackpackItem animatable) {
		if (flamePass) {
			// GeckoLib 才持有动画贴图的当前帧；普通 entity RenderType 只会绑第 0 帧。
			software.bernie.geckolib.cache.texture.AnimatableTexture.setAndUpdate(FLAME_TEXTURE);
			return;
		}
		super.updateAnimatedTextureFrame(animatable);
	}

	private static GeoCube scaleFlameUvs(GeoCube cube) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] scaled = new GeoQuad[source.length];
		for (int i = 0; i < source.length; i++) {
			GeoQuad quad = source[i];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			GeoVertex[] updated = new GeoVertex[vertices.length];
			for (int j = 0; j < vertices.length; j++) {
				GeoVertex vertex = vertices[j];
				updated[j] = vertex.withUVs(vertex.texU() * FLAME_UV_SCALE,
						vertex.texV() * FLAME_UV_SCALE);
			}
			scaled[i] = new GeoQuad(updated, quad.normal(), quad.direction());
		}
		return new GeoCube(scaled, cube.pivot(), cube.rotation(), cube.size(),
				cube.inflate(), cube.mirror());
	}

	private void renderC3Independently(PoseStack matrices, GeoBone bone,
									   VertexConsumer buffer, int packedLight,
										   int packedOverlay, float red, float green,
										   float blue, float alpha,
									   Quaternionf rotation) {
		matrices.pushPose();
		try {
			RenderUtils.translateMatrixToBone(matrices, bone);
			RenderUtils.translateToPivotPoint(matrices, bone);
			matrices.mulPose(rotation);
			RenderUtils.scaleMatrixForBone(matrices, bone);
			RenderUtils.translateAwayFromPivotPoint(matrices, bone);
			renderCubesOfBone(matrices, bone, buffer, packedLight, packedOverlay,
					red, green, blue, alpha);
		} finally {
			matrices.popPose();
		}
	}

	private static Quaternionf calculateAimRotation(PoseStack matrices, LivingEntity entity,
													 GeoBone c3) {
		int targetId = CombatBackpackClientState.targetEntityId(entity.getId());
		if (targetId < 0) {
			AIM_FILTERS.remove(entity.getId());
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		net.minecraft.world.entity.Entity targetEntity = client.level == null ? null
				: client.level.getEntity(targetId);
		if (targetEntity == null) {
			AIM_FILTERS.remove(entity.getId());
			return null;
		}
		Vec3 targetWorld = targetEntity.getBoundingBox().getCenter();
		// r306: filter a launcher-independent world direction, so turret motion
		// cannot feed back into the next smoothing sample.
		Vec3 aimOrigin = entity.getBoundingBox().getCenter();
		Vec3 aimAxis = targetWorld.subtract(aimOrigin);
		if (aimAxis.lengthSqr() < 1.0E-8D) {
			return null;
		}
		// Shared by C3 and auto_C3, covering every launcher using this renderer.
		Vec3 filteredDirection = filterAimDirection(
				entity.getId(), targetId, aimAxis.normalize(),
				entity.level().getGameTime());

		String frontName = "auto_C3".equals(c3.getName()) ? "auto_front" : "front";
		String rearName = frontName.equals("auto_front") ? "auto_rear" : "rear";
		GeoBone front = findBoneRecursive(c3, frontName);
		GeoBone rear = findBoneRecursive(c3, rearName);
		Vector3f localAxis = front != null && rear != null
				? new Vector3f(
						front.getPivotX() - rear.getPivotX(),
						front.getPivotY() - rear.getPivotY(),
						front.getPivotZ() - rear.getPivotZ()).normalize()
				: new Vector3f(0.0F, 0.0F, -1.0F);
		Matrix3f parentNormal = new Matrix3f(matrices.last().normal());
		Vector3f targetInParent = parentNormal.invert().transform(
				new Vector3f((float) filteredDirection.x,
						(float) filteredDirection.y, (float) filteredDirection.z))
				.normalize();
		return new Quaternionf().rotationTo(localAxis, targetInParent);
	}

	private static Vec3 filterAimDirection(int playerId, int targetId,
											 Vec3 current, long tick) {
		AimFilter filter = AIM_FILTERS.computeIfAbsent(playerId,
				ignored -> new AimFilter());
		if (filter.targetId != targetId) {
			filter.targetId = targetId;
			filter.tick = tick;
			filter.direction = current;
			return current;
		}
		if (filter.tick == tick) {
			return filter.direction;
		}
		filter.tick = tick;
		if (filter.direction.lengthSqr() < 1.0E-8D
				|| filter.direction.dot(current) >= AIM_DEADBAND_COS) {
			return filter.direction;
		}
		filter.direction = filter.direction.scale(1.0D - AIM_SMOOTHING)
				.add(current.scale(AIM_SMOOTHING))
				.normalize();
		return filter.direction;
	}

	private static GeoBone findBoneRecursive(GeoBone root, String name) {
		if (root == null) {
			return null;
		}
		if (name.equals(root.getName())) {
			return root;
		}
		for (GeoBone child : root.getChildBones()) {
			GeoBone found = findBoneRecursive(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private void recordLocator(PoseStack matrices, GeoBone bone, LivingEntity entity,
			boolean autoLauncher) {
		String boneName = bone.getName();
		if (!boneName.equals("front") && !boneName.equals("rear")) {
			return;
		}
		matrices.pushPose();
		Vector3f renderedPosition;
		try {
			RenderUtils.prepMatrixForBone(matrices, bone);
			Vector4f point = matrices.last().pose()
					.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
			renderedPosition = new Vector3f(point.x, point.y, point.z);
		} finally {
			matrices.popPose();
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 renderSpace = new Vec3(
				renderedPosition.x, renderedPosition.y, renderedPosition.z);
		Vec3 worldPos = chooseLocatorWorldPosition(entity, renderSpace, camera);
		Vec3 delta = worldPos.subtract(entity.position());
		Vec3 local = yRot(delta, -entity.yBodyRot);
		if ("front".equals(boneName)) {
			FRONT_POSITIONS.put(entity.getId(), worldPos);
			FRONT_LOCAL_OFFSETS.put(entity.getId(), local);
			FRONT_SAMPLE_TICKS.put(entity.getId(), entity.level().getGameTime());
			if (autoLauncher || CombatBackpackAnimation.phase(entity.getId())
					== CombatBackpackAnimation.Phase.READY) {
				CANONICAL_FRONT_OFFSETS.put(entity.getId(), local);
			}
		} else {
			REAR_POSITIONS.put(entity.getId(), worldPos);
			REAR_LOCAL_OFFSETS.put(entity.getId(), local);
			REAR_SAMPLE_TICKS.put(entity.getId(), entity.level().getGameTime());
		}
	}

	private static Vec3 chooseLocatorWorldPosition(LivingEntity entity,
			Vec3 renderSpace, Vec3 camera) {
		Vec3 entityAnchor = entity.position().add(0.0D, 1.0D, 0.0D);
		Vec3 cameraRelative = renderSpace.add(camera);
		Vec3 worldAbsolute = renderSpace;
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Vec3 candidate : List.of(worldAbsolute, cameraRelative)) {
			if (!Double.isFinite(candidate.x) || !Double.isFinite(candidate.y)
					|| !Double.isFinite(candidate.z)) {
				continue;
			}
			double distance = candidate.distanceToSqr(entityAnchor);
			if (distance <= 16.0D && distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best == null ? cameraRelative : best;
	}

	private static Vec3 yRot(Vec3 value, float degrees) {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(value.x * cos - value.z * sin, value.y,
				value.x * sin + value.z * cos);
	}

	private static float modelYaw(net.minecraft.world.entity.Entity entity) {
		return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
	}

	@Override
	public void preRender(PoseStack matrices, CombatBackpackItem animatable, BakedGeoModel model,
						  MultiBufferSource consumers, VertexConsumer buffer, boolean isReRender,
						  float tickDelta, int light, int overlay,
						  float red, float green, float blue, float alpha) {
		this.itemRenderTranslations = new Matrix4f(matrices.last().pose());
	}
}
