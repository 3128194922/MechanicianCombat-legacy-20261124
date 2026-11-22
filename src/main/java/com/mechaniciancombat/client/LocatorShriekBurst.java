package com.mechaniciancombat.client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.world.phys.Vec3;

/** Ten simultaneous shriek sprites arranged in the authored small ring. */
public final class LocatorShriekBurst {
    public static void start(int playerId, Vec3 origin) {
        var client=Minecraft.getInstance();
        if (client.level==null || client.level.getEntity(playerId)==null) return;
        for (int i=0;i<10;i++) {
            double angle=Math.PI*2*i/10, radius=.16D+(i%3)*.08D;
            client.level.addParticle(new ShriekParticleOption(0),origin.x+Math.cos(angle)*radius,
                    origin.y+(i%2)*.04D,origin.z+Math.sin(angle)*radius,0,0,0);
        }
    }
    private LocatorShriekBurst() { }
}
