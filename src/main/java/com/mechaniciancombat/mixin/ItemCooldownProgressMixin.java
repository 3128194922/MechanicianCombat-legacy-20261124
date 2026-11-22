package com.mechaniciancombat.mixin;
import com.mechaniciancombat.client.FortressSkillCooldownClient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownProgressMixin {
    @Inject(method="getCooldownPercent", at=@At("RETURN"), cancellable=true)
    private void tkgun$cooldown(Item item,float partial,CallbackInfoReturnable<Float> cir) {
        float value=FortressSkillCooldownClient.progress(item,partial);
        if (!Float.isNaN(value)) cir.setReturnValue(value);
    }
}
