package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.GunClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Fortress effect planes use their own 160px atlas, while the gun uses a 64px atlas. */
public final class BowgunEffectSkillLayer extends GeoRenderLayer<BowgunRig> {
    private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/bowgun_effects.png");
    public BowgunEffectSkillLayer(GeoRenderer<BowgunRig> renderer) { super(renderer); }
    public static boolean isEffect(String name) { return name.equals("step_1") || name.equals("step_2") || name.equals("step_3") || name.equals("step_4"); }
    @Override public void renderForBone(PoseStack pose, BowgunRig animation, GeoBone bone, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, float partial, int light, int overlay) {
        var player = BowgunRig.CURRENT_PLAYER;
        var state = player == null ? null : GunClientState.get(player.getId());
        if (!isEffect(bone.getName()) || state == null || !state.active() || state.state != TkGun.State.FORTRESS_SKILL_HIT) return;
        VertexConsumer effect = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE, false));
        for (GeoCube cube : bone.getCubes()) {
            pose.pushPose();
            renderer.renderCube(pose, rescale(cube), effect, LightTexture.FULL_BRIGHT, overlay, 1,1,1,1);
            pose.popPose();
        }
    }
    private static GeoCube rescale(GeoCube cube) {
        GeoQuad[] source = cube.quads(), result = new GeoQuad[source.length];
        boolean flat = Math.abs(cube.size().z) < 1.0E-4D;
        for (int i=0; i<source.length; i++) {
            GeoQuad q=source[i]; if (q==null || flat && q.direction()!=Direction.SOUTH) continue;
            GeoVertex[] vertices=q.vertices(), scaled=new GeoVertex[vertices.length];
            for (int j=0; j<vertices.length; j++) scaled[j]=vertices[j].withUVs(vertices[j].texU()*.4F, vertices[j].texV()*.4F);
            result[i]=new GeoQuad(scaled,q.normal(),q.direction());
        }
        return new GeoCube(result,cube.pivot(),cube.rotation(),cube.size(),cube.inflate(),cube.mirror());
    }
}
