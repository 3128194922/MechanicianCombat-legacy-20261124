package com.mechaniciancombat.client;

/** Auto launcher components must remain visible in both idle and target-tracking passes. */
public final class BackpackBoneVisibilityContract {
    public static void main(String[] args) {
        expect("auto_C3", true, false, false, true);
        for (String bone : new String[]{"auto_ejector", "auto_box", "auto_sight", "auto_barrel"}) {
            expect(bone, true, false, true, true);
        }
        expect("auto_C3", true, false, true, false);
        expect("C3", true, false, false, false);
        checkStateClips();
        System.out.println("BackpackBoneVisibilityContract: 7 scenarios + state clips passed");
    }

    /**
     * 背包的 12 段手绘状态必须齐全。
     *
     * <p>历史问题：本地只移植了 `aim/CD/ready/Maintenance` 四段，跳跃推进器、
     * 导弹舱和战旗的背包姿态整段缺失，表现为"装了模块但背包不动"。
     */
    private static void checkStateClips() {
        String[] required = {"aim", "CD", "ready", "Maintenance", "ready_to_fly", "flying",
                "close", "misslie_aim", "misslie_CD", "war_banner_ready", "war_banner_aim",
                "war_banner_cd"};
        for (String clip : required) {
            String path = "/assets/tkgun/animations/entity/combat_backpack.animation.json";
            try (var stream = BackpackBoneVisibilityContract.class.getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError("missing resource: " + path);
                var animations = com.google.gson.JsonParser.parseReader(
                                new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
                if (!animations.has(clip)) {
                    throw new AssertionError("backpack state clip missing: " + clip);
                }
            } catch (java.io.IOException error) {
                throw new AssertionError("cannot read " + path, error);
            }
        }
        String jumpPath = "/assets/tkgun/animations/entity/jump_backpack_states.animation.json";
        try (var stream = BackpackBoneVisibilityContract.class.getResourceAsStream(jumpPath)) {
            if (stream == null) throw new AssertionError("missing resource: " + jumpPath);
            var animations = com.google.gson.JsonParser.parseReader(
                            new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            if (!animations.has("jump_cooldown")) {
                throw new AssertionError("missing jump_cooldown clip");
            }
        } catch (java.io.IOException error) {
            throw new AssertionError("cannot read " + jumpPath, error);
        }
        // 炮台第一人称道具（便携 / 自动）与猎矛 FPA 一样按相机空间绘制。
        for (String asset : new String[]{"portable_turret_fpa", "auto_turret_fpa",
                "hunter_anchor_fpa", "war_banner_fpa"}) {
            for (String path : new String[]{
                    "/assets/tkgun/geo/entity/" + asset + ".geo.json",
                    "/assets/tkgun/animations/entity/" + asset + ".animation.json",
                    "/assets/tkgun/textures/entity/" + asset + ".png"}) {
                if (BackpackBoneVisibilityContract.class.getResource(path) == null) {
                    throw new AssertionError("missing first-person prop asset: " + path);
                }
            }
        }
        // 背包几何必须包含推进器与导弹舱骨骼，否则状态动画无处落地。
        for (String geo : new String[]{"combat_backpack", "combat_backpack_rig"}) {
            String path = "/assets/tkgun/geo/entity/" + geo + ".geo.json";
            try (var stream = BackpackBoneVisibilityContract.class.getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError("missing resource: " + path);
                String text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String bone : new String[]{"jet_left", "jet_right", "jet_F", "jet_F2",
                        "missile_box", "missile_left", "missile_right"}) {
                    if (!text.contains("\"" + bone + "\"")) {
                        throw new AssertionError(geo + " missing bone: " + bone);
                    }
                }
            } catch (java.io.IOException error) {
                throw new AssertionError("cannot read " + path, error);
            }
        }
    }

    private static void expect(String name, boolean auto, boolean summon, boolean suppress, boolean expected) {
        boolean actual = BackpackBoneVisibility.draw(name, auto, summon, suppress);
        if (actual != expected) throw new AssertionError(name + " suppressed=" + suppress + " expected=" + expected);
    }
    private BackpackBoneVisibilityContract() {}
}
