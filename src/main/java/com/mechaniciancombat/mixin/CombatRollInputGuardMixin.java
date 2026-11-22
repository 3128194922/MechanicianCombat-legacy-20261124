package com.mechaniciancombat.mixin;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.GunClientState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Combat Roll from physically rolling while the gun state machine owns the dodge sequence. */
@Pseudo
@Mixin(targets = "net.combatroll.internals.RollManager")
public abstract class CombatRollInputGuardMixin {
	@Shadow(remap = false) public boolean isEnabled;
	@Shadow(remap = false) private int availableRolls;
	@Shadow(remap = false) public abstract boolean isRolling();
	@Inject(method = "isRollAvailable", remap = false, at = @At("HEAD"), cancellable = true)
	private void tkgun$guardDodgeSequence(Player player,
										  CallbackInfoReturnable<Boolean> cir) {
		if (player instanceof LocalPlayer && (TkGun.holdingGun(player)
				|| com.mechaniciancombat.item.MechanicianUndersuitItem.canUseArmorDodge(player))
				&& GunClientState.combatRollSuppressed(player.getId())) {
			cir.setReturnValue(false);
			return;
		}
		var state = GunClientState.get(player.getId());
		var slow = player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
		if (player instanceof LocalPlayer && TkGun.holdingGun(player) && state != null
				&& (state.state == TkGun.State.OVER || state.state == TkGun.State.OVERHEAT)
				&& slow != null && slow.getAmplifier() == TkGun.SLOW_ON_EXIT_AMPLIFIER
				&& player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 0
				&& isEnabled && !isRolling() && availableRolls > 0
				&& !((net.combatroll.mixin.PlayerEntityAccessor) player).invokeIsImmobile_CombatRoll()
				&& player.isEffectiveAi()) {
			// Allow only the mod's temporary slow to be interrupted, never bypass charges.
			cir.setReturnValue(true);
		}
	}
}
