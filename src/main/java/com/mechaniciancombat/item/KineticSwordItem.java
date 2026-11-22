package com.mechaniciancombat.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

/**
 * 动能剑：近战型“枪械”。
 *
 * <p>注册为 {@link BowgunItem} 子类以复用枪械状态机、玩家 rig 与第一人称渲染；
 * 右键切换“立场”（不是瞄准/射击），左键走近战输入。
 *
 * <p>注意：本类不走 {@link BowgunItem#use}，否则原版会把右键当成“使用物品”，
 * 产生挥手动作并让 {@code isUsingItem()} 干扰翻滚/移动判定。
 */
public final class KineticSwordItem extends BowgunItem {
	public KineticSwordItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		// CONSUME 会消费右键（不会落到副手），但 shouldSwing() 为 false，不会播原版挥手。
		// 立场切换由客户端按右键边沿发送 KineticStancePayload（见 TkGunClient）。
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		// 对着方块右键也不进入原版“使用物品”状态；立场输入由客户端独立上报。
		return InteractionResult.PASS;
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 0;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}
}
