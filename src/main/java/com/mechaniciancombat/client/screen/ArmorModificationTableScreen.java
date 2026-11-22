package com.mechaniciancombat.client.screen;

import com.mechaniciancombat.screen.ArmorModificationTableScreenHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** 作者绘制的护甲维护界面，带固定玩家预览。 */
public final class ArmorModificationTableScreen
		extends AbstractContainerScreen<ArmorModificationTableScreenHandler> {
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("tkgun", "textures/gui/container/armor_modification_table.png");
	private static final int TEXTURE_WIDTH = 256;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int PREVIEW_X = 81;
	private static final int PREVIEW_Y = 6;
	private static final int PREVIEW_WIDTH = 49;
	private static final int PREVIEW_HEIGHT = 59;

	public ArmorModificationTableScreen(ArmorModificationTableScreenHandler handler,
										Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight,
				TEXTURE_WIDTH, TEXTURE_HEIGHT);
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		int x1 = leftPos + PREVIEW_X;
		int y1 = topPos + PREVIEW_Y;
		int x2 = x1 + PREVIEW_WIDTH;
		int y2 = y1 + PREVIEW_HEIGHT;
		graphics.enableScissor(x1, y1, x2, y2);
		// 1.20.1 的预览方法把 (x, y) 当作脚底锚点；按中心传会把人物上半身裁掉。
		int feetX = x1 + PREVIEW_WIDTH / 2;
		int feetY = y1 + PREVIEW_HEIGHT - 6;
		InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
				feetX, feetY, 30, (float) (mouseX - feetX),
				(float) (mouseY - feetY), minecraft.player);
		graphics.disableScissor();
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// 作者贴图已包含全部可见标签。
	}
}
