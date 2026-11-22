package com.mechaniciancombat.mixin;
import com.mechaniciancombat.TkGun;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The weapon controller owns left click and its animation while a gun is held. */
@Mixin(Minecraft.class)
public abstract class GunVanillaAttackGuardMixin {
    @Inject(method="startAttack()Z",at=@At("HEAD"),cancellable=true)
    private void tkgun$attack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client=(Minecraft)(Object)this;
        if (client.player!=null && TkGun.holdingGun(client.player)) cir.setReturnValue(false);
    }
    @Inject(method="continueAttack(Z)V",at=@At("HEAD"),cancellable=true)
    private void tkgun$break(boolean breaking,CallbackInfo ci) {
        Minecraft client=(Minecraft)(Object)this;
        if (client.player!=null && TkGun.holdingGun(client.player)) ci.cancel();
    }
}
