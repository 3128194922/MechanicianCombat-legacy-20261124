package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.GunClientState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.HashMap;
import java.util.Map;

/**
 * 动能剑能量面（立场特效）：6 帧贴图。
 *
 * <p>只有右键开启立场后才显示；普通挥剑不再触发该特效。
 */
public final class KineticSwordEnergyEffect {
	public static final ResourceLocation TEXTURE =
			TkGun.id("textures/entity/kinetic_sword_energy_effect.png");
	public static final String BONE = "energy_effect";
	private static final int FRAMES = 6;
	private static final int FRAMES_PER_SECOND = 9;
	private static final float FRAME_SIZE = 48.0F;
	private static final float MODEL_TEXTURE_SIZE = 64.0F;
	private static final float MIN_UV_SPAN = 4.0F / MODEL_TEXTURE_SIZE;
	private static final Map<Integer, Long> STANCE_STARTED_AT = new HashMap<>();

	public static boolean isEnergyBone(String name) {
		return BONE.equals(name);
	}

	public static int colorFor(Entity entity) {
		if (entity == null || entity.level() == null) {
			return 0;
		}
		int id = entity.getId();
		boolean stance = entity instanceof Player swordPlayer
				&& TkGun.isKineticSword(swordPlayer)
				&& GunClientState.kineticStance(id);
		if (!stance) {
			STANCE_STARTED_AT.remove(id);
			return 0;
		}
		// 立场开启期间能量面常亮；记录起始时刻只用于推进 6 帧序列。
		STANCE_STARTED_AT.putIfAbsent(id, entity.level().getGameTime());
		return 0xFFFFFFFF;
	}

	public static boolean applyVisibility(GeoBone bone, Entity entity) {
		int colour = colorFor(entity);
		boolean visible = (colour >>> 24) > 0;
		float scale = visible ? 1.0F : 1.0E-4F;
		bone.setScaleX(scale);
		bone.setScaleY(scale);
		bone.setScaleZ(scale);
		return visible;
	}

	public static int frameFor(Entity entity) {
		if (entity == null || entity.level() == null) {
			return 0;
		}
		Long startedAt = STANCE_STARTED_AT.get(entity.getId());
		if (startedAt == null) {
			return 0;
		}
		long elapsed = entity.level().getGameTime() - startedAt;
		long animationFrame = elapsed * FRAMES_PER_SECOND / 20L;
		return (int) Math.floorMod(animationFrame, (long) FRAMES);
	}

	public static GeoCube remapUv(GeoCube cube, int frame) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] remapped = new GeoQuad[source.length];
		for (int index = 0; index < source.length; index++) {
			GeoQuad quad = source[index];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			float minU = Float.POSITIVE_INFINITY;
			float maxU = Float.NEGATIVE_INFINITY;
			float minV = Float.POSITIVE_INFINITY;
			float maxV = Float.NEGATIVE_INFINITY;
			for (GeoVertex vertex : vertices) {
				minU = Math.min(minU, vertex.texU());
				maxU = Math.max(maxU, vertex.texU());
				minV = Math.min(minV, vertex.texV());
				maxV = Math.max(maxV, vertex.texV());
			}
			if (maxU - minU < MIN_UV_SPAN || maxV - minV < MIN_UV_SPAN) {
				continue;
			}
			GeoVertex[] updated = new GeoVertex[vertices.length];
			for (int vertexIndex = 0; vertexIndex < vertices.length; vertexIndex++) {
				GeoVertex vertex = vertices[vertexIndex];
				updated[vertexIndex] = vertex.withUVs(
						vertex.texU() * MODEL_TEXTURE_SIZE / FRAME_SIZE,
						(frame + vertex.texV() * MODEL_TEXTURE_SIZE / FRAME_SIZE) / FRAMES);
			}
			remapped[index] = new GeoQuad(updated, quad.normal(), quad.direction());
		}
		return new GeoCube(remapped, cube.pivot(), cube.rotation(), cube.size(),
				cube.inflate(), cube.mirror());
	}

	private KineticSwordEnergyEffect() {
	}
}
