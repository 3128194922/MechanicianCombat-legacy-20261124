package com.mechaniciancombat.mixin;
import com.mechaniciancombat.item.BackpackLauncherRules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class OffhandUseGuardClientMixin {
    @Inject(method="useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardStoredAmmo(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
        if (hand==InteractionHand.OFF_HAND && BackpackLauncherRules.hasActiveLauncher(player)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
