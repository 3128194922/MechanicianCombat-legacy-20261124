package com.mechaniciancombat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.bernie.geckolib.core.molang.MolangParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Evaluates the shipped chest expressions with GeckoLib's parser at three pitch angles. */
public final class GunPitchMolangContract {
    private static double pitch;
    public static void main(String[] args) throws Exception {
        MolangParser.INSTANCE.setValue("query.tkgun_head_x_rotation", () -> pitch);
        MolangParser.INSTANCE.setValue("query.tkgun_body_x_rotation", () -> pitch);
        // r611 的新枪（静谧风暴 / 枢德之裁）用标准 query.head_x_rotation 写胸部俯仰。
        MolangParser.INSTANCE.setValue("query.head_x_rotation", () -> pitch);
        MolangParser.INSTANCE.setValue("query.body_x_rotation", () -> pitch);
        try (var stream = GunPitchMolangContract.class.getResourceAsStream(
                "/assets/tkgun/animations/entity/bowgun_rig.animation.json")) {
            if (stream == null) throw new AssertionError("missing bowgun animations");
            JsonObject animations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            for (String name : new String[]{"stand_shooting", "stand_shooting_hold"}) {
                check(animations, name, null, 25, 5, -15);
            }
            for (String name : new String[]{"Focus_hold", "Focus_shooting", "Focus_shooting1",
                    "Focus_shooting2", "Focus_shooting3", "Focus_shooting4", "Focus_shooting5",
                    "Focus_shooting6", "Focus_shooting7", "Focus_shooting_extra",
                    "Focus_shooting_upper", "Focus_shooting2_upper", "Focus_shooting3_upper",
                    "Focus_shooting4_upper", "Focus_shooting5_upper", "Focus_shooting6_upper",
                    "Focus_shooting7_upper"}) {
                check(animations, name, null, 30, 0, -30);
            }
            check(animations, "Focus_ready", "1.0", 30, 0, -30);
            check(animations, "Focus_ready_upper", "1.0", 30, 0, -30);
            check(animations, "sliding_fast_ready", "0.625", 30, 0, -30);
            check(animations, "rolling_fast_ready", "0.5", 30, 0, -30);
            check(animations, "Focus_ready_hold", "0.0", 30, 0, -30);
            for (String name : new String[]{"Focus_over_shooting", "Focus_over_shooting_upper"}) {
                check(animations, name, "0.0", 30, 0, -30);
                check(animations, name, "2.5", 20, 0, -20);
            }
        }
        // 这两把枪的素材只带合并弹链段；蹲射逐发仍按 Focus_shooting..7 取名，
        // 缺任何一段 GeckoLib 都会把骨骼复位成站立姿势（蹲射时人物一瞬间站起来）。
        for (String file : new String[]{"abyssal_ancient_cannon_rig.animation.json",
                "veritas_et_justitia_rig.animation.json"}) {
            try (var stream = GunPitchMolangContract.class.getResourceAsStream(
                    "/assets/tkgun/animations/entity/" + file)) {
                if (stream == null) throw new AssertionError("missing animations: " + file);
                JsonObject animations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
                for (String name : new String[]{"Focus_hold", "Focus_shooting", "Focus_shooting1",
                        "Focus_shooting2", "Focus_shooting3", "Focus_shooting4", "Focus_shooting5",
                        "Focus_shooting6", "Focus_shooting7", "Focus_shooting_extra"}) {
                    check(animations, name, null, 30, 0, -30);
                }
                check(animations, "Focus_ready_upper", "1.0", 30, 0, -30);
            }
        }
        System.out.println("GunPitchMolangContract: pitch expressions follow input");
    }

    private static void check(JsonObject animations, String name, String key,
                              double low, double level, double high) throws Exception {
        var rotation = animations.getAsJsonObject(name).getAsJsonObject("bones")
                .getAsJsonObject("chest").get("rotation");
        JsonArray values = key == null ? rotation.getAsJsonArray()
                : rotation.getAsJsonObject().getAsJsonArray(key);
        var expression = MolangParser.parseExpression(values.get(0).getAsString());
        double[] inputs = {-60, 0, 60};
        double[] expected = {low, level, high};
        for (int i = 0; i < inputs.length; i++) {
            pitch = inputs[i];
            double actual = expression.get();
            if (Math.abs(actual - expected[i]) > 0.0001) {
                throw new AssertionError(name + " at " + pitch + ": " + actual + " != " + expected[i]);
            }
        }
    }
}
