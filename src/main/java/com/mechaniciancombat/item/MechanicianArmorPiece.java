package com.mechaniciancombat.item;

import com.mechaniciancombat.TkGun;
import net.minecraft.resources.ResourceLocation;

public enum MechanicianArmorPiece {
    UNDERSUIT("undersuit"), HELMET("helmet"), CHESTPLATE("chestplate"), LEGGINGS("leggings"), BOOTS("boots");
    private final String modelName;
    MechanicianArmorPiece(String modelName) { this.modelName = modelName; }
    public ResourceLocation modelLocation() { return TkGun.id("armor_models/" + modelName + ".bbmodel"); }
    public ResourceLocation textureLocation() { return TkGun.id("textures/entity/mechanician_" + modelName + ".png"); }
    public String modelName() { return modelName; }
}
