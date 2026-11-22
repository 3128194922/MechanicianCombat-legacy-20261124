package com.mechaniciancombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** r611 server-authoritative collision-checked skill push and single path hit. */
public final class FortressSkillKnockbackService {
    private static final int TRAVEL_TICKS = 15;
    private static final double DISTANCE = 15.0D;
    private static final double STEP = DISTANCE / TRAVEL_TICKS;
    private static final double HALF_EXTENT = 1.5D;
    private static final float PATH_DAMAGE = 10.0F * 1.5F * 1.5F * 1.5F;
    private static final List<Push> PUSHES = new ArrayList<>();

    public static void start(ServerPlayer attacker, Entity target, Vec3 direction) {
        if (!(target.level() instanceof ServerLevel) || attacker.level() != target.level()) return;
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-6D) return;
        PUSHES.removeIf(push -> push.target == target);
        PUSHES.add(new Push(attacker, target, horizontal.normalize()));
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Push> iterator = PUSHES.iterator(); iterator.hasNext();) {
            Push push = iterator.next();
            Entity target = push.target;
            if (target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel world)
                    || world.getServer() != server || push.attacker.isRemoved() || push.attacker.level() != world) {
                iterator.remove();
                continue;
            }
            Vec3 from = target.position();
            Vec3 step = push.direction.scale(STEP);
            Vec3 next = from.add(step);
            if (!world.noCollision(target, target.getBoundingBox().move(step))) {
                iterator.remove();
                continue;
            }
            double yOffset = target.getBbHeight() * 0.5D;
            damagePath(world, push, from, next, yOffset);
            target.teleportTo(next.x, next.y, next.z);
            target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
            target.hurtMarked = true;
            for (int i = 0; i < 3; i++) {
                double t = (i + 1.0D) / 4.0D;
                world.sendParticles(ParticleTypes.FLAME, from.x + step.x * t, from.y + yOffset,
                        from.z + step.z * t, 2, 0.12D, 0.08D, 0.12D, 0.01D);
            }
            if (++push.ticks >= TRAVEL_TICKS) iterator.remove();
        }
    }

    private static void damagePath(ServerLevel world, Push push, Vec3 from, Vec3 to, double yOffset) {
        Vec3 a = from.add(0, yOffset, 0);
        Vec3 b = to.add(0, yOffset, 0);
        AABB hitbox = new AABB(a.x - HALF_EXTENT, a.y - HALF_EXTENT, a.z - HALF_EXTENT,
                a.x + HALF_EXTENT, a.y + HALF_EXTENT, a.z + HALF_EXTENT)
                .minmax(new AABB(b.x - HALF_EXTENT, b.y - HALF_EXTENT, b.z - HALF_EXTENT,
                        b.x + HALF_EXTENT, b.y + HALF_EXTENT, b.z + HALF_EXTENT));
        for (Entity entity : world.getEntities(push.target, hitbox,
                candidate -> candidate != push.attacker && candidate.isAlive() && candidate.isPickable()
                        && !candidate.isSpectator() && CombatTargeting.isTargetable(candidate))) {
            if (!push.hitIds.add(entity.getId())) continue;
            if (entity instanceof LivingEntity living) living.invulnerableTime = 0;
            entity.hurt(push.attacker.damageSources().playerAttack(push.attacker), PATH_DAMAGE);
        }
    }

    private static final class Push {
        final ServerPlayer attacker;
        final Entity target;
        final Vec3 direction;
        final Set<Integer> hitIds = new HashSet<>();
        int ticks;
        Push(ServerPlayer attacker, Entity target, Vec3 direction) {
            this.attacker = attacker;
            this.target = target;
            this.direction = direction;
        }
    }
    private FortressSkillKnockbackService() { }
}
