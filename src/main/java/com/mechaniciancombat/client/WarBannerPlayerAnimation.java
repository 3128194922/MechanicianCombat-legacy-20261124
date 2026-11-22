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

/**
 * 非持枪施法时的上半身动作（作者 `flag_skill` PlayerAnimator 片段）。
 *
 * <p>持枪施法由 GeckoLib rig 播 `skill_flag_gun` 并桥接身体姿势，
 * 这里再叠一层会盖掉作者的手臂/头部，所以那条路径直接跳过。
 * 只输出 torso / 左右臂，其余部位交回下层（走路/待机）动画。
 */
public final class WarBannerPlayerAnimation implements IAnimation {
    private static final int LAYER_PRIORITY = 26;
    private static final ResourceLocation CLIP = TkGun.id("player_animations/flag_skill.json");
    private static final String CLIP_NAME = "flag_skill";
    private static final Map<Integer, WarBannerPlayerAnimation> LAYERS = new HashMap<>();
    private static Map<String, BoneChannels> channels;

    private final AbstractClientPlayer player;

    private WarBannerPlayerAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            WarBannerPlayerAnimation animation = new WarBannerPlayerAnimation(player);
            stack.addAnimLayer(LAYER_PRIORITY, animation);
            LAYERS.put(player.getId(), animation);
        });
    }

    public static void clearCache() {
        channels = null;
    }

    public static void clearAll() {
        LAYERS.clear();
    }

    @Override
    public boolean isActive() {
        return player != null && player.isAlive()
                && WarBannerSkillClientState.isActive(player.getId())
                && !WarBannerSkillClientState.usesGunAnimation(player.getId());
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f fallback) {
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
        double seconds = WarBannerSkillClientState.animationSeconds(
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
            return position == null ? fallback : new Vec3f(position.x(), position.y(), position.z());
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
            JsonObject bones = clip.getAsJsonObject("bones");
            for (String boneName : bones.keySet()) {
                JsonObject bone = bones.getAsJsonObject(boneName);
                loaded.put(boneName, new BoneChannels(
                        channel(bone.get("rotation")),
                        channel(bone.get("position"))));
            }
        } catch (Exception exception) {
            TkGun.LOGGER.error("[tkgun] 战旗施法动作读取失败：{}", CLIP, exception);
        }
        channels = Map.copyOf(loaded);
        return channels;
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
        if (seconds <= first.getKey()) return first.getValue();
        if (seconds >= last.getKey()) return last.getValue();
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

    private record BoneChannels(TreeMap<Double, Vec3> rotation, TreeMap<Double, Vec3> position) {
    }

    private record Vec3(float x, float y, float z) {
    }
}
