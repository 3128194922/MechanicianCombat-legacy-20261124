package com.mechaniciancombat.client.entity;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.entity.SoulAnchorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 灵魂锚投射物的 bbmodel 几何与贴图。 */
public final class SoulAnchorProjectileGeoModel extends GeoModel<SoulAnchorEntity> {
	@Override
	public ResourceLocation getModelResource(SoulAnchorEntity animatable) {
		return TkGun.id("geo/entity/soul_anchor_projectile.geo.json");
	}

	@Override
	public ResourceLocation getTextureResource(SoulAnchorEntity animatable) {
		return TkGun.id("textures/item/soul_anchor_projectile.png");
	}

	@Override
	public ResourceLocation getAnimationResource(SoulAnchorEntity animatable) {
		return TkGun.id("animations/entity/soul_anchor_projectile.animation.json");
	}
}
