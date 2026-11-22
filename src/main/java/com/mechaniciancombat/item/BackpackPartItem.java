package com.mechaniciancombat.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Installable component for a combat backpack. */
public final class BackpackPartItem extends Item implements GeoItem {
	public enum Kind {
		CORE,
		LAUNCHER,
		AUTO_LAUNCHER,
		SUMMON_ANTENNA,
		/** r774 战旗部件。 */
		BATTLE_BANNER,
		/** r774 跳跃推进器部件。 */
		JUMP_THRUSTER,
		/** r774 导弹舱部件（装在跳跃推进器之上）。 */
		MISSILE_BOX,
		/** 魔像空投舱（装在召唤天线之上，仅创造模式可直接生成）。 */
		GOLEM_CAPSULE
	}

	private final Kind kind;
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

	public BackpackPartItem(Kind kind, Properties settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// 战旗手持道具由 WarBannerHeldRenderer 手动采样姿态，不需要控制器。
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	public boolean isLauncherKind() {
		return kind == Kind.LAUNCHER || kind == Kind.AUTO_LAUNCHER
				|| kind == Kind.SUMMON_ANTENNA || kind == Kind.BATTLE_BANNER
				|| kind == Kind.JUMP_THRUSTER;
	}

	/** 装在独立槽位的附加部件（不占用发射器槽）。 */
	public boolean isModuleKind() {
		return kind == Kind.MISSILE_BOX || kind == Kind.GOLEM_CAPSULE;
	}

	@Override
	public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip,
							  TooltipFlag type) {
		super.appendHoverText(stack, context, tooltip, type);
		if (kind == Kind.LAUNCHER) {
			tooltip.add(Component.translatable("tooltip.tkgun.portable_launcher.storage"));
			tooltip.add(Component.translatable("tooltip.tkgun.portable_launcher.ammo"));
			tooltip.add(Component.translatable("tooltip.tkgun.portable_launcher.trajectory"));
		} else if (kind == Kind.AUTO_LAUNCHER) {
			tooltip.add(Component.translatable("tooltip.tkgun.auto_portable_launcher.storage"));
			tooltip.add(Component.translatable("tooltip.tkgun.auto_portable_launcher.attack"));
			tooltip.add(Component.translatable("tooltip.tkgun.auto_portable_launcher.charge"));
		} else if (kind == Kind.SUMMON_ANTENNA) {
			tooltip.add(Component.translatable("tooltip.tkgun.summon_antenna.deploy"));
			tooltip.add(Component.translatable("tooltip.tkgun.summon_antenna.mark"));
			tooltip.add(Component.translatable("tooltip.tkgun.summon_antenna.cast"));
		} else if (kind == Kind.BATTLE_BANNER) {
			tooltip.add(Component.translatable("tooltip.tkgun.war_banner.install"));
			tooltip.add(Component.translatable("tooltip.tkgun.war_banner.skill"));
			tooltip.add(Component.translatable("tooltip.tkgun.war_banner.aura"));
		} else if (kind == Kind.JUMP_THRUSTER) {
			tooltip.add(Component.translatable("tooltip.tkgun.jump_backpack_thruster.activate"));
			tooltip.add(Component.translatable("tooltip.tkgun.jump_backpack_thruster.steer"));
			tooltip.add(Component.translatable("tooltip.tkgun.jump_backpack_thruster.restriction"));
		} else if (kind == Kind.MISSILE_BOX) {
			tooltip.add(Component.translatable("tooltip.tkgun.missile_box.install"));
			tooltip.add(Component.translatable("tooltip.tkgun.missile_box.mark"));
			tooltip.add(Component.translatable("tooltip.tkgun.missile_box.fire"));
		} else if (kind == Kind.GOLEM_CAPSULE) {
			tooltip.add(Component.translatable("tooltip.tkgun.golem_capsule.install"));
			tooltip.add(Component.translatable("tooltip.tkgun.golem_capsule.summon"));
			tooltip.add(Component.translatable("tooltip.tkgun.golem_capsule.creative"));
		}
	}

	@Override
	public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
		if (kind != Kind.GOLEM_CAPSULE) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		net.minecraft.world.entity.player.Player player = context.getPlayer();
		if (player == null || !player.isCreative()
				|| com.mechaniciancombat.TkGunServer.MOLTEN_GOLEM == null) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		net.minecraft.world.level.Level level = context.getLevel();
		if (level.isClientSide) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		com.mechaniciancombat.entity.MoltenGolemEntity golem =
				com.mechaniciancombat.TkGunServer.MOLTEN_GOLEM.create(level);
		if (golem == null) {
			return net.minecraft.world.InteractionResult.FAIL;
		}
		net.minecraft.core.BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		golem.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
				player.getYRot(), 0.0F);
		golem.setSummoner(player.getUUID());
		if (!level.addFreshEntity(golem)) {
			return net.minecraft.world.InteractionResult.FAIL;
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}
}
