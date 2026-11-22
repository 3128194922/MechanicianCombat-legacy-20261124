package com.mechaniciancombat.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 战旗光环火花：中心亮、短十字光、碎对角尾；像素淡出由 8 帧贴图本身承担。
 */
public final class WarBannerSparkParticle extends TextureSheetParticle {
    private static final int FRAME_COUNT = 8;
    private static final int FRAME_TICKS = 2;
    private static final int LIFETIME = 16;

    private final SpriteSet provider;
    private final float driftPhase;
    private final float baseScale;

    private WarBannerSparkParticle(ClientLevel level, double x, double y, double z,
                                   SpriteSet provider) {
        super(level, x, y, z);
        this.provider = provider;
        this.lifetime = LIFETIME;
        this.baseScale = 0.20F + this.random.nextFloat() * 0.10F;
        this.quadSize = baseScale;
        this.xd = (this.random.nextDouble() - 0.5D) * 0.012D;
        this.yd = 0.035D + this.random.nextDouble() * 0.035D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.012D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.driftPhase = this.random.nextFloat() * Mth.TWO_PI;
        updateSprite();
    }

    private void updateSprite() {
        int frame = Math.min(FRAME_COUNT - 1, this.age / FRAME_TICKS);
        this.setSprite(this.provider.get(frame, FRAME_COUNT - 1));
    }

    @Override
    public void tick() {
        super.tick();
        float phase = driftPhase + this.age * 0.38F;
        this.xd += Mth.cos(phase) * 0.0012D;
        this.zd += Mth.sin(phase) * 0.0012D;
        float life = (float) this.age / (float) this.lifetime;
        this.quadSize = this.baseScale * (1.0F - life * 0.32F);
        this.setAlpha(1.0F - life * 0.48F);
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

    public record Factory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
        @Override
        public WarBannerSparkParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                     double x, double y, double z,
                                                     double vx, double vy, double vz) {
            return new WarBannerSparkParticle(level, x, y, z, provider);
        }
    }
}
