package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.CombatPropFpaItem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 单个作者导出的第一人称道具的资源包。 */
public final class CombatPropFpaGeoModel extends GeoModel<CombatPropFpaItem> {
	private final ResourceLocation model;
	private final ResourceLocation texture;
	private final ResourceLocation animation;

	public CombatPropFpaGeoModel(String assetName) {
		model = TkGun.id("geo/entity/" + assetName + ".geo.json");
		texture = TkGun.id("textures/entity/" + assetName + ".png");
		animation = TkGun.id("animations/entity/" + assetName + ".animation.json");
	}

	@Override
	public ResourceLocation getModelResource(CombatPropFpaItem animatable) {
		return model;
	}

	@Override
	public ResourceLocation getTextureResource(CombatPropFpaItem animatable) {
		return texture;
	}

	@Override
	public ResourceLocation getAnimationResource(CombatPropFpaItem animatable) {
		return animation;
	}

	@Override
	public RenderType getRenderType(CombatPropFpaItem animatable, ResourceLocation currentTexture) {
		return RenderType.entityCutoutNoCull(currentTexture);
	}
}
