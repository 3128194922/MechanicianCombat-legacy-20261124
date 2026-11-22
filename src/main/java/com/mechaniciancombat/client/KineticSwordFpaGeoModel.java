package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.KineticSwordFpaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 复用动能剑 rig 的几何、贴图与动画。 */
public final class KineticSwordFpaGeoModel extends GeoModel<KineticSwordFpaItem> {
	private static final ResourceLocation MODEL = TkGun.id("geo/entity/kinetic_sword_rig.geo.json");
	private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/kinetic_sword_sword.png");
	private static final ResourceLocation ANIMATION =
			TkGun.id("animations/entity/kinetic_sword_rig.animation.json");

	@Override
	public ResourceLocation getModelResource(KineticSwordFpaItem animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(KineticSwordFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(KineticSwordFpaItem animatable) {
		return ANIMATION;
	}
}
