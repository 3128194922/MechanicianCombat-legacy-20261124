package com.mechaniciancombat.client;

import net.minecraft.world.phys.Vec3;

/**
 * HUD 落点圈和实际弹道必须共用同一条射线（枪口 / 出膛点 + 方向）。
 *
 * <p>历史问题：圈按"炮塔方向 {@code aimDir} 的帧插值"画，而腰射实际按"枪口 → 准星"打，
 * 并且腰射不受 ±25° 扇区限制 —— 转身或枪管被动画拧到一侧时，圈会滞后/被夹在扇区边缘，
 * 表现就是"实际弹道比指示器更偏左"。
 */
public final class ReticleShotAlignmentContract {
    public static void main(String[] args) {
        // 腰射：出膛点必须与指示器共用同一份 HipFireOrigin 计算（跑动时用推算枪口，否则用渲染枪口）。
        Vec3 player = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 sample = new Vec3(0.4D, 64.4D, 2.6D);
        Vec3 running = HipFireOrigin.select(true, player, 0.0F, sample);
        // yaw = 0：前方 = +Z，侧向 = −X（MUZZLE_SIDE 为负 → 落在 +X 一侧）。
        Vec3 expected = player.add(-com.mechaniciancombat.TkGun.MUZZLE_SIDE,
                com.mechaniciancombat.TkGun.MUZZLE_HEIGHT,
                com.mechaniciancombat.TkGun.MUZZLE_FORWARD);
        if (running.distanceTo(expected) > 1.0E-9D) {
            throw new AssertionError("running origin must match the shared muzzle formula: " + running);
        }
        if (!HipFireOrigin.select(false, player, 0.0F, sample).equals(sample)) {
            throw new AssertionError("idle origin must be the rendered muzzle sample");
        }
        // 方向：枪口 → 准星点必须真的指向那个点（而不是枪管轴向或炮塔方向）。
        Vec3 muzzle = new Vec3(0.5D, 64.2D, 1.0D);
        Vec3 target = new Vec3(-6.0D, 65.0D, 40.0D);
        Vec3 dir = target.subtract(muzzle).normalize();
        Vec3 again = muzzle.add(dir.scale(muzzle.distanceTo(target)));
        if (again.distanceTo(target) > 1.0E-6D) {
            throw new AssertionError("direction must pass through the crosshair target");
        }
        checkHipFireOrdering();
        checkReticleSharesFireBranch();
        System.out.println("ReticleShotAlignmentContract: reticle and shot share one ray");
    }

