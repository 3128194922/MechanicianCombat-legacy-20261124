package com.mechaniciancombat.net;

import com.mechaniciancombat.TkGun;
import net.minecraft.network.FriendlyByteBuf;

/** 可视化配方编辑器的翻页 / 保存 / 删除请求。 */
public record RecipeEditorActionPayload(int action, int page, boolean shaped,
										String recipeId, float experience,
										int cookingTime) implements ModPacket {
	public static final int ACTION_SET_PAGE = 0;
	public static final int ACTION_SAVE = 1;
	public static final int ACTION_DELETE = 2;
	private static final int MAX_RECIPE_ID_LENGTH = 128;

	public static final PacketType<RecipeEditorActionPayload> ID =
			new PacketType<>(RecipeEditorActionPayload.class, TkGun.id("recipe_editor_action"));

	public static final PacketCodec<RecipeEditorActionPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.action());
				buf.writeVarInt(value.page());
				buf.writeBoolean(value.shaped());
				buf.writeUtf(value.recipeId() == null ? "" : value.recipeId(), MAX_RECIPE_ID_LENGTH);
				buf.writeFloat(value.experience());
				buf.writeVarInt(value.cookingTime());
			},
			buf -> new RecipeEditorActionPayload(buf.readVarInt(), buf.readVarInt(),
					buf.readBoolean(), buf.readUtf(MAX_RECIPE_ID_LENGTH),
					buf.readFloat(), buf.readVarInt()));

	@Override
	public PacketType<? extends ModPacket> getId() {
		return ID;
	}
}
