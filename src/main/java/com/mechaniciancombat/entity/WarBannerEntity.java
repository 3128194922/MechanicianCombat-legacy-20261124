package com.mechaniciancombat.entity;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.TkGunParticles;
import com.mechaniciancombat.TkGunServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 战旗：不可移动、不可摧毁，展开后给周围友军提供短时增益（吸收 + 力量）。
 *
 * <p>生命周期：10 tick 收起→展开，10 tick 展开，之后循环待机，共 300 tick。
 * 位置来自客户端量到的 locator（{@code WarBannerAnchorPayload}），模型根节点放在
 * locator 下方 {@link #LOCATOR_HEIGHT} 处，保证旗杆底部贴地。
 */
public final class WarBannerEntity extends Entity implements GeoEntity {
	public static final int CLOSE_TICKS = 10;
	public static final int OPEN_TICKS = 10;
	public static final int LIFETIME_TICKS = 300;
	public static final double AURA_RADIUS = 8.0D;
	private static final int SPARK_INTERVAL_TICKS = 2;
	private static final int SPARKS_PER_INTERVAL = 3;
	private static final int SPARK_MAX_AGE_TICKS = 16;
	/** `战旗.bbmodel` 里 `P2` 骨骼下的 locator 高度（模型像素 / 16）。 */
	public static final double LOCATOR_HEIGHT = 39.0D / 16.0D;

	private static final EntityDataAccessor<Float> VISUAL_YAW =
			SynchedEntityData.defineId(WarBannerEntity.class, EntityDataSerializers.FLOAT);
	private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("close");
	private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
	private UUID ownerUuid;
	private boolean expirationHandled;

	public WarBannerEntity(EntityType<? extends WarBannerEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		setInvulnerable(true);
	}

	public void setOwnerUuid(UUID ownerUuid) {
		this.ownerUuid = ownerUuid;
	}

	public UUID getOwnerUuid() {
		return ownerUuid;
	}

	public void setVisualYaw(float yaw) {
		entityData.set(VISUAL_YAW, Mth.wrapDegrees(yaw));
	}

	/**
	 * 单独用一个同步浮点，而不是实体 spawn 包的 yaw：
	 * 后者只同步到 1/256 圈，旗面朝向会歪。
	 */
	public float visualYaw() {
		float yaw = entityData.get(VISUAL_YAW);
		return Float.isFinite(yaw) ? yaw : Mth.wrapDegrees(getYRot());
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide) {
			return;
		}
		int age = tickCount;
		if (level() instanceof ServerLevel server
				&& age >= CLOSE_TICKS + OPEN_TICKS
				&& age < LIFETIME_TICKS - SPARK_MAX_AGE_TICKS
				&& age % SPARK_INTERVAL_TICKS == 0) {
			spawnAuraSparks(server);
		}
		if (age % 5 == 0) {
			applyAura();
		}
		if (age >= LIFETIME_TICKS && !expirationHandled) {
			expirationHandled = true;
			TkGunServer.onWarBannerExpired(this);
			discard();
		}
	}

	private void spawnAuraSparks(ServerLevel server) {
		Vec3 center = new Vec3(getX(), getY() + LOCATOR_HEIGHT, getZ());
		for (int index = 0; index < SPARKS_PER_INTERVAL; index++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			// 半径取平方根，火花才会均匀铺满光环圆盘而不是堆在旗杆周围。
			double radius = Math.sqrt(random.nextDouble()) * AURA_RADIUS;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			double y = center.y + 0.03D + random.nextDouble() * 0.18D;
			server.sendParticles(TkGunParticles.WAR_BANNER_SPARK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private void applyAura() {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		int remaining = Math.max(1, LIFETIME_TICKS - tickCount);
		Vec3 auraCenter = new Vec3(getX(), getY() + LOCATOR_HEIGHT, getZ());
		AABB search = new AABB(auraCenter, auraCenter).inflate(AURA_RADIUS);
		for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, search, this::isFriendly)) {
			if (target.position().distanceToSqr(auraCenter) > AURA_RADIUS * AURA_RADIUS) {
				continue;
			}
			target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, remaining, 0, true, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, remaining, 0, true, false, true));
		}
	}

	private boolean isFriendly(LivingEntity candidate) {
		if (!candidate.isAlive() || candidate.isSpectator()) {
			return false;
		}
		if (candidate instanceof Player) {
			return true;
		}
		Player owner = owner();
		if (owner == null) {
			return false;
		}
		if (candidate.isAlliedTo(owner)) {
			return true;
		}
		if (candidate instanceof OwnableEntity pet && ownerUuid != null
				&& ownerUuid.equals(pet.getOwnerUUID())) {
			return true;
		}
		return candidate instanceof MoltenGolemEntity golem
				&& owner.equals(golem.getSummoner());
	}

	private Player owner() {
		if (!(level() instanceof ServerLevel server) || ownerUuid == null) {
			return null;
		}
		ServerPlayer player = server.getServer().getPlayerList().getPlayer(ownerUuid);
		return player != null && player.isAlive() ? player : null;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void defineSynchedData() {
		entityData.define(VISUAL_YAW, Float.NaN);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		tickCount = tag.getInt("BannerAge");
		if (tag.contains("BannerYaw")) {
			setVisualYaw(tag.getFloat("BannerYaw"));
		}
		expirationHandled = false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerUuid != null) {
			tag.putUUID("Owner", ownerUuid);
		}
		tag.putInt("BannerAge", tickCount);
		tag.putFloat("BannerYaw", visualYaw());
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "banner", 0, this::animationPredicate));
	}

	private PlayState animationPredicate(AnimationState<WarBannerEntity> state) {
		if (tickCount < CLOSE_TICKS) {
			return state.setAndContinue(CLOSE);
		}
		if (tickCount < CLOSE_TICKS + OPEN_TICKS) {
			return state.setAndContinue(OPEN);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