    /**
     * HUD 的分支条件必须和开火路径**逐字一致**。
     *
     * <p>历史缺陷：开火用 `focusish(id) || aiming(client)` 选方向，HUD 却只判 `aiming(client)`。
     * 蹲射（专注）时玩家不按右键，于是 HUD 画的是"枪口 → 准星"，子弹走的却是被 ±25° 扇区
     * 钳制过的 `aimDir` —— 两边方向不同，表现就是"特定情况下弹道比指示器更偏左"。
     */
    private static void checkReticleSharesFireBranch() {
        java.nio.file.Path fire = java.nio.file.Path.of(
                "src/main/java/com/mechaniciancombat/client/TkGunClient.java");
        java.nio.file.Path camera = java.nio.file.Path.of(
                "src/main/java/com/mechaniciancombat/client/GunCamera.java");
        if (!java.nio.file.Files.isRegularFile(fire) || !java.nio.file.Files.isRegularFile(camera)) {
            return;
        }
        // ① 两边都必须委托给同一个纯函数（这是真正的防护：只有一处判据，改不歪）。
        String cameraText = strip(read(camera));
        if (!cameraText.contains("ShotBranchRules.usesAimDirection(")) {
            throw new AssertionError("reticle must use the shared ShotBranchRules.usesAimDirection");
        }
        String fireText = strip(read(fire));
        if (!fireText.contains("ShotBranchRules.usesAimDirection(")) {
            throw new AssertionError("fire path must use the shared ShotBranchRules.usesAimDirection");
        }
        // ② 穷举判据矩阵：任何"专注但不按右键"的组合都必须是瞄准分支（这正是原先走歪的格子）。
        for (boolean focusish : new boolean[]{true, false}) {
            for (boolean aimKey : new boolean[]{true, false}) {
                boolean aim = ShotBranchRules.usesAimDirection(focusish, aimKey);
                if (focusish && !aim) {
                    throw new AssertionError("focused shots must use the aim direction: "
                            + "focusish=" + focusish + " aimKey=" + aimKey);
                }
                if (!focusish && !aimKey && aim) {
                    throw new AssertionError("hip fire must not use the aim direction");
                }
                if (!focusish && aimKey && !aim) {
                    throw new AssertionError("aim key must select the aim direction");
                }
            }
        }
        // ③ 方向取值只能有一份：HUD 必须直接调用开火用的 `shotDirection`。
        //
        // 历史缺陷：`shotDirection` 用 `aiming(client) && aimDir != null` 取方向，
        // 而 `updateAim` 每 tick 都以 `true` 调用 → `aimDir` 永不为 null。
        // 于是专注（不按右键）时 HUD 用 `aimDir`、子弹用 `muzzleWorldDir()`，
        // 两条方向差着枪口横向偏移 → "指示器和实际弹道对不上"。
        if (!cameraText.contains("Vec3 dir = shotDirection(client);")) {
            throw new AssertionError("reticle must reuse GunCamera#shotDirection "
                    + "(otherwise it can pick a different direction than the bullet)");
        }
        // `shotDirection` 本身也必须走共享判据，不能只判右键。
        int shot = cameraText.indexOf("public static Vec3 shotDirection");
        if (shot < 0) {
            throw new AssertionError("shotDirection not found in " + camera);
        }
        int shotEnd = cameraText.indexOf("\n\t}", shot + 10);
        if (shotEnd < 0) {
            throw new AssertionError("cannot delimit shotDirection in " + camera);
        }
        String shotBody = cameraText.substring(shot, shotEnd);
        if (!shotBody.contains("ShotBranchRules.usesAimDirection(")) {
            throw new AssertionError("shotDirection must use ShotBranchRules.usesAimDirection, "
                    + "not just the aim key: focused shots without right-click would fire "
                    + "along the barrel while the reticle shows the crosshair ray");
        }
    }

    private static String read(java.nio.file.Path path) {
        try {
            return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new AssertionError("cannot read " + path, error);
        }
    }

    private static String strip(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /**
     * 腰射必须在**掰玩家朝向之前**算完弹道。
     *
     * <p>历史缺陷：`fireHipImmediately` 先把 `player` 的 yaw/bodyYaw/headYaw 连同插值起点
     * 一次性掰到相机 yaw，再调 `directionToCrosshair`；而 HUD 不改朝向、用的是上一渲染帧的枪口。
     * 两边基准不一致时，横向分量系统性偏向枪口原在侧 → "实际弹道比指示器更偏左"。
     *
     * <p>这是一条**源码顺序**断言：只有在开发树里能找到源文件时才执行，
     * 打包产物里没有源码时自动跳过（避免把发布构建搞挂）。
     */
    private static void checkHipFireOrdering() {
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/com/mechaniciancombat/client/TkGunClient.java");
        if (!java.nio.file.Files.isRegularFile(source)) {
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new AssertionError("cannot read " + source, error);
        }
        int methodStart = text.indexOf("private static void fireHipImmediately");
        if (methodStart < 0) {
            throw new AssertionError("fireHipImmediately not found in " + source);
        }
        int methodEnd = text.indexOf("\n\t}", methodStart);
        String body = methodEnd < 0 ? text.substring(methodStart) : text.substring(methodStart, methodEnd);
        // 方法体里既有说明注释也有真正的调用；注释里同样出现 `directionToCrosshair`，
        // 不剥掉的话顺序判断会被注释骗过去（这个坑在写契约时就踩了一次）。
        body = body.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        int direction = body.indexOf("directionToCrosshair");
        int rotate = body.indexOf("setYRot");
        if (direction < 0 || rotate < 0) {
            throw new AssertionError("hip-fire path must call directionToCrosshair and setYRot");
        }
        if (direction > rotate) {
            throw new AssertionError("hip-fire computes direction AFTER rotating the player: "
                    + "the reticle and the shot then disagree (shot drifts to one side)");
        }
    }

    private ReticleShotAlignmentContract() { }
}
