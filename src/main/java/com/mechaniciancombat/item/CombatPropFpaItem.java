package com.mechaniciancombat.item;

import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 猎矛锚等第一人称道具的隐藏控制器物品。 */
public final class CombatPropFpaItem extends Item implements GeoItem {
	private static final RawAnimation HUNTER_ANCHOR =
			RawAnimation.begin().thenPlayAndHold("fpa");
	private static final RawAnimation READY = RawAnimation.begin().thenPlayAndHold("ready");
	private static final RawAnimation AIM = RawAnimation.begin().thenLoop("aim");
	private static final RawAnimation CD = RawAnimation.begin().thenPlayAndHold("CD");
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

	public CombatPropFpaItem(Properties properties) {
		super(properties);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "fpa", 0,
				state -> switch (CombatPropFpaRenderState.mode()) {
					case HUNTER_ANCHOR -> state.setAndContinue(HUNTER_ANCHOR);
					case READY -> state.setAndContinue(READY);
					case AIM -> state.setAndContinue(AIM);
					case CD -> state.setAndContinue(CD);
					case NONE -> PlayState.STOP;
				})
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
