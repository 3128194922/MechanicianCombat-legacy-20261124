package com.mechaniciancombat.client.rig;

import com.mechaniciancombat.TkGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Selects resources from the gun held by the player currently being rendered. */
final class GunVisual {
    private static String variant() {
        var player = BowgunRig.CURRENT_PLAYER;
        if (player == null || !TkGun.isGun(player.getMainHandItem())) return "bowgun";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
        if (id != null && id.getNamespace().equals(TkGun.MOD_ID)
                && (id.getPath().equals("abyssal_ancient_cannon") || id.getPath().equals("veritas_et_justitia")
                || id.getPath().equals("kinetic_sword"))) return id.getPath();
        return "bowgun";
    }
    static ResourceLocation model() { return TkGun.id("geo/entity/" + variant() + "_rig.geo.json"); }
    static ResourceLocation animation() { return TkGun.id("animations/entity/" + variant() + "_rig.animation.json"); }
    static ResourceLocation texture() {
        if (variant().equals("kinetic_sword")) {
            return TkGun.id("textures/entity/kinetic_sword_sword.png");
        }
        return TkGun.id("textures/entity/" + variant() + "_gun.png");
    }
    static boolean abyssal() { return variant().equals("abyssal_ancient_cannon"); }
    /** 当前渲染的玩家手上那把枪的变体名（`bowgun` 为默认）。 */
    static String variantName() { return variant(); }
    /** 只有作者给 `open` / `close` 折叠-展开片段的枪械（`abyssal` / `veritas`）。 */
    static boolean hasGunPoseClips() {
        return GunPoseRules.hasGunPoseClips(variant());
    }
    private GunVisual() { }
}
