package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.GunClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.Set;

/**
 * 枢德之裁近战特效平面（作者在 close_combat.bbmodel 里做的 1_1..2_4）。
 *
 * <p>这些骨骼的 UV 指向 `close_combat_effect-sheet.png` / `close_combat_effect2.png`，
 * **不能**用枪贴图那一层画（会糊成乱码），所以由本层接管，并让
 * {@link BowgunGunLayer} 跳过它们。
 *
 * <p>可见性有两道闸：动画里 step-held 的 scale 关键帧，以及按客户端权威近战时钟
 * （{@code GunClientState.elapsed}，命中冻结时暂停）算出的时间窗。
 */
public final class BowgunCloseCombatEffectLayer extends GeoRenderLayer<BowgunRig> {
	private static final Set<String> EFFECT_BONES = Set.of(
			"effect_1", "1_1", "1_2", "1_3", "1_4",
			"effect_2", "2_1", "2_2", "2_3", "2_4");
	private static final Set<String> EFFECT_ANIMATIONS = Set.of(
			"ATK_1", "ATK_1_upper", "ATK_2", "ATK_4", "ATK_hold_over");
	private static final ResourceLocation TEXTURE =
			TkGun.id("textures/entity/close_combat_effect-sheet.png");
	private static final ResourceLocation TEXTURE_2 =
			TkGun.id("textures/entity/close_combat_effect2.png");
	private static final Map<String, ResourceLocation> BONE_TEXTURES = Map.of(
			"1_1", TEXTURE, "1_2", TEXTURE, "1_3", TEXTURE, "1_4", TEXTURE,
			"2_1", TEXTURE_2, "2_2", TEXTURE_2, "2_3", TEXTURE_2, "2_4", TEXTURE_2);
	private static final Map<String, EffectWindow> EFFECT_WINDOWS = Map.ofEntries(
			entry("ATK_1", "1_1", 0.25F, 0.29167F),
			entry("ATK_1", "1_2", 0.29167F, 0.33333F),
			entry("ATK_1", "1_3", 0.33333F, 0.375F),
			entry("ATK_1", "1_4", 0.375F, 0.41667F),
			entry("ATK_2", "2_1", 0.375F, 0.41667F),
			entry("ATK_2", "2_2", 0.41667F, 0.45833F),
			entry("ATK_2", "2_3", 0.5F, 0.54167F),
			entry("ATK_2", "2_4", 0.58333F, 0.625F),
			entry("ATK_2", "1_1", 0.41667F, 0.45833F),
			entry("ATK_2", "1_2", 0.45833F, 0.5F),
			entry("ATK_2", "1_3", 0.5F, 0.54167F),
			entry("ATK_2", "1_4", 0.54167F, 0.58333F),
			entry("ATK_4", "1_1", 0.66667F, 0.70833F),
			entry("ATK_4", "1_2", 0.70833F, 0.75F),
			entry("ATK_4", "1_3", 0.75F, 0.79167F),
			entry("ATK_4", "1_4", 0.79167F, 0.83333F),
			entry("ATK_4", "2_1", 0.375F, 0.41667F),
			entry("ATK_4", "2_2", 0.41667F, 0.45833F),
			entry("ATK_4", "2_3", 0.5F, 0.54167F),
			entry("ATK_4", "2_4", 0.58333F, 0.625F),
			entry("ATK_hold_over", "2_1", 0.25F, 0.29167F),
			entry("ATK_hold_over", "2_2", 0.29167F, 0.33333F),
			entry("ATK_hold_over", "2_3", 0.375F, 0.41667F),
			entry("ATK_hold_over", "2_4", 0.45833F, 0.5F),
			entry("ATK_hold_over", "1_1", 0.70833F, 0.75F),
			entry("ATK_hold_over", "1_2", 0.75F, 0.79167F),
			entry("ATK_hold_over", "1_3", 0.83333F, 0.875F),
			entry("ATK_hold_over", "1_4", 0.91667F, 0.95833F));
	private static final float VISIBLE_EPSILON = 1.0E-4F;

	public BowgunCloseCombatEffectLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	/** 这根骨骼是不是近战特效平面（枪贴图层据此跳过）。 */
	public static boolean isEffectBone(String name) {
		return EFFECT_BONES.contains(name);
	}

