package com.mechaniciancombat.mixin;
import com.mechaniciancombat.item.BackpackLauncherRules;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class OffhandUseGuardMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardStoredAmmo(ServerboundUseItemPacket packet,CallbackInfo ci) {
        if (packet.getHand()==InteractionHand.OFF_HAND && BackpackLauncherRules.hasActiveLauncher(player)) ci.cancel();
    }
}
