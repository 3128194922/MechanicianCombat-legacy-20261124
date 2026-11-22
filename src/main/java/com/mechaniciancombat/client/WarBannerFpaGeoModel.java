package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.WarBannerFpaItem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 仅第一人称技能动画使用的相机空间战旗模型。 */
public final class WarBannerFpaGeoModel extends GeoModel<WarBannerFpaItem> {
	private static final ResourceLocation MODEL =
			TkGun.id("geo/entity/war_banner_fpa.geo.json");
	private static final ResourceLocation TEXTURE =
			TkGun.id("textures/entity/war_banner_fpa.png");
	private static final ResourceLocation ANIMATION =
			TkGun.id("animations/entity/war_banner_fpa.animation.json");

	@Override
	public ResourceLocation getModelResource(WarBannerFpaItem animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(WarBannerFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(WarBannerFpaItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderType getRenderType(WarBannerFpaItem animatable, ResourceLocation texture) {
		return RenderType.entityCutoutNoCull(texture);
	}
}
