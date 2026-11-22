package com.mechaniciancombat.mixin;
import com.mechaniciancombat.TkGun;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The held gun owns the pose; the physical sneak key remains available for focus input. */
@Mixin(Entity.class)
public abstract class GunCrouchPoseGuardMixin {
    @Inject(method="isCrouching()Z",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardCrouch(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof Player player && TkGun.holdingGun(player)) cir.setReturnValue(false);
    }
    @Inject(method="setShiftKeyDown(Z)V",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardShift(boolean down,CallbackInfo ci) {
        if (down && (Object)this instanceof Player player && TkGun.holdingGun(player)) ci.cancel();
    }
}
