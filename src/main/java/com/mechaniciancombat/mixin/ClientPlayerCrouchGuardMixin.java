package com.mechaniciancombat.mixin;
import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.client.SoulAnchorSkillClientState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持枪 / 灵魂锚施法期间压制原版蹲姿。
 *
 * <p>两件事必须一起做，否则姿态会打架：
 * <ol>
 *   <li><b>持枪</b>：蹲姿归模组自己的 `Focus_*` 动画管（`body.position.y = -8` 是作者画的下蹲），
 *       原版再压一次眼高会让镜头和模型错位；</li>
 *   <li><b>灵魂锚</b>：投掷/牵引/回收那几段接管了全身姿势，
 *       此时按住 sneak 会让原版又叠一个蹲姿 —— 用户看到的是"施法时人突然矮一截"。</li>
 * </ol>
 *
 * <p>⚠️ 上游把这两条写在 `ClientPlayerEntity` 的覆写上（`method_5715` / `method_18276`），
 * 因为 `ClientPlayerEntity` 会**遮蔽** `Entity` 的同名方法 —— 只拦 `Entity` 的话
 * 本地玩家的眼高与模型姿态选择都拦不到。这里同样必须拦 `LocalPlayer`（Forge 侧的对应类）。
 */
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerCrouchGuardMixin {
    @Inject(method="isShiftKeyDown()Z",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardShift(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (TkGun.holdingGun(player) || SoulAnchorSkillClientState.suppressSneakPose(player.getId())) {
            cir.setReturnValue(false);
        }
    }
    @Inject(method="isCrouching()Z",at=@At("HEAD"),cancellable=true)
    private void tkgun$guardCrouch(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (TkGun.holdingGun(player) || SoulAnchorSkillClientState.suppressSneakPose(player.getId())) {
            cir.setReturnValue(false);
        }
    }
}
