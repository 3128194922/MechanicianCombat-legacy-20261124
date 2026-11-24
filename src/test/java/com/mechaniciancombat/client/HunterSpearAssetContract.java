package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 猎矛/炮台资源契约：模型、动画、贴图、物品模型、玩家动作必须齐全。
 *
 * <p>缺段不会崩，但会表现为"炮台不动 / 猎矛不显示 / 部署无动作"。
 */
public final class HunterSpearAssetContract {
    public static void main(String[] args) throws Exception {
        checkAnimation("/assets/tkgun/animations/entity/hunter_turret.animation.json",
                "/assets/tkgun/geo/entity/hunter_turret.geo.json",
                new String[]{"summon", "idle", "fire_1", "fire_2"});
        checkAnimation("/assets/tkgun/animations/entity/hunter_spear_deploy.animation.json",
                "/assets/tkgun/geo/entity/hunter_spear_deploy.geo.json",
                new String[]{"skill_flag"});
        checkAnimation("/assets/tkgun/player_animations/hunter_spear_deploy.json",
                null, new String[]{"hunter_spear_deploy"});
        checkPlayerAnimation();
        // 第一人称（相机空间）猎矛：缺了它部署时第一人称会看不到枪。
        checkAnimation("/assets/tkgun/animations/entity/hunter_anchor_fpa.animation.json",
                "/assets/tkgun/geo/entity/hunter_anchor_fpa.geo.json",
                new String[]{"fpa"});

        for (String path : new String[]{
                "/assets/tkgun/textures/entity/hunter_turret.png",
                "/assets/tkgun/textures/entity/hunter_spear_deploy.png",
                "/assets/tkgun/textures/item/mechanician_hunter_spear.png",
                "/assets/tkgun/models/item/hunter_spear.json",
                "/assets/tkgun/sounds/hunter_turret_fire.ogg"}) {
            if (HunterSpearAssetContract.class.getResource(path) == null) {
                throw new AssertionError("missing resource: " + path);
            }
        }
        System.out.println("HunterSpearAssetContract: turret and spear assets aligned");
    }

    private static void checkAnimation(String animationPath, String geoPath, String[] required)
            throws Exception {
        JsonObject animations = read(animationPath).getAsJsonObject("animations");
        Set<String> bones = new HashSet<>();
        if (geoPath != null) {
            JsonObject geometry = read(geoPath).getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject();
            for (JsonElement bone : geometry.getAsJsonArray("bones")) {
                bones.add(bone.getAsJsonObject().get("name").getAsString());
            }
        }
        for (String name : required) {
            if (!animations.has(name)) {
                throw new AssertionError("missing animation: " + name + " in " + animationPath);
            }
            if (geoPath == null) continue;
            JsonObject clipBones = animations.getAsJsonObject(name).getAsJsonObject("bones");
            if (clipBones == null) continue;
            for (String bone : clipBones.keySet()) {
                if (!bones.contains(bone)) {
                    throw new AssertionError(name + " references unknown bone " + bone
                            + " in " + animationPath);
                }
            }
        }
    }

    /**
     * PlayerAnimator 必须能把作者片段解析成原生动画数据。
     *
     * <p>这条路径同时覆盖 GeckoLib 的坐标转换；只剩物品动画通常就是这里失效。
     */
    private static void checkPlayerAnimation() throws Exception {
        try (var stream = HunterSpearAssetContract.class.getResourceAsStream(
                "/assets/tkgun/player_animations/hunter_spear_deploy.json")) {
            if (stream == null) {
                throw new AssertionError("missing player animation");
            }
            KeyframeAnimation clip = null;
            for (KeyframeAnimation animation : AnimationSerializing.deserializeAnimation(stream)) {
                if ("hunter_spear_deploy".equals(animation.extraData.get("name"))) {
                    clip = animation;
                    break;
                }
            }
            if (clip == null) {
                throw new AssertionError("PlayerAnimator could not read hunter_spear_deploy");
            }
            if (clip.endTick != 23) {
                throw new AssertionError("hunter_spear_deploy length changed: " + clip.endTick);
            }
            for (String part : new String[]{"torso", "rightArm", "leftArm"}) {
                if (clip.getPartOptional(part).isEmpty()) {
                    throw new AssertionError("hunter_spear_deploy missing part: " + part);
                }
            }
        }
    }

    private static JsonObject read(String path) throws Exception {
        try (var stream = HunterSpearAssetContract.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private HunterSpearAssetContract() { }
}
