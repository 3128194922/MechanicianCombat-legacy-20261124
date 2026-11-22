package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 战旗手持道具的姿态采样：直接读作者 `war_banner_held.animation.json` 的骨骼通道。
 *
 * <p>不交给 GeckoLib 控制器，是因为这个道具只在施法期间由玩家渲染器手动驱动；
 * 手写采样能精确按施法秒数取帧，也便于命中冻结时暂停。
 */
final class WarBannerHeldPose {
    private static final ResourceLocation RESOURCE =
            TkGun.id("animations/entity/war_banner_held.animation.json");
    private static final ResourceLocation ANCHOR_RESOURCE =
            TkGun.id("animations/entity/soul_anchor_fpa.animation.json");
    /** 第一人称战旗的相机空间片段（作者单独导出的 `war_banner_fpa`）。 */
    private static final ResourceLocation FPA_RESOURCE =
            TkGun.id("animations/entity/war_banner_fpa.animation.json");
    /** 第一人称猎矛的相机空间片段（作者单独导出的 `hunter_anchor_fpa`）。 */
    private static final ResourceLocation HUNTER_FPA_RESOURCE =
            TkGun.id("animations/entity/hunter_anchor_fpa.animation.json");
    /** 背包炮台第一人称的两套片段（便携 / 自动）。 */
    private static final ResourceLocation PORTABLE_TURRET_RESOURCE =
            TkGun.id("animations/entity/portable_turret_fpa.animation.json");
    private static final ResourceLocation AUTO_TURRET_RESOURCE =
            TkGun.id("animations/entity/auto_turret_fpa.animation.json");
    private static Map<String, Map<String, BoneChannels>> animationBones;
    private static Map<String, Map<String, BoneChannels>> anchorBones;
    private static Map<String, Map<String, BoneChannels>> fpaBones;
    private static Map<String, Map<String, BoneChannels>> hunterFpaBones;
    private static Map<String, Map<String, BoneChannels>> portableTurretBones;
    private static Map<String, Map<String, BoneChannels>> autoTurretBones;

    static void clearCache() {
        animationBones = null;
        anchorBones = null;
        fpaBones = null;
        hunterFpaBones = null;
        portableTurretBones = null;
        autoTurretBones = null;
    }

    static void apply(GeoBone bone, double seconds, boolean gunHeld) {
        apply(bone, seconds, animationBones(), gunHeld ? "skill_flag_gun" : "skill_flag");
    }

    /** 灵魂锚第一人称模型的 ready/throw 姿态。 */
    static void applyAnchorFirstPerson(GeoBone bone, double seconds, boolean throwing) {
        apply(bone, seconds, anchorBones(), throwing ? "throw" : "ready");
    }

    /** 战旗第一人称模型：按作者 `war_banner_fpa` 片段采样。 */
    static void applyFirstPerson(GeoBone bone, double seconds) {
        apply(bone, seconds, fpaBones(), "skill_flag_fpa");
    }

    /** 猎矛第一人称模型：按作者 `hunter_anchor_fpa` 的 `fpa` 片段采样。 */
    static void applyHunterFirstPerson(GeoBone bone, double seconds) {
        apply(bone, seconds, hunterFpaBones(), "fpa");
    }

    /** 背包炮台第一人称：`READY / AIM / CD` 三档。 */
    static void applyTurretFirstPerson(GeoBone bone, double seconds,
                                       com.mechaniciancombat.item.CombatPropFpaRenderState.Mode mode) {
        String clip = switch (mode) {
            case AIM -> "aim";
            case CD -> "CD";
            default -> "ready";
        };
        // 便携与自动两套几何骨骼同名（C3），片段也同名，任取其一即可；
        // 这里按当前渲染器挂载的资源分别缓存，避免串用。
        Map<String, Map<String, BoneChannels>> table =
                com.mechaniciancombat.client.CombatPropFirstPersonRenderer.currentTurretAsset()
                        .equals("auto_turret_fpa") ? autoTurretBones() : portableTurretBones();
        apply(bone, seconds, table, clip);
    }

