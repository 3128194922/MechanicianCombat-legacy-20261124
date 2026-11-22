package com.mechaniciancombat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mechaniciancombat.TkGun;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 枢德之裁近战资源契约：动画段、腿部派生段、特效段、音效关键帧必须与代码请求的名字对得上。
 *
 * <p>历史问题：素材更新后动画改名/删段，而代码仍按旧名取 → GeckoLib 找不到动画就把骨骼复位，
 * 表现是近战中途人物一瞬间站直、特效消失或音效整段不响。
 */
public final class VeritasMeleeAssetContract {
    private static final String ANIMATION_PATH =
            "/assets/tkgun/animations/entity/veritas_et_justitia_rig.animation.json";
    private static final String GEO_PATH =
            "/assets/tkgun/geo/entity/veritas_et_justitia_rig.geo.json";

    public static void main(String[] args) throws Exception {
        JsonObject animations = read(ANIMATION_PATH).getAsJsonObject("animations");
        JsonObject geometry = read(GEO_PATH)
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

        List<String> required = new ArrayList<>(List.of(
                "ATK_1", "ATK_1_upper", "ATK_1_to_idle", "ATK_1_to_idle_upper",
                "ATK_2", "ATK_2_to_idle", "ATK_2_dash_to_idle", "ATK_2_dash_to_run",
                "ATK_3", "ATK_3_hold", "ATK_3_to_idle",
                "ATK_4", "ATK_4_to_idle",
                "ATK_hold_over", "ATK_holdover_to_idle", "ATK_run"));
        // 作者为这些段生成的下半身通道（腿不该被走路动画盖掉）。
        for (String base : new String[]{"ATK_2", "ATK_2_to_idle", "ATK_3", "ATK_3_to_idle",
                "ATK_4", "ATK_4_to_idle", "ATK_hold_over", "ATK_holdover_to_idle"}) {
            required.add(base + "_lower");
        }
        // 近战特效平面由独立控制器播放。
        for (String effect : new String[]{"close_effect_ATK_1", "close_effect_ATK_2",
                "close_effect_ATK_4", "close_effect_ATK_hold_over"}) {
            required.add(effect);
        }
        for (String name : required) {
            if (!animations.has(name)) {
                throw new AssertionError("missing melee animation: " + name);
            }
        }

        // 特效骨骼必须在几何里存在，否则特效层拿到的是空集合。
        Set<String> effectBones = Set.of("effect_1", "1_1", "1_2", "1_3", "1_4",
                "effect_2", "2_1", "2_2", "2_3", "2_4");
        Set<String> geoBones = new java.util.HashSet<>();
        for (JsonElement bone : geometry.getAsJsonArray("bones")) {
            geoBones.add(bone.getAsJsonObject().get("name").getAsString());
        }
        for (String bone : effectBones) {
            if (!geoBones.contains(bone)) {
                throw new AssertionError("missing effect bone in geometry: " + bone);
            }
        }

        // 动画里出现的音效名必须能在 KEYFRAME_SOUNDS 里查到，否则服务端只能打警告、整段不响。
        for (String clip : new String[]{"ATK_1", "ATK_2", "ATK_3", "ATK_3_hold", "ATK_4",
                "ATK_hold_over", "ATK_run"}) {
            JsonObject soundEffects = animations.getAsJsonObject(clip)
                    .getAsJsonObject("sound_effects");
            if (soundEffects == null) continue;
            for (var entry : soundEffects.entrySet()) {
                String key = entry.getValue().getAsJsonObject().get("effect").getAsString();
                if (key.isBlank()) continue;
                if (!TkGun.KEYFRAME_SOUNDS.containsKey(key)) {
                    throw new AssertionError(clip + " references unmapped sound: " + key);
                }
            }
        }

        // 近战时间常量必须与作者动画长度一致。
        expect(TkGun.MELEE_ATK3_MAIN_END_TICK, 15, "ATK_3 main end (0.75s)");
        expect(TkGun.MELEE_HOLDOVER_RETURN_TICKS, 9, "ATK_holdover_to_idle (0.375s)");
        System.out.println("VeritasMeleeAssetContract: melee clips, effect bones and sounds aligned");
    }

    private static void expect(int actual, int expected, String what) {
        if (actual != expected) {
            throw new AssertionError(what + ": " + actual + " != " + expected);
        }
    }

    private static JsonObject read(String path) throws Exception {
        try (var stream = VeritasMeleeAssetContract.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private VeritasMeleeAssetContract() { }
}
