package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.MeleeImpactClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MeleeEntityFreezeRenderMixin {
    @Unique private Snapshot tkgun$snapshot;
    @Unique private record Snapshot(double x,double y,double z,double xo,double yo,double zo,
            float yaw,float pitch,float oldYaw,float oldPitch,float head,float body,float oldHead,float oldBody) { }
    @Inject(method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("HEAD"))
    private void tkgun$freeze(Entity e,double x,double y,double z,float partial,PoseStack pose,MultiBufferSource buffers,CallbackInfo ci) {
        var state = MeleeImpactClientState.frozenTarget(e.getId());
        if (state == null) { tkgun$snapshot = null; return; }
        LivingEntity living = e instanceof LivingEntity l ? l : null;
        tkgun$snapshot = new Snapshot(e.getX(),e.getY(),e.getZ(),e.xo,e.yo,e.zo,e.getYRot(),e.getXRot(),e.yRotO,e.xRotO,
                living == null ? e.getYRot() : living.yHeadRot,living == null ? e.getYRot() : living.yBodyRot,
                living == null ? e.yRotO : living.yHeadRotO,living == null ? e.yRotO : living.yBodyRotO);
        double shake=state.shake(partial), fx=state.x()+state.shakeX()*shake, fz=state.z()+state.shakeZ()*shake;
        e.setPos(fx,state.y(),fz); e.xo=fx; e.yo=state.y(); e.zo=fz;
        e.setYRot(state.yaw()); e.setXRot(state.pitch()); e.yRotO=state.yaw(); e.xRotO=state.pitch();
        if (living != null) { living.yHeadRot=state.headYaw(); living.yBodyRot=state.bodyYaw(); living.yHeadRotO=state.headYaw(); living.yBodyRotO=state.bodyYaw(); }
    }
    @Inject(method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("TAIL"))
    private void tkgun$restore(Entity e,double x,double y,double z,float partial,PoseStack pose,MultiBufferSource buffers,CallbackInfo ci) {
        Snapshot s=tkgun$snapshot; tkgun$snapshot=null; if (s==null) return;
        e.setPos(s.x(),s.y(),s.z()); e.xo=s.xo(); e.yo=s.yo(); e.zo=s.zo();
        e.setYRot(s.yaw()); e.setXRot(s.pitch()); e.yRotO=s.oldYaw(); e.xRotO=s.oldPitch();
        if (e instanceof LivingEntity living) { living.yHeadRot=s.head(); living.yBodyRot=s.body(); living.yHeadRotO=s.oldHead(); living.yBodyRotO=s.oldBody(); }
    }
}
