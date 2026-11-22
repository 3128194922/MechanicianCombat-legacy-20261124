package com.mechaniciancombat.mixin;

import com.mechaniciancombat.client.MeleeImpactClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MeleeInputFreezeMixin {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void tkgun$freezeInput(boolean slow, float factor, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.player.input != (Object) this) {
            return;
        }
        KeyboardInput input = (KeyboardInput) (Object) this;
        // ① 命中定格：完全冻结输入（原样保留）。
        if (MeleeImpactClientState.freezesAnimation(client.player.getId())) {
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            return;
        }
        // ② 近战动作期间：按作者曲线缩放输入（上游 `MeleeMovementSlowdown`）。
        //    本地此前没有这一步，近战全程保持满速移动 —— 与"重劈时脚下被拖住"的观感不符。
        float scale = com.mechaniciancombat.client.MeleeMovementSlowdown
                .inputMultiplier(client.player.getId());
        if (scale < 1.0F) {
            input.forwardImpulse *= scale;
            input.leftImpulse *= scale;
        }
    }
}
