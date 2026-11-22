package com.mechaniciancombat;

import com.mechaniciancombat.net.MeleeImpactPayload;
import com.mechaniciancombat.net.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Hitstop and deferred knockback for successful Veritas hits. */
public final class MeleeImpactService {
    private record Pending(LivingEntity target, Vec3 direction, long releaseAt) { }
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    public static void apply(ServerPlayer attacker, TkGunServer.PState state, List<Entity> targets, Vec3 forward,
                             int baseTicks, boolean critical, boolean lockAttacker) {
        if (attacker.getServer() == null || targets.isEmpty()) return;
        int ticks = Math.min(8, Math.max(1, baseTicks + (critical ? 2 : 0)));
        int[] ids = new int[targets.size()]; long now = attacker.level().getGameTime();
        for (int i=0; i<targets.size(); i++) {
            Entity target = targets.get(i); ids[i] = target.getId();
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 outward = attacker.getEyePosition().subtract(center); outward = new Vec3(outward.x,0,outward.z);
            if (outward.lengthSqr() < 1.0E-6D) outward = forward.scale(-1);
            outward = outward.normalize();
            Vec3 point = new Vec3(center.x + outward.x * (target.getBbWidth()*.5D+.2D), target.getY()+target.getBbHeight()*.62D, center.z+outward.z*(target.getBbWidth()*.5D+.2D));
            attacker.serverLevel().sendParticles(TkGunParticles.GRAPESHOT_IMPACT, point.x, point.y, point.z, 1,0,0,0,0);
            if (target instanceof LivingEntity living) {
                Pending old = PENDING.get(living.getId());
                PENDING.put(living.getId(), new Pending(living, forward, Math.max(now + ticks, old == null ? 0 : old.releaseAt())));
            }
        }
        TkGunServer.playSound(attacker, TkGun.id("hit"), 2.0F, 2.0F);
        if (lockAttacker) {
            state.stateStarted += ticks;
            if (state.stateUntil > 0) state.stateUntil += ticks;
            if (state.meleeHeld && state.meleePressedAt > 0) state.meleePressedAt += ticks;
        }
        boolean shake = state.meleeCombo == 3 && !state.meleeHoldOver || state.meleeHoldOver && state.meleeHoldHits == 3;
        var payload = new MeleeImpactPayload(attacker.getId(), ids, ticks, critical, lockAttacker, shake);
        for (ServerPlayer viewer : attacker.getServer().getPlayerList().getPlayers()) ModNetwork.send(viewer, payload);
    }
    public static void tick(MinecraftServer server) {
        Iterator<Pending> iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            Pending p = iterator.next(); LivingEntity target = p.target();
            if (target.isRemoved() || !target.isAlive() || target.getServer() != server) { iterator.remove(); continue; }
            if (target.level().getGameTime() < p.releaseAt()) {
                Vec3 v = target.getDeltaMovement(); target.setDeltaMovement(0,v.y,0); target.hurtMarked = true;
            } else { iterator.remove(); target.knockback(.35D,p.direction().x,p.direction().z); }
        }
    }
    private MeleeImpactService() { }
}
