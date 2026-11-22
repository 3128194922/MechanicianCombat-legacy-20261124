package com.mechaniciancombat.entity;

import com.mechaniciancombat.CombatTargeting;
import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.TkGunServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
 * 猎矛部署的固定狩猎炮台（r774）。
 *
 * <p>不可移动、不可摧毁；自动索敌（15 格内敌对生物）、水平/垂直转向、
 * 对齐且有视线时交替从两个炮口发射，最多 8 发；主人离远/换维度/手动交互即回收。
 */
public final class HunterTurretEntity extends Entity implements GeoEntity {
	/** 召唤动画至少播这么久，之后切待机。 */
	public static final int SUMMON_TICKS = 30;
	public static final int MAX_SHOTS = 8;
	/** 两段开火动画各 0.5 秒。 */
	public static final int FIRE_INTERVAL_TICKS = (int) Math.ceil(0.5D * 20.0D);
	public static final double TARGET_RADIUS = 15.0D;
	public static final float TURN_DEGREES_PER_TICK = 4.0F;
	private static final int[] SUMMON_SOUND_TICKS = {0, 10, 17, 20};

	private static final EntityDataAccessor<Float> SHOULDER_YAW =
			SynchedEntityData.defineId(HunterTurretEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FOREARM_PITCH =
			SynchedEntityData.defineId(HunterTurretEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> FIRE_MODE =
			SynchedEntityData.defineId(HunterTurretEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FIRE_SEQUENCE =
			SynchedEntityData.defineId(HunterTurretEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> SUMMONING =
			SynchedEntityData.defineId(HunterTurretEntity.class, EntityDataSerializers.BOOLEAN);

	private static final RawAnimation SUMMON = RawAnimation.begin().thenPlayAndHold("summon");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation FIRE_1 = RawAnimation.begin().thenPlayAndHold("fire_1");
	private static final RawAnimation FIRE_2 = RawAnimation.begin().thenPlayAndHold("fire_2");

	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
	private UUID ownerUuid;
	private LivingEntity target;
	private double shoulderYaw;
	private double forearmPitch;
	private int shotsFired;
	private long nextShotAt;
	private long fireModeUntil;
	private long finishAt;
	private int fireSequence;
	private int lastAnimationSequence = -1;
	private int summonSoundMask;
	private boolean summoned;
	private boolean finished;

	public HunterTurretEntity(EntityType<? extends HunterTurretEntity> type, Level level) {
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

	public float shoulderYaw() {
		return entityData.get(SHOULDER_YAW);
	}

	public float forearmPitch() {
		return entityData.get(FOREARM_PITCH);
	}

	public boolean isSummoning() {
		return entityData.get(SUMMONING);
	}

	public boolean isFiringAnimation() {
		int mode = entityData.get(FIRE_MODE);
		return mode == 2 || mode == 3;
	}

	public int fireMode() {
		return entityData.get(FIRE_MODE);
	}

	public int fireSequence() {
		return entityData.get(FIRE_SEQUENCE);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty() && (TkGunServer.HUNTER_SPEAR == null
				|| !held.is(TkGunServer.HUNTER_SPEAR))) {
			return InteractionResult.PASS;
		}
		if (level().isClientSide) {
			return isSummoning() ? InteractionResult.FAIL : InteractionResult.SUCCESS;
		}
		boolean ready = summoned && !isSummoning() && !finished && ownerUuid != null
				&& ownerUuid.equals(player.getUUID());
		if (!ready) {
			return InteractionResult.PASS;
		}
		finishAndReturn("手动回收");
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide || finished) {
			return;
		}
		ServerLevel world = (ServerLevel) level();
		ServerPlayer owner = owner(world);
		if (owner == null) {
			return;
		}
		if (owner.serverLevel() != world) {
			finishAndReturn("主人切换维度");
			return;
		}
		if (distanceToSqr(owner) > TARGET_RADIUS * TARGET_RADIUS) {
			finishAndReturn("主人离开范围");
			return;
		}
		if (!summoned) {
			playSummonSounds(world);
			if (tickCount < SUMMON_TICKS) {
				return;
			}
			summoned = true;
			entityData.set(SUMMONING, false);
		}
		long now = world.getGameTime();
		if (fireModeUntil > 0 && now >= fireModeUntil) {
			fireModeUntil = 0;
			entityData.set(FIRE_MODE, 0);
		}
		if (finishAt > 0 && now >= finishAt) {
			finishAndReturn("弹药耗尽");
			return;
		}
		if (target == null || !validTarget(target, owner)) {
			target = findTarget(world, owner);
		}
		if (target == null) {
			return;
		}
		Vec3 origin = turretEyePosition();
		Vec3 targetPoint = target.getBoundingBox().getCenter();
		Vec3 delta = targetPoint.subtract(origin);
		double desiredYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		double desiredPitch = Math.toDegrees(Math.atan2(delta.y, horizontal));
		shoulderYaw = approachYaw(shoulderYaw, desiredYaw, TURN_DEGREES_PER_TICK);
		forearmPitch = approachPitch(forearmPitch, desiredPitch, TURN_DEGREES_PER_TICK);
		entityData.set(SHOULDER_YAW, (float) shoulderYaw);
		entityData.set(FOREARM_PITCH, (float) forearmPitch);

		boolean aligned = Math.abs(Mth.wrapDegrees((float) (desiredYaw - shoulderYaw))) <= 4.0F
				&& Math.abs(desiredPitch - forearmPitch) <= 4.0F;
		if (aligned && shotsFired < MAX_SHOTS && now >= nextShotAt
				&& hasLineOfSight(world, origin, targetPoint)) {
			fire(world, owner, shotsFired + 1);
		}
	}

	private void playSummonSounds(ServerLevel world) {
		for (int index = 0; index < SUMMON_SOUND_TICKS.length; index++) {
			int bit = 1 << index;
			if ((summonSoundMask & bit) != 0 || tickCount < SUMMON_SOUND_TICKS[index]) {
				continue;
			}
			summonSoundMask |= bit;
			SoundEvent sound = SoundEvent.createVariableRangeEvent(switch (index) {
				case 0 -> TkGun.SND_HUNTER_MECH4;
				case 1 -> TkGun.SND_HUNTER_BOLT3;
				case 2 -> TkGun.SND_HUNTER_BOLT;
				default -> TkGun.SND_MISSILE_COLD_LAUNCH;
			});
			world.playSound(null, getX(), getY() + 1.0D, getZ(), sound,
					SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	private ServerPlayer owner(ServerLevel world) {
		if (ownerUuid == null) {
			return null;
		}
		return world.getServer().getPlayerList().getPlayer(ownerUuid);
	}

	private LivingEntity findTarget(ServerLevel world, ServerPlayer owner) {
		AABB search = getBoundingBox().inflate(TARGET_RADIUS);
		Vec3 origin = turretEyePosition();
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : world.getEntitiesOfClass(LivingEntity.class, search,
				entity -> validTarget(entity, owner))) {
			Vec3 point = candidate.getBoundingBox().getCenter();
			if (!hasLineOfSight(world, origin, point)) {
				continue;
			}
			double distance = distanceToSqr(candidate);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private boolean validTarget(LivingEntity candidate, ServerPlayer owner) {
		if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()
				|| !candidate.isPickable() || !(candidate instanceof Monster)
				|| CombatTargeting.isGolem(candidate) || candidate.isAlliedTo(owner)) {
			return false;
		}
		return distanceToSqr(candidate) <= TARGET_RADIUS * TARGET_RADIUS;
	}

	private boolean hasLineOfSight(ServerLevel world, Vec3 from, Vec3 targetPoint) {
		var hit = world.clip(new ClipContext(from, targetPoint,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS
				|| from.distanceTo(hit.getLocation())
				>= from.distanceTo(targetPoint) - 0.05D;
	}

	private void fire(ServerLevel world, ServerPlayer owner, int shotNumber) {
		int muzzleIndex = (shotNumber - 1) % 2;
		Vec3 muzzle = muzzlePosition(muzzleIndex);
		Vec3 direction = target.getBoundingBox().getCenter().subtract(muzzle).normalize();
		ItemStack bolt = new ItemStack(TkGunServer.BOLT);
		if (TkGunServer.MOLTEN_TRIDENT_ENTITY == null) {
			return;
		}
		MoltenTridentEntity projectile = new MoltenTridentEntity(
				TkGunServer.MOLTEN_TRIDENT_ENTITY, world, owner, bolt);
		projectile.moveTo(muzzle.x, muzzle.y, muzzle.z,
				(float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
				(float) Math.toDegrees(Math.asin(-direction.y)));
		projectile.setDeltaMovement(direction.scale(2.5D));
		projectile.setGunFired(true);
		projectile.setTurretProjectile(owner);
		world.addFreshEntity(projectile);
		world.playSound(null, muzzle.x, muzzle.y, muzzle.z,
				SoundEvent.createVariableRangeEvent(TkGun.SND_GUNSHOT),
				SoundSource.HOSTILE, 1.0F, 1.0F);

		shotsFired = shotNumber;
		nextShotAt = world.getGameTime() + FIRE_INTERVAL_TICKS;
		fireModeUntil = world.getGameTime() + FIRE_INTERVAL_TICKS;
		fireSequence++;
		entityData.set(FIRE_MODE, muzzleIndex == 0 ? 2 : 3);
		entityData.set(FIRE_SEQUENCE, fireSequence);
		if (shotsFired >= MAX_SHOTS) {
			finishAt = world.getGameTime() + FIRE_INTERVAL_TICKS;
		}
	}

	private Vec3 muzzlePosition(int index) {
		double yaw = Math.toRadians(shoulderYaw);
		double pitch = Math.toRadians(forearmPitch);
		double muzzleY = index == 0 ? 17.5D : 14.5D;
		double muzzleZ = -14.5D;
		double forearmY = 16.0D;
		double forearmZ = 2.5D;
		double relativeY = muzzleY - forearmY;
		double relativeZ = muzzleZ - forearmZ;
		double rotatedY = relativeY * Math.cos(pitch) - relativeZ * Math.sin(pitch);
		double rotatedZ = relativeY * Math.sin(pitch) + relativeZ * Math.cos(pitch);
		double localY = (forearmY + rotatedY - 3.0D) / 16.0D;
		double localZ = (forearmZ + rotatedZ) / 16.0D;
		return new Vec3(
				getX() - Math.sin(yaw) * localZ,
				getY() + localY,
				getZ() + Math.cos(yaw) * localZ);
	}

	private Vec3 turretEyePosition() {
		return position().add(0.0D, 1.0D, 0.0D);
	}

	private static double approachYaw(double current, double target, double maxStep) {
		double delta = Mth.wrapDegrees((float) (target - current));
		return Mth.wrapDegrees((float) (current + Mth.clamp(delta, -maxStep, maxStep)));
	}

	private static double approachPitch(double current, double target, double maxStep) {
		double delta = target - current;
		return Mth.clamp(current + Mth.clamp(delta, -maxStep, maxStep), -89.0D, 89.0D);
	}

	public void finishAndReturn(String reason) {
		if (finished) {
			return;
		}
		finished = true;
		TkGun.LOGGER.info("[tkgun] 狩猎炮台结束：{} pos={} shots={}",
				reason, position(), shotsFired);
		TkGunServer.onHunterTurretFinished(this);
		discard();
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
		// 让准星能选中它进行交互；仍不可被攻击（isAttackable=false）。
		return true;
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
	public void move(MoverType type, Vec3 movement) {
		// 固定炮台不移动。
	}

	@Override
	protected void defineSynchedData() {
		entityData.define(SHOULDER_YAW, 0.0F);
		entityData.define(FOREARM_PITCH, 0.0F);
		entityData.define(FIRE_MODE, 0);
		entityData.define(FIRE_SEQUENCE, 0);
		entityData.define(SUMMONING, true);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		shoulderYaw = tag.getDouble("ShoulderYaw");
		forearmPitch = tag.getDouble("ForearmPitch");
		shotsFired = tag.getInt("ShotsFired");
		fireSequence = tag.getInt("FireSequence");
		// 从存档读回的炮台视为已部署完成，不重播召唤动画。
		summoned = true;
		entityData.set(SHOULDER_YAW, (float) shoulderYaw);
		entityData.set(FOREARM_PITCH, (float) forearmPitch);
		entityData.set(FIRE_SEQUENCE, fireSequence);
		entityData.set(SUMMONING, false);
		if (shotsFired >= MAX_SHOTS && finishAt <= 0L) {
			finishAt = level().getGameTime() + FIRE_INTERVAL_TICKS;
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerUuid != null) {
			tag.putUUID("Owner", ownerUuid);
		}
		tag.putDouble("ShoulderYaw", shoulderYaw);
		tag.putDouble("ForearmPitch", forearmPitch);
		tag.putInt("ShotsFired", shotsFired);
		tag.putInt("FireSequence", fireSequence);
		tag.putBoolean("Summoned", summoned);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "turret", 0, this::animationPredicate)
				.setSoundKeyframeHandler(event -> { })
				.setCustomInstructionKeyframeHandler(event -> { })
				.setParticleKeyframeHandler(event -> { }));
	}

	private PlayState animationPredicate(AnimationState<HunterTurretEntity> state) {
		if (entityData.get(SUMMONING)) {
			return state.setAndContinue(SUMMON);
		}
		int mode = entityData.get(FIRE_MODE);
		int sequence = entityData.get(FIRE_SEQUENCE);
		if (mode == 2 || mode == 3) {
			if (sequence != lastAnimationSequence) {
				lastAnimationSequence = sequence;
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(mode == 2 ? FIRE_1 : FIRE_2);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
