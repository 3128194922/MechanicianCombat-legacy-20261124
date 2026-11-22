package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.MeleeImpactClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MeleeCameraShakeMixin {
    @Inject(method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
    private void tkgun$shake(PoseStack pose, float partial, CallbackInfo ci) {
        MeleeImpactClientState.applyCameraShake(pose, partial);
    }
}
