package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import com.mechaniciancombat.entity.HunterTurretEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 狩猎炮台模型（r774）。 */
public final class HunterTurretGeoModel extends GeoModel<HunterTurretEntity> {
    private static final ResourceLocation MODEL = TkGun.id("geo/entity/hunter_turret.geo.json");
    private static final ResourceLocation TEXTURE = TkGun.id("textures/entity/hunter_turret.png");
    private static final ResourceLocation ANIMATION =
            TkGun.id("animations/entity/hunter_turret.animation.json");

    @Override
    public ResourceLocation getModelResource(HunterTurretEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HunterTurretEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HunterTurretEntity animatable) {
        return ANIMATION;
    }
}
