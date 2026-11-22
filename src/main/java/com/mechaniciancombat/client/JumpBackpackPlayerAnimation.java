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
 * 跳跃推进器的玩家空中动作（作者 `jump` / `falling` 翻页动画）。
 *
 * <p>整套身体通道都由这里输出；持枪时上层会清掉本层，交回枪械 rig。
 */
public final class JumpBackpackPlayerAnimation implements IAnimation {
    private static final int LAYER_PRIORITY = 24;
    private static final ResourceLocation JUMP_CLIP =
            TkGun.id("player_animations/jump_backpack_jump.json");
    private static final ResourceLocation FALLING_CLIP =
            TkGun.id("player_animations/jump_backpack_falling.json");
    private static final Map<Integer, JumpBackpackPlayerAnimation> LAYERS = new HashMap<>();
    private static final Map<ResourceLocation, Map<String, BoneChannels>> CACHE = new HashMap<>();
    private static final Map<UUID, Integer> PHASE = new HashMap<>();

    private final AbstractClientPlayer player;

    private JumpBackpackPlayerAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            JumpBackpackPlayerAnimation animation = new JumpBackpackPlayerAnimation(player);
            stack.addAnimLayer(LAYER_PRIORITY, animation);
            LAYERS.put(player.getId(), animation);
        });
    }

    public static void apply(AbstractClientPlayer player, JumpBackpackClientState.Phase phase) {
        if (phase == JumpBackpackClientState.Phase.READY_TO_FLY
                || phase == JumpBackpackClientState.Phase.FLYING) {
            PHASE.put(player.getUUID(), 1);
        } else if (phase == JumpBackpackClientState.Phase.FALLING) {
            PHASE.put(player.getUUID(), 2);
        } else {
            PHASE.remove(player.getUUID());
        }
    }

    public static void clear(UUID uuid) {
        PHASE.remove(uuid);
    }

    public static void clearAll() {
        PHASE.clear();
        CACHE.clear();
    }

    @Override
    public boolean isActive() {
        return player != null && player.isAlive() && PHASE.containsKey(player.getUUID());
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f fallback) {
        if (!isActive()) {
            return fallback;
        }
        int mode = PHASE.getOrDefault(player.getUUID(), 0);
        if (mode == 0) {
            return fallback;
        }
        ResourceLocation clip = mode == 2 ? FALLING_CLIP : JUMP_CLIP;
        String bone = switch (part) {
            case "torso" -> "torso";
            case "head" -> "head";
            case "rightArm" -> "rightArm";
            case "leftArm" -> "leftArm";
            case "rightLeg" -> "rightLeg";
            case "leftLeg" -> "leftLeg";
            default -> null;
        };
        if (bone == null) {
            return fallback;
        }
        BoneChannels channels = channels(clip).get(bone);
        if (channels == null) {
            return fallback;
        }
        double seconds = Math.min(mode == 2 ? 0.15D : 0.25D,
                player.tickCount / 20.0D % 1.0D);
        if (type == TransformType.ROTATION) {
            Vec3 rotation = sample(channels.rotation(), seconds);
            return rotation == null ? fallback
                    : new Vec3f((float) Math.toRadians(rotation.x()),
                    (float) Math.toRadians(rotation.y()),
                    (float) Math.toRadians(rotation.z()));
        }
        if (type == TransformType.POSITION) {
            Vec3 position = sample(channels.position(), seconds);
            return position == null ? fallback : new Vec3f(position.x(), position.y(), position.z());
        }
        return fallback;
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    private static Map<String, BoneChannels> channels(ResourceLocation clip) {
        Map<String, BoneChannels> cached = CACHE.get(clip);
        if (cached != null) {
            return cached;
        }
        Map<String, BoneChannels> loaded = new HashMap<>();
        try (var resource = Minecraft.getInstance().getResourceManager().getResource(clip)
                .orElseThrow().open();
             var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
            for (String name : animations.keySet()) {
                JsonObject bones = animations.getAsJsonObject(name).getAsJsonObject("bones");
                if (bones == null) continue;
                for (String boneName : bones.keySet()) {
                    JsonObject bone = bones.getAsJsonObject(boneName);
                    loaded.put(boneName, new BoneChannels(
                            channel(bone.get("rotation")),
                            channel(bone.get("position"))));
                }
            }
        } catch (Exception exception) {
            TkGun.LOGGER.error("[tkgun] 跳跃推进器动作读取失败：{}", clip, exception);
        }
        CACHE.put(clip, Map.copyOf(loaded));
        return CACHE.get(clip);
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
                if (object.has("post")) value = object.get("post");
                else if (object.has("vector")) value = object.get("vector");
            }
            Vec3 vector = vector(value);
            if (vector != null) frames.put(Double.parseDouble(frame.getKey()), vector);
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
        if (frames == null || frames.isEmpty()) return null;
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
