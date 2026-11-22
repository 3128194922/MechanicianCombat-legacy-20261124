package com.mechaniciancombat.item;

import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 只用于第一人称灵魂锚模型的隐藏控制器物品。 */
public final class SoulAnchorFpaItem extends Item implements GeoItem {
	private static final RawAnimation READY = RawAnimation.begin().thenPlayAndHold("ready");
	private static final RawAnimation THROW = RawAnimation.begin().thenPlayAndHold("throw");
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

	public SoulAnchorFpaItem(Properties properties) {
		super(properties);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "anchor", 0,
				state -> switch (SoulAnchorFpaRenderState.mode()) {
					case READY -> state.setAndContinue(READY);
					case THROW -> state.setAndContinue(THROW);
					case NONE -> PlayState.STOP;
				})
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
