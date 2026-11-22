package com.mechaniciancombat.mixin;

import com.mechaniciancombat.TkGun;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gun attack input is handled by the server-authoritative weapon controller. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GunMeleeAttackGuardMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void tkgun$suppressVanillaAttack(Player player, Entity target, CallbackInfo ci) {
        if (TkGun.holdingGun(player)) ci.cancel();
    }
}
