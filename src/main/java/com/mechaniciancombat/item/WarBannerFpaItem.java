package com.mechaniciancombat.item;

import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 隐藏的运行时物品，唯一用途是驱动战旗第一人称（FPA）控制器。 */
public final class WarBannerFpaItem extends Item implements GeoItem {
	private static final RawAnimation FPA =
			RawAnimation.begin().thenPlayAndHold("skill_flag_fpa");
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public WarBannerFpaItem(Properties properties) {
		super(properties);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "fpa", 0,
				state -> state.setAndContinue(FPA))
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
