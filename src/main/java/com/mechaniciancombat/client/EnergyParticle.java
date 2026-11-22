package com.mechaniciancombat.client;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 机械师素材里那两张"竖条翻页"粒子：**按寿命逐帧播**（`setSpriteForAge`）。
 *
 * <p>{@code particle-muzzle}=枪口能量汇聚（converge，6 帧，短促 0.3s）、
 * {@code particle-hit}=命中能量爆（xplo，6 帧，稍长带淡出）。
 * 两个粒子各有一个 SpriteProvider（帧序列由 `assets/tkgun/particles/*.json` 定），
 * 所以这里用同一个类、两组参数，各给一个 Factory。
 */
public class EnergyParticle extends TextureSheetParticle {
	private final SpriteSet provider;
	private final boolean fade;
    private int attachedPlayerId = -1;
    private String attachedLocator = "aim";
    private static volatile SpriteSet activeProvider;

	protected EnergyParticle(ClientLevel world, double x, double y, double z,
							 SpriteSet provider, int lifetime, float scale, boolean fade) {
		super(world, x, y, z);
		this.provider = provider;
		this.lifetime = lifetime;
		this.quadSize = scale;
		this.fade = fade;
		this.xd = 0.0D;
		this.yd = 0.0D;
		this.zd = 0.0D;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.setSpriteFromAge(provider);
	}

	@Override
    public void tick() {
        if (attachedPlayerId >= 0) {
            var point = "aim".equals(attachedLocator) ? com.mechaniciancombat.client.rig.BowgunRigRenderer.aimPosition(attachedPlayerId)
                    : com.mechaniciancombat.client.rig.BowgunRigRenderer.locatorPosition(attachedPlayerId);
            if (point != null) setPos(point.x,point.y,point.z);
        }
        super.tick();
		this.setSpriteFromAge(this.provider);      // 逐帧翻页（寿命归一化）
		if (this.fade) {
			float life = (float) this.age / (float) this.lifetime;
			this.setAlpha(Math.max(0.0F, 1.0F - life * life));
		}
	}

	@Override
	protected int getLightColor(float tickDelta) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	/** 枪口：短促一团能量。 */
	public record MuzzleFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
        public MuzzleFactory { activeProvider = provider; }
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 7, 1.1F, false);
		}
	}

    public static boolean spawnAttached(net.minecraft.world.entity.Entity player, net.minecraft.world.phys.Vec3 pos, String locator) {
        var client=net.minecraft.client.Minecraft.getInstance(); var provider=activeProvider;
        if (player==null || pos==null || client.level==null || provider==null) return false;
        var particle=new EnergyParticle(client.level,pos.x,pos.y,pos.z,provider,10,1.25F,true);
        particle.attachedPlayerId=player.getId(); particle.attachedLocator=locator==null ? "locator" : locator;
        client.particleEngine.add(particle); return true;
    }
	/** 命中：大一圈、带淡出。 */
	public record HitFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 1.9F, true);
		}
	}

	public record CuttingFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 12, 2.0F, true);
		}
	}

	/** 幽匿弹：8 帧音爆，0.8 秒，铺满 3 格直径。 */
	public record SonicBoomFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 16, 3.0F, true);
		}
	}

	/** One pellet-sized impact puff for each grapeshot projectile. */
	public record GrapeshotImpactFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 2.0F, true);
		}
	}

	public record RoseQuartzImpactFactory(SpriteSet provider) implements ParticleProvider<SimpleParticleType> {
		@Override
		public EnergyParticle createParticle(SimpleParticleType type, ClientLevel world,
											 double x, double y, double z,
											 double vx, double vy, double vz) {
			return new EnergyParticle(world, x, y, z, this.provider, 10, 1.2F, true);
		}
	}
}
