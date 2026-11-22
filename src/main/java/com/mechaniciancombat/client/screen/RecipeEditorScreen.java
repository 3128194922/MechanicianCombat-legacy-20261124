package com.mechaniciancombat.client.screen;

import com.mechaniciancombat.client.ClientNetwork;
import com.mechaniciancombat.net.RecipeEditorActionPayload;
import com.mechaniciancombat.screen.RecipeEditorScreenHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** 可视化合成 / 熔炉 / 酿造配方编辑器。 */
public final class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorScreenHandler> {
	private static final ResourceLocation CRAFTING_TABLE_TEXTURE =
			new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");
	private static final ResourceLocation FURNACE_TEXTURE =
			new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
	private static final ResourceLocation BREWING_STAND_TEXTURE =
			new ResourceLocation("minecraft", "textures/gui/container/brewing_stand.png");
	private static final int WIDTH = 176;
	private static final int BASE_HEIGHT = 166;
	private static final int HEIGHT = 238;
	private boolean shaped = true;
	private EditBox recipeIdField;
	private EditBox experienceField;
	private EditBox cookingTimeField;
	private Button modeButton;

	public RecipeEditorScreen(RecipeEditorScreenHandler handler, Inventory inventory,
							  Component title) {
		super(handler, inventory, title);
		this.imageWidth = WIDTH;
		this.imageHeight = HEIGHT;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 72;
	}

	@Override
	protected void init() {
		super.init();
		recipeIdField = new EditBox(font, leftPos + 58, topPos + 171, 110, 18,
				Component.translatable("screen.tkgun.recipe_editor.id"));
		recipeIdField.setMaxLength(128);
		recipeIdField.setHint(Component.translatable("screen.tkgun.recipe_editor.id_hint"));
		addRenderableWidget(recipeIdField);

		addRenderableWidget(Button.builder(Component.literal("←"), button -> changePage(-1))
				.bounds(leftPos + 8, topPos + 194, 20, 18).build());
		addRenderableWidget(Button.builder(Component.literal("→"), button -> changePage(1))
				.bounds(leftPos + 30, topPos + 194, 20, 18).build());
		modeButton = addRenderableWidget(Button.builder(modeText(), button -> {
			shaped = !shaped;
			button.setMessage(modeText());
		}).bounds(leftPos + 8, topPos + 216, 80, 18).build());
		addRenderableWidget(Button.builder(
				Component.translatable("screen.tkgun.recipe_editor.save"),
				button -> ClientNetwork.send(action(RecipeEditorActionPayload.ACTION_SAVE)))
				.bounds(leftPos + 96, topPos + 194, 38, 18).build());
		addRenderableWidget(Button.builder(
				Component.translatable("screen.tkgun.recipe_editor.delete"),
				button -> ClientNetwork.send(action(RecipeEditorActionPayload.ACTION_DELETE)))
				.bounds(leftPos + 136, topPos + 194, 32, 18).build());
		experienceField = new EditBox(font, leftPos + 32, topPos + 216, 50, 18,
				Component.translatable("screen.tkgun.recipe_editor.experience"));
		experienceField.setValue("0.1");
		addRenderableWidget(experienceField);
		cookingTimeField = new EditBox(font, leftPos + 108, topPos + 216, 52, 18,
				Component.translatable("screen.tkgun.recipe_editor.cooking_time"));
		cookingTimeField.setValue("200");
		addRenderableWidget(cookingTimeField);
		updatePageControls();
	}

	private Component modeText() {
		return Component.translatable(shaped
				? "screen.tkgun.recipe_editor.shaped"
				: "screen.tkgun.recipe_editor.shapeless");
	}

	private void changePage(int offset) {
		int next = Math.floorMod(menu.getPage() + offset, RecipeEditorScreenHandler.PAGE_COUNT);
		menu.setPage(next);
		updatePageControls();
		ClientNetwork.send(new RecipeEditorActionPayload(
				RecipeEditorActionPayload.ACTION_SET_PAGE, next, shaped, "", 0.1F, 200));
	}

	private void updatePageControls() {
		int page = menu.getPage();
		modeButton.visible = page == RecipeEditorScreenHandler.PAGE_CRAFTING;
		experienceField.visible = page == RecipeEditorScreenHandler.PAGE_FURNACE;
		cookingTimeField.visible = page == RecipeEditorScreenHandler.PAGE_FURNACE;
	}

	private RecipeEditorActionPayload action(int action) {
		return new RecipeEditorActionPayload(action, menu.getPage(), shaped,
				recipeIdField.getValue(), parseFloat(), parseInt());
	}

	private float parseFloat() {
		try {
			return Math.max(0.0F, Float.parseFloat(experienceField.getValue().trim()));
		} catch (NumberFormatException ignored) {
			return 0.1F;
		}
	}

	private int parseInt() {
		try {
			return Math.max(1, Integer.parseInt(cookingTimeField.getValue().trim()));
		} catch (NumberFormatException ignored) {
			return 200;
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		ResourceLocation texture = switch (menu.getPage()) {
			case RecipeEditorScreenHandler.PAGE_FURNACE -> FURNACE_TEXTURE;
			case RecipeEditorScreenHandler.PAGE_BREWING -> BREWING_STAND_TEXTURE;
			default -> CRAFTING_TABLE_TEXTURE;
		};
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		graphics.blit(texture, leftPos, topPos, 0, 0, WIDTH, BASE_HEIGHT, 256, 256);
		graphics.fill(leftPos, topPos + BASE_HEIGHT, leftPos + WIDTH,
				topPos + HEIGHT, 0xFFC6C6C6);
		graphics.fill(leftPos, topPos + BASE_HEIGHT, leftPos + 1,
				topPos + HEIGHT, 0xFF373737);
		graphics.fill(leftPos + WIDTH - 1, topPos + BASE_HEIGHT, leftPos + WIDTH,
				topPos + HEIGHT, 0xFF373737);
		graphics.fill(leftPos, topPos + HEIGHT - 1, leftPos + WIDTH,
				topPos + HEIGHT, 0xFF373737);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(font,
				Component.translatable("screen.tkgun.recipe_editor.id"),
				leftPos + 8, topPos + 176, 0xFF404040, false);
		graphics.drawString(font, pageText(), leftPos + 56, topPos + 199, 0xFF404040, false);
		if (menu.getPage() == RecipeEditorScreenHandler.PAGE_BREWING) {
			graphics.drawString(font,
					Component.translatable("screen.tkgun.recipe_editor.brewing_hint"),
					leftPos + 8, topPos + 221, 0xFF404040, false);
		} else if (menu.getPage() == RecipeEditorScreenHandler.PAGE_FURNACE) {
			graphics.drawString(font,
					Component.translatable("screen.tkgun.recipe_editor.experience"),
					leftPos + 8, topPos + 221, 0xFF404040, false);
			graphics.drawString(font,
					Component.translatable("screen.tkgun.recipe_editor.cooking_time"),
					leftPos + 84, topPos + 221, 0xFF404040, false);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
				0x404040, false);
	}

	private Component pageText() {
		return Component.translatable(switch (menu.getPage()) {
			case RecipeEditorScreenHandler.PAGE_FURNACE -> "screen.tkgun.recipe_editor.page.furnace";
			case RecipeEditorScreenHandler.PAGE_BREWING -> "screen.tkgun.recipe_editor.page.brewing";
			default -> "screen.tkgun.recipe_editor.page.crafting";
		});
	}
}
