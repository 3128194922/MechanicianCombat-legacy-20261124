package com.mechaniciancombat.client;

import com.mechaniciancombat.TkGun;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 猎矛部署时的上半身 PlayerAnimator 动画。
 *
 * <p>使用 PlayerAnimator 原生动画层，让库自己完成原版玩家模型的枢轴、坐标和手臂空间转换；
 * 不再手动采样 JSON 后直接写玩家手臂骨骼。
 */
public final class HunterSpearPlayerAnimation {
    private static final int LAYER_PRIORITY = 27;
    private static final ResourceLocation CLIP = TkGun.id("hunter_spear_deploy");
    private static final Map<UUID, ModifierLayer<IAnimation>> LAYERS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

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
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(CLIP);
        if (layer == null || animation == null) {
            TkGun.LOGGER.warn("[tkgun] 猎矛部署 PlayerAnimator 片段缺失：{}", CLIP);
            return;
        }
        layer.setAnimation(new HoldAnimation(new KeyframeAnimationPlayer(animation)));
        ACTIVE.add(uuid);
    }

    public static void clear(UUID uuid) {
        ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
        if (layer != null) {
            layer.setAnimation(null);
        }
        ACTIVE.remove(uuid);
    }

    public static void clearAll() {
        for (ModifierLayer<IAnimation> layer : LAYERS.values()) {
            layer.setAnimation(null);
        }
        LAYERS.clear();
        ACTIVE.clear();
    }

    private static ModifierLayer<IAnimation> layerFor(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = LAYERS.get(player.getUUID());
        if (layer != null) {
            return layer;
        }
        try {
            layer = new ModifierLayer<>();
            PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(LAYER_PRIORITY, layer);
            LAYERS.put(player.getUUID(), layer);
            return layer;
        } catch (RuntimeException exception) {
            return null;
        }
    }

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

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return animation.getFirstPersonMode(tickDelta);
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return animation.getFirstPersonConfiguration(tickDelta);
        }
    }

    private HunterSpearPlayerAnimation() {
    }
}
