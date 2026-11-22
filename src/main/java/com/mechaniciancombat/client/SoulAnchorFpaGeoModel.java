package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.SoulAnchorFpaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 相机空间灵魂锚模型（ready / throw）。 */
public final class SoulAnchorFpaGeoModel extends GeoModel<SoulAnchorFpaItem> {
	private static final ResourceLocation MODEL = TkGun.id("geo/entity/soul_anchor_fpa.geo.json");
	private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/soul_anchor_fpa.png");
	private static final ResourceLocation ANIMATION =
			TkGun.id("animations/entity/soul_anchor_fpa.animation.json");

	@Override
	public ResourceLocation getModelResource(SoulAnchorFpaItem animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(SoulAnchorFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(SoulAnchorFpaItem animatable) {
		return ANIMATION;
	}
}
