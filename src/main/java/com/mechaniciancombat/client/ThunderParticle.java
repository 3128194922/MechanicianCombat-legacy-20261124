package com.mechaniciancombat.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/** Authored seven-frame Veritas thunder flash. */
public final class ThunderParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private ThunderParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites) {
        super(level,x,y,z); this.sprites=sprites; lifetime=20; quadSize=6; xd=yd=zd=0; gravity=0; hasPhysics=false; updateSprite();
    }
    private void updateSprite() { setSprite(sprites.get(Math.min(6,age/2),7)); }
    @Override public void tick() { super.tick(); updateSprite(); }
    @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer out, net.minecraft.client.Camera camera,float partial) {
        var cam=camera.getPosition();
        float px=(float)(net.minecraft.util.Mth.lerp(partial,xo,x)-cam.x);
        float py=(float)(net.minecraft.util.Mth.lerp(partial,yo,y)-cam.y);
        float pz=(float)(net.minecraft.util.Mth.lerp(partial,zo,z)-cam.z);
        float half=getQuadSize(partial); double yaw=Math.toRadians(camera.getYRot());
        float rx=(float)Math.cos(yaw), rz=(float)Math.sin(yaw);
        vertex(out,px-rx*half,py-half,pz-rz*half,getU0(),getV1());
        vertex(out,px+rx*half,py-half,pz+rz*half,getU1(),getV1());
        vertex(out,px+rx*half,py+half,pz+rz*half,getU1(),getV0());
        vertex(out,px-rx*half,py+half,pz-rz*half,getU0(),getV0());
    }
    private void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,float x,float y,float z,float u,float v) {
        out.vertex(x,y,z).uv(u,v).color(rCol,gCol,bCol,alpha).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override protected int getLightColor(float partial) { return LightTexture.FULL_BRIGHT; }
    public record Factory(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public ThunderParticle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
            return new ThunderParticle(level,x,y,z,sprites);
        }
    }
}
