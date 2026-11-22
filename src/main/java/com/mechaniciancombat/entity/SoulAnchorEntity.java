package com.mechaniciancombat.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 灵魂锚投射物：飞行 → 固定 → 回收三种状态。
 *
 * <p>飞行阶段每 tick 自行做方块/实体射线检测；命中方块后固定在落点，
 * 命中生物后挂在目标身上并把目标 id 同步给客户端（绳索渲染用）。
 * 回收阶段沿直线飞回主人，命中生物时按原版钓鱼竿的方式持续牵引目标。
 */
public final class SoulAnchorEntity extends Projectile implements GeoEntity {
	public enum State {
		FLYING,
		ANCHORED,
		RETRACTING
	}

	private static final EntityDataAccessor<Integer> DATA_STATE =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HOOKED_ID =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_DIR_X =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_DIR_Y =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_DIR_Z =
			SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.FLOAT);

	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
	private int life;

	public SoulAnchorEntity(EntityType<? extends SoulAnchorEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(false);
		this.noPhysics = false;
	}

	public SoulAnchorEntity(EntityType<? extends SoulAnchorEntity> type, Level level, LivingEntity owner) {
		this(type, level);
		this.setOwner(owner);
	}

	@Override
	protected void defineSynchedData() {
		this.entityData.define(DATA_STATE, State.FLYING.ordinal());
		this.entityData.define(DATA_OWNER_ID, -1);
		this.entityData.define(DATA_HOOKED_ID, -1);
		this.entityData.define(DATA_DIR_X, 0.0F);
		this.entityData.define(DATA_DIR_Y, 0.0F);
		this.entityData.define(DATA_DIR_Z, 1.0F);
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		if (!this.level().isClientSide) {
			this.entityData.set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
		}
	}

	public Entity trackedOwner() {
		int ownerId = this.entityData.get(DATA_OWNER_ID);
		if (ownerId < 0) {
			return this.getOwner();
		}
		return this.level().getEntity(ownerId);
	}

	public State anchorState() {
		int ordinal = Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1);
		return State.values()[ordinal];
	}

	public boolean isRetracting() {
		return this.anchorState() == State.RETRACTING;
	}

	public Entity hookedEntity() {
		int entityId = this.entityData.get(DATA_HOOKED_ID);
		return entityId < 0 ? null : this.level().getEntity(entityId);
	}

	public int hookedEntityId() {
		return this.entityData.get(DATA_HOOKED_ID);
	}

	public Vec3 flightDirection() {
		Vec3 direction = new Vec3(this.entityData.get(DATA_DIR_X),
				this.entityData.get(DATA_DIR_Y), this.entityData.get(DATA_DIR_Z));
		return direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
	}

	@Override
	public void tick() {
		this.xo = this.getX();
		this.yo = this.getY();
		this.zo = this.getZ();
		this.life++;
		State state = this.anchorState();
		if (state == State.ANCHORED) {
			this.setDeltaMovement(Vec3.ZERO);
			this.hasImpulse = true;
			Entity hooked = this.hookedEntity();
			if (hooked != null && hooked.isAlive()) {
				Vec3 center = hooked.getBoundingBox().getCenter();
				this.setPos(center.x, center.y, center.z);
			}
			return;
		}
		if (state == State.RETRACTING) {
			Entity owner = this.trackedOwner();
			if (owner == null || !owner.isAlive()) {
				this.discard();
				return;
			}
			Entity hooked = this.hookedEntity();
			if (hooked != null && hooked.isAlive()) {
				Vec3 center = hooked.getBoundingBox().getCenter();
				this.setPos(center.x, center.y, center.z);
				this.setDeltaMovement(Vec3.ZERO);
				this.hasImpulse = true;
				pullHookedEntity(owner, hooked);
				if (owner.distanceToSqr(hooked) <= 6.25D) {
					this.discard();
				}
				return;
			}
			Vec3 target = owner.getEyePosition().add(0.0D, -owner.getEyeHeight() * 0.35D, 0.0D);
			Vec3 delta = target.subtract(this.position());
			if (delta.lengthSqr() <= 2.25D) {
				this.discard();
				return;
			}
			this.noPhysics = true;
			this.setDeltaMovement(delta.normalize().scale(3.2D));
			this.hasImpulse = true;
			moveFree(this.getDeltaMovement());
			return;
		}

		Entity owner = this.trackedOwner();
		if (!this.level().isClientSide && (owner == null || !owner.isAlive() || this.life > 200)) {
			if (owner != null && owner.isAlive()) {
				this.retrieve(owner);
			} else {
				this.discard();
			}
			return;
		}

		Vec3 from = this.position();
		Vec3 delta = this.getDeltaMovement();
		Vec3 to = from.add(delta);
		BlockHitResult blockHit = this.level().clip(new ClipContext(from, to,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this, from, to,
				this.getBoundingBox().expandTowards(delta).inflate(1.0D),
				entity -> entity instanceof LivingEntity && entity != owner
						&& entity.isAlive() && entity.isPickable(), delta.lengthSqr());
		if (entityHit != null && (blockHit == null || entityHit.getLocation().distanceToSqr(from)
				<= blockHit.getLocation().distanceToSqr(from))) {
			this.anchorAt(entityHit.getLocation().subtract(direction(delta).scale(0.2D)),
					entityHit.getEntity().getId());
			return;
		}
		if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
			this.anchorAt(blockHit.getLocation().subtract(direction(delta).scale(0.08D)), -1);
			return;
		}
		this.setPos(to.x, to.y, to.z);
		this.setDeltaMovement(delta.x, delta.y - 0.06D, delta.z);
	}

	private static Vec3 direction(Vec3 delta) {
		return delta.lengthSqr() > 1.0E-6D ? delta.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
	}

	private void moveFree(Vec3 delta) {
		this.setPos(this.getX() + delta.x, this.getY() + delta.y, this.getZ() + delta.z);
	}

	private void anchorAt(Vec3 position, int hookedEntityId) {
		if (this.level().isClientSide) {
			return;
		}
		Vec3 direction = direction(this.getDeltaMovement());
		this.entityData.set(DATA_DIR_X, (float) direction.x);
		this.entityData.set(DATA_DIR_Y, (float) direction.y);
		this.entityData.set(DATA_DIR_Z, (float) direction.z);
		this.setPos(position.x, position.y, position.z);
		this.setDeltaMovement(Vec3.ZERO);
		this.hasImpulse = true;
		this.noPhysics = true;
		this.entityData.set(DATA_HOOKED_ID, hookedEntityId);
		this.entityData.set(DATA_STATE, State.ANCHORED.ordinal());
	}

	private void pullHookedEntity(Entity owner, Entity hooked) {
		Vec3 pull = owner.position().subtract(this.position()).scale(0.1D);
		hooked.setDeltaMovement(hooked.getDeltaMovement().add(pull));
		hooked.hasImpulse = true;
		hooked.fallDistance = 0.0F;
	}

	public void retrieve(Entity owner) {
		if (this.level().isClientSide || this.anchorState() == State.RETRACTING) {
			return;
		}
		this.setOwner(owner);
		this.noPhysics = true;
		this.entityData.set(DATA_STATE, State.RETRACTING.ordinal());
		Vec3 delta = owner.getEyePosition().subtract(this.position());
		if (delta.lengthSqr() > 1.0E-6D) {
			this.setDeltaMovement(delta.normalize().scale(3.2D));
			this.hasImpulse = true;
		}
	}

	/** 取消与实体的连接并回收，避免继续牵引目标。 */
	public void cancelRetrieve(Entity owner) {
		if (this.level().isClientSide) {
			return;
		}
		this.entityData.set(DATA_HOOKED_ID, -1);
		if (this.anchorState() != State.RETRACTING) {
			this.retrieve(owner);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("AnchorState", (byte) this.anchorState().ordinal());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		int state = Mth.clamp(tag.getByte("AnchorState"), 0, State.values().length - 1);
		this.entityData.set(DATA_STATE, state);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 192.0D * 192.0D;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// 固定姿势，无动画控制器。
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.animationCache;
	}
}
