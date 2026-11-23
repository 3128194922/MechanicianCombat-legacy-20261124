package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 猎矛部署时的上半身 PlayerAnimator 动画。
 *
 * <p>作者给出的 `player_animations/hunter_spear_deploy.json` 是 Blockbench/GeckoLib
 * 的 `animations -> bones` 结构，PlayerAnimator 自带的 `PlayerAnimationRegistry`
 * 只识别 Emotecraft 的 `emote/moves` 结构，直接注册会得到空动画。这里与战旗、
 * 跳跃推进器保持一致，手动解析作者 JSON，再把骨骼通道交给 PlayerAnimator。
 */
public final class HunterSpearPlayerAnimation implements IAnimation {
    private static final int LAYER_PRIORITY = 27;
    private static final ResourceLocation CLIP =
            TkGun.id("player_animations/hunter_spear_deploy.json");
    private static final String CLIP_NAME = "hunter_spear_deploy";
    private static Map<String, BoneChannels> channels;

    private final AbstractClientPlayer player;

    private HunterSpearPlayerAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) ->
                stack.addAnimLayer(LAYER_PRIORITY, new HunterSpearPlayerAnimation(player)));
    }

    /** 播放由 HunterSpearSkillClientState 的计时驱动；这里只确保资源已解析。 */
    public static void play(AbstractClientPlayer player) {
        if (player != null) {
            channels();
        }
    }

    public static void clear(UUID uuid) {
        // 动画是否生效由 HunterSpearSkillClientState 判断，不需要手动摘层。
    }

    public static void clearAll() {
        channels = null;
    }

    @Override
    public boolean isActive() {
        return player != null && player.isAlive()
                && HunterSpearSkillClientState.isActive(player.getId());
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type,
                                float tickDelta, Vec3f fallback) {
        if (!isActive()) {
            return fallback;
        }
        String bone = switch (part) {
            case "torso" -> "torso";
            case "rightArm" -> "rightArm";
            case "leftArm" -> "leftArm";
            default -> null;
        };
        if (bone == null) {
            return fallback;
        }
        double seconds = HunterSpearSkillClientState.animationSeconds(
                player.getId(), player.level().getGameTime(), tickDelta);
        BoneChannels boneChannels = channels().get(bone);
        if (boneChannels == null) {
            return fallback;
        }
        if (type == TransformType.ROTATION) {
            Vec3 rotation = sample(boneChannels.rotation(), seconds);
            return rotation == null ? fallback
                    : new Vec3f((float) Math.toRadians(rotation.x()),
                    (float) Math.toRadians(rotation.y()),
                    (float) Math.toRadians(rotation.z()));
        }
        if (type == TransformType.POSITION) {
            Vec3 position = sample(boneChannels.position(), seconds);
            return position == null ? fallback
                    : new Vec3f(position.x(), position.y(), position.z());
        }
        return fallback;
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    private static Map<String, BoneChannels> channels() {
        Map<String, BoneChannels> cached = channels;
        if (cached != null) {
            return cached;
        }
        Map<String, BoneChannels> loaded = new HashMap<>();
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(CLIP)
                .orElseThrow().open();
             var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
            JsonObject clip = animations.getAsJsonObject(CLIP_NAME);
            if (clip != null && clip.has("bones")) {
                JsonObject bones = clip.getAsJsonObject("bones");
                for (String boneName : bones.keySet()) {
                    JsonObject bone = bones.getAsJsonObject(boneName);
                    loaded.put(boneName, new BoneChannels(
                            channel(bone.get("rotation")),
                            channel(bone.get("position"))));
                }
            }
        } catch (Exception exception) {
            TkGun.LOGGER.warn("[tkgun] 猎矛部署 PlayerAnimator 片段读取失败：{}", CLIP, exception);
        }
        channels = Map.copyOf(loaded);
        return channels;
    }

    private static TreeMap<Double, Vec3> channel(JsonElement source) {
        if (source == null) {
            return null;
        }
        TreeMap<Double, Vec3> frames = new TreeMap<>();
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
                try {
                    frames.put(Double.parseDouble(frame.getKey()), vector);
                } catch (NumberFormatException ignored) {
                    // 通道里偶尔会有非数字键（作者备注），跳过即可。
                }
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
                                TreeMap<Double, Vec3> position) {
    }

    private record Vec3(float x, float y, float z) {
    }

}
