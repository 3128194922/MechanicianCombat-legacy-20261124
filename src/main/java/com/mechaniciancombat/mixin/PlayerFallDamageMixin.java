package com.mechaniciancombat.mixin;
import com.mechaniciancombat.TkGunServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerFallDamageMixin {
    @Inject(method="calculateFallDamage(FF)I",at=@At("RETURN"),cancellable=true)
    private void tkgun$reduceFall(float distance,float multiplier,CallbackInfoReturnable<Integer> cir) {
        if ((Object)this instanceof ServerPlayer player && cir.getReturnValueI()>0)
            cir.setReturnValue(TkGunServer.applyAirborneLandingDamageReduction(player,cir.getReturnValueI()));
    }
}
