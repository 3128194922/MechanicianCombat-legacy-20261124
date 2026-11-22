package com.mechaniciancombat.client;

import com.mechaniciancombat.item.BackpackUpgradeData;
import com.mechaniciancombat.item.CombatBackpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 跳跃推进器的像素能量电池（屏幕左下角）。
 *
 * <p>素材是作者给的 32×32 单帧：左侧 12×32 是电池外壳，右侧 8×28 是能量填充。
 * 填充高度直接用服务端下发的 `jumpEnergyPixels`（0..28），所以 HUD 与推进器实际可用能量一致。
 * 锁定回充时叠一层红色脉冲，提示"现在按跳不会起跳"。
 */
public final class JumpEnergyHud {
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("tkgun", "textures/gui/hud/jet_energy.png");
	private static final int FRAME_WIDTH = 12;
	private static final int FRAME_HEIGHT = 32;
	private static final int MAX_HEIGHT = 28;
	private static final int METER_X = 2;
	private static final int METER_Y = 2;
	private static final int FILL_WIDTH = 8;
	private static final int FILL_SOURCE_X = 24;

	public static void render(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui
				|| !BackpackUpgradeData.canUseJumpThruster(
						CombatBackpackItem.equippedStack(client.player))) {
			return;
		}
		int x = 6;
		int y = context.guiHeight() - 40;
		// 作者画的是左侧 12×32 外壳；电池开口 8×28。
		context.blit(TEXTURE, x, y, 0.0F, 0.0F,
				FRAME_WIDTH, FRAME_HEIGHT, 32, 32);
		int id = client.player.getId();
		if (CombatBackpackClientState.jumpEnergyLocked(id)) {
			float cycle = (net.minecraft.Util.getMillis() % 1000L) / 1000.0F;
			float pulse = 0.5F - 0.5F * Mth.cos(cycle * (float) Math.PI * 2.0F);
			int alpha = 32 + Math.round(pulse * 176.0F);
			context.fill(x + METER_X, y + METER_Y,
					x + METER_X + FILL_WIDTH, y + METER_Y + MAX_HEIGHT,
					(alpha << 24) | 0x00FF2020);
		}
		int height = Mth.clamp(CombatBackpackClientState.jumpEnergyPixels(id), 0, MAX_HEIGHT);
		if (height <= 0) {
			return;
		}
		int sourceY = METER_Y + MAX_HEIGHT - height;
		int destinationY = y + METER_Y + MAX_HEIGHT - height;
		context.blit(TEXTURE, x + METER_X, destinationY,
				FILL_SOURCE_X, sourceY, FILL_WIDTH, height, 32, 32);
	}

	private JumpEnergyHud() {
	}
}
