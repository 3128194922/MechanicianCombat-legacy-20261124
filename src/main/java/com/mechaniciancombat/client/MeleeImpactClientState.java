package com.mechaniciancombat.client;

import com.mechaniciancombat.net.MeleeImpactPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Client-side hitstop, target render freeze and camera shake. */
public final class MeleeImpactClientState {
    private record Hitstop(int remaining, boolean lock) { }
    public record Freeze(int remaining, int total, double x, double y, double z, float yaw, float pitch,
                         float headYaw, float bodyYaw, double shakeX, double shakeZ) {
        public double shake(float partial) {
            if (total <= 0) return 0;
            float elapsed = total - remaining + partial;
            float fade = Math.max(0, (remaining - partial) / total);
            return Math.sin(elapsed * 5) * fade;
        }
        Freeze next() { return new Freeze(remaining-1,total,x,y,z,yaw,pitch,headYaw,bodyYaw,shakeX,shakeZ); }
    }
    private static final Map<Integer, Hitstop> STOPS = new HashMap<>();
    private static final Map<Integer, Freeze> TARGETS = new HashMap<>();
    private static int shakeRemaining, shakeTotal;
    private static float shakeIntensity;
    public static void apply(MeleeImpactPayload payload) {
        if (payload.ticks() <= 0) return;
        Hitstop old = STOPS.get(payload.attackerId());
        boolean locked = payload.lockAttacker() || old != null && old.lock();
        int remaining = Math.max(payload.ticks(), old == null ? 0 : old.remaining());
        STOPS.put(payload.attackerId(), new Hitstop(remaining, locked));
        if (payload.lockAttacker()) {
            GunClientState.Entry e = GunClientState.get(payload.attackerId());
            if (e != null && e.state == com.mechaniciancombat.TkGun.State.MELEE) e.total += payload.ticks();
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity attacker = client.level.getEntity(payload.attackerId());
        for (int id : payload.targetIds()) {
            Entity target = client.level.getEntity(id); if (target == null) continue;
            float head = target instanceof LivingEntity living ? living.yHeadRot : target.getYRot();
            float body = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
            double angle = attacker != null && attacker != target ? Math.atan2(target.getZ()-attacker.getZ(),target.getX()-attacker.getX()) : client.level.random.nextDouble()*Math.PI*2;
            Freeze freeze = new Freeze(payload.ticks(),payload.ticks(),target.getX(),target.getY(),target.getZ(),target.getYRot(),target.getXRot(),head,body,Math.cos(angle)*.075D,Math.sin(angle)*.075D);
            Freeze previous = TARGETS.get(id);
            if (previous == null || freeze.remaining() >= previous.remaining()) TARGETS.put(id,freeze);
        }
        if (payload.cameraShake() && client.player != null && client.player.getId() == payload.attackerId()) {
            shakeTotal = payload.ticks()+1; shakeRemaining = shakeTotal;
            shakeIntensity = (payload.ticks() <= 1 ? .3F : payload.ticks() <= 3 ? .6F : 1F) * (payload.critical() ? 1.2F : 1F);
        }
    }
    public static boolean freezesAnimation(int id) { Hitstop stop = STOPS.get(id); return stop != null && stop.lock(); }
    public static Freeze frozenTarget(int id) { return TARGETS.get(id); }
    public static void applyCameraShake(PoseStack pose, float partial) {
        if (shakeRemaining <= 0 || shakeTotal <= 0) return;
        float progress = Math.max(0, (shakeRemaining-partial)/shakeTotal);
        float elapsed = shakeTotal-shakeRemaining+partial, magnitude = shakeIntensity*progress;
        pose.mulPose(Axis.XP.rotationDegrees((float)Math.sin(elapsed*7)*magnitude*.5F));
        pose.mulPose(Axis.ZP.rotationDegrees((float)Math.sin(elapsed*9)*magnitude));
    }
    public static void tick() {
        if (Minecraft.getInstance().level == null) { STOPS.clear(); TARGETS.clear(); shakeRemaining=0; return; }
        for (Iterator<Map.Entry<Integer, Hitstop>> it=STOPS.entrySet().iterator(); it.hasNext();) {
            var entry=it.next(); Hitstop old=entry.getValue();
            if (old.remaining()<=1) it.remove(); else entry.setValue(new Hitstop(old.remaining()-1,old.lock()));
        }
        for (Iterator<Map.Entry<Integer, Freeze>> it=TARGETS.entrySet().iterator(); it.hasNext();) {
            var entry=it.next(); Freeze old=entry.getValue();
            if (old.remaining()<=1) it.remove(); else entry.setValue(old.next());
        }
        if (shakeRemaining > 0) shakeRemaining--;
    }
    private MeleeImpactClientState() { }
}
