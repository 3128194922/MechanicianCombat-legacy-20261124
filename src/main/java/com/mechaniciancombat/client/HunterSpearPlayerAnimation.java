package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 猎矛部署时的上半身 PlayerAnimator 动画。
 *
 * <p>作者的 `player_animations/hunter_spear_deploy.json` 是 Blockbench/GeckoLib 的
 * `animations -> bones` 结构。这里不再手工采样后直接写玩家骨骼，而是直接调用
 * PlayerAnimator 自带的 {@link AnimationSerializing}（内部走 GeckoLibSerializer），
 * 让库完成度数→弧度、GeckoLib 轴向/坐标到原版玩家模型的转换。
 *
 * <p>不使用 {@code PlayerAnimationRegistry} 是因为 1.0.2-rc1 的注册表按
 * {@code player_animation}（单数）扫描资源，而本项目资源目录是
 * {@code player_animations}（复数），注册表会得到空结果。直接读资源文件可以
 * 同时绕开该路径问题并保留原生动画语义。
 */
public final class HunterSpearPlayerAnimation {
    private static final int LAYER_PRIORITY = 27;
    private static final ResourceLocation CLIP =
            TkGun.id("player_animations/hunter_spear_deploy.json");
    private static final String CLIP_NAME = "hunter_spear_deploy";
    private static final Map<UUID, ModifierLayer<IAnimation>> LAYERS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static KeyframeAnimation animationData;

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            ModifierLayer<IAnimation> layer = new ModifierLayer<>();
            stack.addAnimLayer(LAYER_PRIORITY, layer);
            LAYERS.put(player.getUUID(), layer);
            ACTIVE.remove(player.getUUID());
        });
    }

    public static void play(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        if (ACTIVE.contains(uuid)) {
            return;
        }
        ModifierLayer<IAnimation> layer = layerFor(player);
        KeyframeAnimation animation = animation();
        if (layer == null || animation == null) {
            return;
        }
        layer.setAnimation(new HoldAnimation(new KeyframeAnimationPlayer(animation)));
        ACTIVE.add(uuid);
    }

    public static void clear(UUID uuid) {
        ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
        if (ACTIVE.remove(uuid) && layer != null) {
            layer.setAnimation(null);
        }
    }

    public static void clearAll() {
        for (ModifierLayer<IAnimation> layer : LAYERS.values()) {
            layer.setAnimation(null);
        }
        LAYERS.clear();
        ACTIVE.clear();
        animationData = null;
    }

    private static ModifierLayer<IAnimation> layerFor(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = LAYERS.get(player.getUUID());
        if (layer != null) {
            return layer;
        }
        try {
            layer = new ModifierLayer<>();
            PlayerAnimationAccess.getPlayerAnimLayer(player)
                    .addAnimLayer(LAYER_PRIORITY, layer);
            LAYERS.put(player.getUUID(), layer);
            return layer;
        } catch (RuntimeException exception) {
            TkGun.LOGGER.warn("[tkgun] 猎矛部署动画层创建失败", exception);
            return null;
        }
    }

    private static KeyframeAnimation animation() {
        KeyframeAnimation cached = animationData;
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = Minecraft.getInstance().getResourceManager()
                .getResource(CLIP).orElseThrow().open()) {
            List<KeyframeAnimation> animations =
                    AnimationSerializing.deserializeAnimation(stream);
            for (KeyframeAnimation animation : animations) {
                if (CLIP_NAME.equals(animation.extraData.get("name"))) {
                    animationData = animation;
                    return animation;
                }
            }
            if (!animations.isEmpty()) {
                animationData = animations.get(0);
                return animationData;
            }
        } catch (Exception exception) {
            TkGun.LOGGER.warn("[tkgun] 猎矛部署 PlayerAnimator 片段读取失败：{}", CLIP,
                    exception);
        }
        return null;
    }

    /**
     * 只让作者片段驱动躯干和双臂，头部保持玩家原本的视线朝向；
     * 片段结束后由 {@link HunterSpearSkillClientState} 摘层。
     */
    private static final class HoldAnimation implements IAnimation {
        private final KeyframeAnimationPlayer animation;
        private boolean running = true;

        private HoldAnimation(KeyframeAnimationPlayer animation) {
            this.animation = animation;
        }

        @Override
        public void tick() {
            if (running && animation.isActive()) {
                animation.tick();
            } else {
                running = false;
            }
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type,
                                    float tickDelta, Vec3f fallback) {
            if ("head".equals(part)) {
                return type == TransformType.POSITION
                        ? animation.get3DTransform(part, type, tickDelta, fallback)
                        : fallback;
            }
            if (!"torso".equals(part) && !"rightArm".equals(part)
                    && !"leftArm".equals(part)) {
                return fallback;
            }
            return animation.get3DTransform(part, type, tickDelta, fallback);
        }

        @Override
        public void setupAnim(float tickDelta) {
            animation.setupAnim(tickDelta);
        }
    }

    private HunterSpearPlayerAnimation() {
    }
}
