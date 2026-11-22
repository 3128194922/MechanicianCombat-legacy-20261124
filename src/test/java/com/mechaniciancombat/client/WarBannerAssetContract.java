package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 战旗资源契约：实体模型/动画、手持模型/动画、粒子贴图、音效映射必须齐全。
 *
 * <p>缺动画或音效不会崩，但会表现为"旗子不动 / 施法无声"，
 * 这类问题在静态检查里最容易漏。
 */
public final class WarBannerAssetContract {
    public static void main(String[] args) throws Exception {
        checkAnimation("/assets/tkgun/animations/entity/war_banner.animation.json",
                "/assets/tkgun/geo/entity/war_banner.geo.json",
                new String[]{"idle", "close", "open"});
        checkAnimation("/assets/tkgun/animations/entity/war_banner_held.animation.json",
                "/assets/tkgun/geo/entity/war_banner_held.geo.json",
                new String[]{"skill_flag", "skill_flag_gun"});
        // 第一人称（相机空间）战旗：缺了它施法时第一人称会看不到旗子。
        checkAnimation("/assets/tkgun/animations/entity/war_banner_fpa.animation.json",
                "/assets/tkgun/geo/entity/war_banner_fpa.geo.json",
                new String[]{"skill_flag_fpa"});
        checkResource("/assets/tkgun/textures/entity/war_banner_fpa.png");
        checkResource("/assets/tkgun/models/item/war_banner_fpa_model.json");
        checkAnimation("/assets/tkgun/player_animations/flag_skill.json",
                null, new String[]{"flag_skill"});

        // 施法音效时间表里的每个 key 都必须能在关键帧映射里查到。
        for (String key : TkGun.WAR_BANNER_SOUND_KEYS) {
            if (!TkGun.KEYFRAME_SOUNDS.containsKey(key)) {
                throw new AssertionError("war banner sound not mapped: " + key);
            }
        }
        if (TkGun.WAR_BANNER_SOUND_TICKS.length != TkGun.WAR_BANNER_SOUND_KEYS.length) {
            throw new AssertionError("war banner sound ticks/keys length mismatch");
        }

        // 粒子定义与贴图序列。
        if (WarBannerAssetContract.class.getResource(
                "/assets/tkgun/particles/war_banner_spark.json") == null) {
            throw new AssertionError("missing war_banner_spark particle definition");
        }
        for (int i = 0; i < 8; i++) {
            String texture = "/assets/tkgun/textures/particle/war_banner_spark_" + i + ".png";
            if (WarBannerAssetContract.class.getResource(texture) == null) {
                throw new AssertionError("missing particle texture: " + texture);
            }
        }
        for (String texture : new String[]{
                "/assets/tkgun/textures/entity/war_banner.png",
                "/assets/tkgun/textures/entity/war_banner_held.png",
                "/assets/tkgun/textures/entity/war_banner_effect.png",
                "/assets/tkgun/textures/item/war_banner.png"}) {
            if (WarBannerAssetContract.class.getResource(texture) == null) {
                throw new AssertionError("missing texture: " + texture);
            }
        }
        System.out.println("WarBannerAssetContract: banner models, clips, particles and sounds aligned");
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
            JsonObject clip = animations.getAsJsonObject(name);
            JsonObject clipBones = clip.getAsJsonObject("bones");
            if (clipBones == null) continue;
            for (String bone : clipBones.keySet()) {
                // 作者手持片段里引用了玩家 rig 的 gun 骨，手持 geo 没有它是正常的。
                if ("gun".equals(bone)) continue;
                if (!bones.contains(bone)) {
                    throw new AssertionError(name + " references unknown bone " + bone
                            + " in " + animationPath);
                }
            }
        }
    }

    private static JsonObject read(String path) throws Exception {
        try (var stream = WarBannerAssetContract.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static void checkResource(String path) {
        if (WarBannerAssetContract.class.getResource(path) == null) {
            throw new AssertionError("missing resource: " + path);
        }
    }

    private WarBannerAssetContract() { }
}
