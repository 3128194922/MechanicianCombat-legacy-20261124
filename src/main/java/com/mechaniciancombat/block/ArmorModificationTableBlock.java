package com.mechaniciancombat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 护甲维护工作台：查看当前护甲并安装/替换模块。 */
public final class ArmorModificationTableBlock extends SmithingTableBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	private static final VoxelShape NORTH_SHAPE = Shapes.or(
			Block.box(3.0D, 0.0D, 0.0D, 13.0D, 2.0D, 16.0D),
			Block.box(7.0D, 1.0D, 0.0D, 9.0D, 12.0D, 16.0D),
			Block.box(3.0D, 12.0D, 0.0D, 13.0D, 14.0D, 16.0D),
			Block.box(3.5D, 12.5D, 0.5D, 12.5D, 15.5D, 4.5D),
			Block.box(3.5D, 12.5D, 11.5D, 12.5D, 15.5D, 15.5D));
	private static final VoxelShape EAST_SHAPE = Shapes.or(
			Block.box(0.0D, 0.0D, 3.0D, 16.0D, 2.0D, 13.0D),
			Block.box(0.0D, 1.0D, 7.0D, 16.0D, 12.0D, 9.0D),
			Block.box(0.0D, 12.0D, 3.0D, 16.0D, 14.0D, 13.0D),
			Block.box(0.5D, 12.5D, 3.5D, 4.5D, 15.5D, 12.5D),
			Block.box(11.5D, 12.5D, 3.5D, 15.5D, 15.5D, 12.5D));
	private static final VoxelShape SOUTH_SHAPE = Shapes.or(
			Block.box(3.0D, 0.0D, 0.0D, 13.0D, 2.0D, 16.0D),
			Block.box(7.0D, 1.0D, 0.0D, 9.0D, 12.0D, 16.0D),
			Block.box(3.0D, 12.0D, 0.0D, 13.0D, 14.0D, 16.0D),
			Block.box(3.5D, 12.5D, 11.5D, 12.5D, 15.5D, 15.5D),
			Block.box(3.5D, 12.5D, 0.5D, 12.5D, 15.5D, 4.5D));
	private static final VoxelShape WEST_SHAPE = Shapes.or(
			Block.box(0.0D, 0.0D, 3.0D, 16.0D, 2.0D, 13.0D),
			Block.box(0.0D, 1.0D, 7.0D, 16.0D, 12.0D, 9.0D),
			Block.box(0.0D, 12.0D, 3.0D, 16.0D, 14.0D, 13.0D),
			Block.box(11.5D, 12.5D, 3.5D, 15.5D, 15.5D, 12.5D),
			Block.box(0.5D, 12.5D, 3.5D, 4.5D, 15.5D, 12.5D));

	public ArmorModificationTableBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING,
				context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
								 InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			BlockPos screenPos = pos.immutable();
			net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
					new SimpleMenuProvider(
							(containerId, inventory, menuPlayer) ->
									new com.mechaniciancombat.screen.ArmorModificationTableScreenHandler(
											containerId, inventory, screenPos),
							Component.translatable("block.tkgun.armor_modification_table")),
					buffer -> buffer.writeBlockPos(screenPos));
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
							   CollisionContext context) {
		return shapeFor(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
										CollisionContext context) {
		return shapeFor(state);
	}

	private static VoxelShape shapeFor(BlockState state) {
		return switch (state.getValue(FACING)) {
			case EAST -> EAST_SHAPE;
			case SOUTH -> SOUTH_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}
}
