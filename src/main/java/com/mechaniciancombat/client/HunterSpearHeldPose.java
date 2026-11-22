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
 * 猎矛部署道具的姿态采样：直接读作者 `hunter_spear_deploy.animation.json` 的骨骼通道。
 *
 * <p>不交给 GeckoLib 控制器，是因为这个道具只在施法期间由玩家渲染器手动驱动；
 * 手写采样能精确按施法秒数取帧，也便于命中冻结时暂停。
 */
final class HunterSpearHeldPose {
    private static final ResourceLocation RESOURCE =
            TkGun.id("animations/entity/hunter_spear_deploy.animation.json");
    private static Map<String, Map<String, BoneChannels>> animationBones;

    static void clearCache() {
        animationBones = null;
    }

    static void apply(GeoBone bone, double seconds) {
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
        BoneChannels channels = channels("skill_flag", bone.getName());
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

    private static BoneChannels channels(String animationName, String boneName) {
        load();
        Map<String, BoneChannels> bones = animationBones.get(animationName);
        return bones == null ? null : bones.get(boneName);
    }

    private static void load() {
        if (animationBones != null) {
            return;
        }
        Map<String, Map<String, BoneChannels>> loaded = new HashMap<>();
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(RESOURCE)
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
            TkGun.LOGGER.error("[tkgun] 战旗手持姿态读取失败：{}", RESOURCE, exception);
        }
        animationBones = Map.copyOf(loaded);
    }

    private static TreeMap<Double, Vec3> channel(JsonElement source) {
        if (source == null) {
            return null;
        }
        TreeMap<Double, Vec3> frames = new TreeMap<>();
        // 上游 r1034：动画通道允许直接写常量数组（如 "arm_right":{"rotation":[0,0,0]}），
        // 旧解析只认对象，会把整条通道当成没有 → 部署动作缺失/错位。
        if (source.isJsonArray()) {
            Vec3 constant = vector(source);
            if (constant != null) {
                frames.put(0.0D, constant);
                return frames;
            }
            return null;
        }
        if (!source.isJsonObject()) {
            return null;
        }
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

    private HunterSpearHeldPose() { }
}
