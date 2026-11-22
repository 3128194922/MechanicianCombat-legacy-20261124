package com.mechaniciancombat.item;

import com.mechaniciancombat.client.KineticSwordFirstPersonState;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 只用于动能剑第一人称渲染的隐藏控制器物品。 */
public final class KineticSwordFpaItem extends Item implements GeoItem {
	private static final Map<String, RawAnimation> RAW = new ConcurrentHashMap<>();
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

	public KineticSwordFpaItem(Properties properties) {
		super(properties);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "sword_fpa", 0, state -> {
			String name = KineticSwordFirstPersonState.animationName();
			if (name == null || name.isBlank()) {
				return PlayState.STOP;
			}
			return state.setAndContinue(rawFor(name));
		}).setAnimationSpeedHandler(item -> KineticSwordFirstPersonState.animationSpeed()));
	}

	private static RawAnimation rawFor(String name) {
		return RAW.computeIfAbsent(name, key -> isLooping(key)
				? RawAnimation.begin().thenLoop(key)
				: RawAnimation.begin().thenPlayAndHold(key));
	}

	private static boolean isLooping(String name) {
		return "idle".equals(name) || "GS_run".equals(name) || name.startsWith("GS_walk_");
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
