package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.item.MechanicianHunterSpearItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 部署时握在玩家手里的猎矛模型。 */
public final class HunterSpearHeldGeoModel extends GeoModel<MechanicianHunterSpearItem> {
    private static final ResourceLocation MODEL = TkGun.id("geo/entity/hunter_spear_deploy.geo.json");
    private static final ResourceLocation TEXTURE =
            TkGun.id("textures/entity/hunter_spear_deploy.png");
    private static final ResourceLocation ANIMATION =
            TkGun.id("animations/entity/hunter_spear_deploy.animation.json");

    @Override
    public ResourceLocation getModelResource(MechanicianHunterSpearItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MechanicianHunterSpearItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MechanicianHunterSpearItem animatable) {
        return ANIMATION;
    }
}
