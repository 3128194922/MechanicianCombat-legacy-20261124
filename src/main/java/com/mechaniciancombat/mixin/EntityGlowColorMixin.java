package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.SoulAnchorMarkClientState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 灵魂锚标记期间，发光描边使用作者给的颜色，而不是默认队伍色。 */
@Mixin(Entity.class)
public abstract class EntityGlowColorMixin {
	@Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
	private void tkgun$anchorGlowColor(CallbackInfoReturnable<Integer> cir) {
		Entity entity = (Entity) (Object) this;
		int color = SoulAnchorMarkClientState.glowColor(entity.getId());
		if (color >= 0) {
			cir.setReturnValue(color);
		}
	}
}