    private static void apply(GeoBone bone, double seconds,
                              Map<String, Map<String, BoneChannels>> table, String animationName) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        if (initial != null) {
            bone.setPosX(initial.getOffsetX());
            bone.setPosY(initial.getOffsetY());
            bone.setPosZ(initial.getOffsetZ());
            bone.setRotX(initial.getRotX());
            bone.setRotY(initial.getRotY());
            bone.setRotZ(initial.getRotZ());
            bone.setScaleX(initial.getScaleX());
            bone.setScaleY(initial.getScaleY());
            bone.setScaleZ(initial.getScaleZ());
        }
        BoneChannels channels = channels(table, animationName, bone.getName());
        if (channels == null) {
            return;
        }
        Vec3 rotation = sample(channels.rotation(), seconds);
        Vec3 position = sample(channels.position(), seconds);
        Vec3 scale = sample(channels.scale(), seconds);
        if (rotation != null) {
            float baseX = initial == null ? 0.0F : initial.getRotX();
            float baseY = initial == null ? 0.0F : initial.getRotY();
            float baseZ = initial == null ? 0.0F : initial.getRotZ();
            bone.setRotX(baseX + (float) Math.toRadians(rotation.x()));
            bone.setRotY(baseY + (float) Math.toRadians(rotation.y()));
            bone.setRotZ(baseZ + (float) Math.toRadians(rotation.z()));
        }
        if (position != null) {
            float baseX = initial == null ? 0.0F : initial.getOffsetX();
            float baseY = initial == null ? 0.0F : initial.getOffsetY();
            float baseZ = initial == null ? 0.0F : initial.getOffsetZ();
            // 动画 position 是相对烘焙静止位的偏移，直接覆盖会把旗子抬到手臂 pivot 上方。
            bone.setPosX(baseX + position.x());
            bone.setPosY(baseY + position.y());
            bone.setPosZ(baseZ + position.z());
        }
        if (scale != null) {
            float baseX = initial == null ? 1.0F : initial.getScaleX();
            float baseY = initial == null ? 1.0F : initial.getScaleY();
            float baseZ = initial == null ? 1.0F : initial.getScaleZ();
            bone.setScaleX(baseX * scale.x());
            bone.setScaleY(baseY * scale.y());
            bone.setScaleZ(baseZ * scale.z());
        }
    }

    private static BoneChannels channels(Map<String, Map<String, BoneChannels>> table,
                                         String animationName, String boneName) {
        Map<String, BoneChannels> bones = table.get(animationName);
        return bones == null ? null : bones.get(boneName);
    }

    private static Map<String, Map<String, BoneChannels>> animationBones() {
        if (animationBones != null) {
            return animationBones;
        }
        animationBones = load(RESOURCE);
        return animationBones;
    }

    private static Map<String, Map<String, BoneChannels>> anchorBones() {
        if (anchorBones != null) {
            return anchorBones;
        }
        anchorBones = load(ANCHOR_RESOURCE);
        return anchorBones;
    }

    private static Map<String, Map<String, BoneChannels>> fpaBones() {
        if (fpaBones != null) {
            return fpaBones;
        }
        fpaBones = load(FPA_RESOURCE);
        return fpaBones;
    }

    private static Map<String, Map<String, BoneChannels>> hunterFpaBones() {
        if (hunterFpaBones != null) {
            return hunterFpaBones;
        }
        hunterFpaBones = load(HUNTER_FPA_RESOURCE);
        return hunterFpaBones;
    }

    private static Map<String, Map<String, BoneChannels>> portableTurretBones() {
        if (portableTurretBones != null) {
            return portableTurretBones;
        }
        portableTurretBones = load(PORTABLE_TURRET_RESOURCE);
        return portableTurretBones;
    }

    private static Map<String, Map<String, BoneChannels>> autoTurretBones() {
        if (autoTurretBones != null) {
            return autoTurretBones;
        }
        autoTurretBones = load(AUTO_TURRET_RESOURCE);
        return autoTurretBones;
    }

    private static Map<String, Map<String, BoneChannels>> load(ResourceLocation location) {
        Map<String, Map<String, BoneChannels>> loaded = new HashMap<>();
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(location)
                .orElseThrow().open();
             var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
            for (String animationName : animations.keySet()) {
                JsonObject animation = animations.getAsJsonObject(animationName);
                if (animation == null || !animation.has("bones")) {
                    continue;
                }
                JsonObject bones = animation.getAsJsonObject("bones");
                Map<String, BoneChannels> parsed = new HashMap<>();
                for (String boneName : bones.keySet()) {
                    JsonObject channels = bones.getAsJsonObject(boneName);
                    parsed.put(boneName, new BoneChannels(
                            channel(channels.get("rotation")),
                            channel(channels.get("position")),
                            channel(channels.get("scale"))));
                }
                loaded.put(animationName, Map.copyOf(parsed));
            }
        } catch (Exception exception) {
            TkGun.LOGGER.error("[tkgun] 手持姿态读取失败：{}", location, exception);
        }
        return Map.copyOf(loaded);
    }

    private static TreeMap<Double, Vec3> channel(JsonElement source) {
        if (source == null || !source.isJsonObject()) {
            return null;
        }
        TreeMap<Double, Vec3> frames = new TreeMap<>();
        for (var frame : source.getAsJsonObject().entrySet()) {
            JsonElement value = frame.getValue();
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("post")) {
                    value = object.get("post");
                } else if (object.has("vector")) {
                    value = object.get("vector");
                }
            }
            Vec3 vector = vector(value);
            if (vector != null) {
                frames.put(Double.parseDouble(frame.getKey()), vector);
            }
        }
        return frames.isEmpty() ? null : frames;
    }

    private static Vec3 vector(JsonElement value) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            return null;
        }
        return new Vec3(value.getAsJsonArray().get(0).getAsFloat(),
                value.getAsJsonArray().get(1).getAsFloat(),
                value.getAsJsonArray().get(2).getAsFloat());
    }

    private static Vec3 sample(TreeMap<Double, Vec3> frames, double seconds) {
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        var first = frames.firstEntry();
        var last = frames.lastEntry();
        if (seconds <= first.getKey()) {
            return first.getValue();
        }
        if (seconds >= last.getKey()) {
            return last.getValue();
        }
        var before = frames.floorEntry(seconds);
        var after = frames.ceilingEntry(seconds);
        double span = after.getKey() - before.getKey();
        float t = span <= 1.0E-8D ? 1.0F : (float) ((seconds - before.getKey()) / span);
        Vec3 a = before.getValue();
        Vec3 b = after.getValue();
        return new Vec3(a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t);
    }

    private record BoneChannels(TreeMap<Double, Vec3> rotation,
                                TreeMap<Double, Vec3> position,
                                TreeMap<Double, Vec3> scale) {
    }

    private record Vec3(float x, float y, float z) {
    }

    private WarBannerHeldPose() { }
}
