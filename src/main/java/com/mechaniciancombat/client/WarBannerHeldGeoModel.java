package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.BackpackPartItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 施法时握在玩家手里的战旗模型（只含旗杆子树）。 */
public final class WarBannerHeldGeoModel extends GeoModel<BackpackPartItem> {
    private static final ResourceLocation MODEL = TkGun.id("geo/entity/war_banner_held.geo.json");
    private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/war_banner_held.png");
    private static final ResourceLocation ANIMATION =
            TkGun.id("animations/entity/war_banner_held.animation.json");

    @Override
    public ResourceLocation getModelResource(BackpackPartItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(BackpackPartItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(BackpackPartItem animatable) {
        return ANIMATION;
    }
}
