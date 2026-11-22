package com.mechaniciancombat.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 枢德之裁近战技能火花（对应上游 `SkillSparkParticle`）。
 *
 * <p>作者素材是 **5 帧、18 FPS** 的翻页图（`skill_spark_0..4.png`），
 * 在 `ATK_3_hold`(0.5s) 与 `ATK_hold_over`(1.0s) 的粒子关键帧上触发。
 * 生命 10 tick，全程不透明、不受重力，全亮渲染（近战火花不该被世界光照压暗）。
 */
public final class SkillSparkParticle extends TextureSheetParticle {
	private static final int FRAME_COUNT = 5;
	private static final int LIFETIME = 10;
	/** 素材是 18 FPS 的翻页图；MC 的 age 是 20 tick/s，所以按比例换算。 */
	private static final int FRAME_RATE = 18;

	private final SpriteSet provider;

	private SkillSparkParticle(ClientLevel level, double x, double y, double z, SpriteSet provider) {
		super(level, x, y, z);
		this.provider = provider;
		this.lifetime = LIFETIME;
		this.quadSize = 0.9F;
		this.xd = 0.0D;
		this.yd = 0.0D;
		this.zd = 0.0D;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		updateSprite();
	}

	private void updateSprite() {
		// 用真实 age 选帧：`get(frame, FRAME_COUNT)` 会把最后一帧夹到倒数第二帧。
		int frame = Math.min(FRAME_COUNT - 1, this.age * FRAME_RATE / 20);
		this.setSprite(this.provider.get(frame, FRAME_COUNT - 1));
	}

	@Override
	public void tick() {
		super.tick();
		updateSprite();
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	public static final class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Factory(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public SkillSparkParticle createParticle(SimpleParticleType type, ClientLevel level,
												 double x, double y, double z,
												 double dx, double dy, double dz) {
			return new SkillSparkParticle(level, x, y, z, this.sprites);
		}
	}
}
