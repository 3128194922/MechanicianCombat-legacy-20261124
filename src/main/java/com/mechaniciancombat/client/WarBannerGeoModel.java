package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.entity.WarBannerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 战旗实体模型（r774 素材）。 */
public final class WarBannerGeoModel extends GeoModel<WarBannerEntity> {
    private static final ResourceLocation MODEL = TkGun.id("geo/entity/war_banner.geo.json");
    private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/war_banner.png");
    private static final ResourceLocation ANIMATION =
            TkGun.id("animations/entity/war_banner.animation.json");

    @Override
    public ResourceLocation getModelResource(WarBannerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WarBannerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WarBannerEntity animatable) {
        return ANIMATION;
    }
}