	/**
	 * 按客户端近战时钟把特效平面开关写进骨骼 scale。
	 * 命中冻结会暂停 `elapsed`，所以同一段时间窗会接着播完，而不是被跳过。
	 */
	public static void applyScheduledVisibility(GeoBone bone, Entity entity, float partialTick) {
		if (entity == null || !isEffectBone(bone.getName())) {
			return;
		}
		GunClientState.Entry entry = GunClientState.get(entity.getId());
		if (entry == null || entry.state != TkGun.State.MELEE || entry.meleeReturning) {
			setScale(bone, 0.0F);
			return;
		}
		String animation = normalizedEffectAnimation();
		double seconds = (entry.elapsed + partialTick) / 20.0D;
		String boneName = bone.getName();
		if ("effect_1".equals(boneName) || "effect_2".equals(boneName)) {
			String prefix = "effect_1".equals(boneName) ? "1_" : "2_";
			boolean visible = false;
			for (Map.Entry<String, EffectWindow> window : EFFECT_WINDOWS.entrySet()) {
				if (!window.getKey().startsWith(animation + "/" + prefix)) continue;
				EffectWindow value = window.getValue();
				if (seconds + 1.0E-4D >= value.start() && seconds < value.end() - 1.0E-4D) {
					visible = true;
					break;
				}
			}
			setScale(bone, visible ? 1.0F : 0.0F);
			return;
		}
		EffectWindow window = EFFECT_WINDOWS.get(animation + "/" + boneName);
		boolean visible = window != null
				&& seconds + 1.0E-4D >= window.start()
				&& seconds < window.end() - 1.0E-4D;
		setScale(bone, visible ? 1.0F : 0.0F);
	}

	@Override
	public void renderForBone(PoseStack poseStack, BowgunRig animatable, GeoBone bone, RenderType type,
							  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
							  int packedLight, int packedOverlay) {
		ResourceLocation texture = BONE_TEXTURES.get(bone.getName());
		if (texture == null || bone.getCubes().isEmpty() || !isEffectAnimationActive()
				|| !isScaleVisible(bone)) {
			return;
		}
		VertexConsumer effectBuffer = bufferSource.getBuffer(
				RenderType.entityTranslucentEmissive(texture, false));
		for (GeoCube cube : bone.getCubes()) {
			poseStack.pushPose();
			renderUnshadedCube(poseStack, cube, effectBuffer, packedOverlay);
			poseStack.popPose();
		}
	}

	/**
	 * 自发光 shader 仍会乘光照；`ATK_hold_over` 的平面会转到朝下，
	 * 所以固定法线 + 最大顶点色，保证两面一样亮。
	 */
	private static void renderUnshadedCube(PoseStack poseStack, GeoCube cube,
										   VertexConsumer buffer, int packedOverlay) {
		Matrix4f matrix = poseStack.last().pose();
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) continue;
			emitQuad(matrix, quad, buffer, packedOverlay, false);
			// 实体渲染层默认背面剔除；再发一遍反绕序，平面转到背对相机时仍可见。
			emitQuad(matrix, quad, buffer, packedOverlay, true);
		}
	}

	private static void emitQuad(Matrix4f matrix, GeoQuad quad, VertexConsumer buffer,
								 int packedOverlay, boolean reverse) {
		GeoVertex[] vertices = quad.vertices();
		for (int index = 0; index < vertices.length; index++) {
			GeoVertex vertex = vertices[reverse ? vertices.length - 1 - index : index];
			Vector4f position = matrix.transform(new Vector4f(vertex.position(), 1.0F));
			buffer.vertex(position.x, position.y, position.z)
					.color(255, 255, 255, 255)
					.uv(vertex.texU(), vertex.texV())
					.overlayCoords(packedOverlay)
					.uv2(LightTexture.FULL_BRIGHT)
					.normal(0.0F, 1.0F, 0.0F)
					.endVertex();
		}
	}

	private static boolean isScaleVisible(GeoBone bone) {
		return Math.abs(bone.getScaleX()) > VISIBLE_EPSILON
				&& Math.abs(bone.getScaleY()) > VISIBLE_EPSILON
				&& Math.abs(bone.getScaleZ()) > VISIBLE_EPSILON;
	}

	private static boolean isEffectAnimationActive() {
		String upper = BowgunRig.currentUpperAnimation();
		if (upper != null && EFFECT_ANIMATIONS.contains(upper)) return true;
		return EFFECT_ANIMATIONS.contains(BowgunRig.currentAnimation());
	}

	private static String normalizedEffectAnimation() {
		String active = BowgunRig.currentUpperAnimation();
		if (active == null || !EFFECT_ANIMATIONS.contains(active)) {
			active = BowgunRig.currentAnimation();
		}
		if (active != null && active.endsWith("_upper")) {
			return active.substring(0, active.length() - "_upper".length());
		}
		return active == null ? "" : active;
	}

	private static Map.Entry<String, EffectWindow> entry(String animation, String bone,
														 float start, float end) {
		return Map.entry(animation + "/" + bone, new EffectWindow(start, end));
	}

	private static void setScale(GeoBone bone, float value) {
		bone.setScaleX(value);
		bone.setScaleY(value);
		bone.setScaleZ(value);
	}

	private record EffectWindow(float start, float end) {
	}
}
