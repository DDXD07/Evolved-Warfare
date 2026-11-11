package com.evolvedwarfare.train;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Un bloque de las vías grandes: cada trozo de vía ocupa varios (de 4 a 21) y todos guardan la forma, hacia dónde mira,
 * si gira a la izquierda y qué parte son (así se sabe dónde está su esquina de origen). Se dibuja el trozo entero desde su
 * bloque principal; al romper uno se rompe el trozo entero. Los desvíos se cambian con clic derecho.
 */
public class TrackBlock extends BlockWithEntity {
	public static final MapCodec<TrackBlock> CODEC = createCodec(TrackBlock::new);
	public static final EnumProperty<Track.Shape> SHAPE = EnumProperty.of("shape", Track.Shape.class);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty LEFT = BooleanProperty.of("left");
	public static final IntProperty PART = IntProperty.of("part", 0, 35);
	public static final BooleanProperty THROWN = BooleanProperty.of("thrown");
	private static final VoxelShape FLAT = Block.createCuboidShape(0, 0, 0, 16, 2, 16);
	private static final VoxelShape[] SLOPE = new VoxelShape[4];
	private static boolean removing;

	static {
		for (int i = 0; i < 4; i++) {
			SLOPE[i] = Block.createCuboidShape(0, 0, 0, 16, Math.min(16, (i + 1) * 4 + 2), 16);
		}
	}

	public TrackBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(SHAPE, Track.Shape.STRAIGHT).with(FACING, net.minecraft.util.math.Direction.NORTH)
				.with(LEFT, false).with(PART, Track.MAIN).with(THROWN, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, FACING, LEFT, PART, THROWN);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(PART) == Track.MAIN ? new TrackBlockEntity(pos, state) : null;
	}

	private static VoxelShape shape(BlockState state) {
		if (state.get(SHAPE) == Track.Shape.SLOPE) {
			return SLOPE[Math.min(3, state.get(PART) / 6)];
		}
		return FLAT;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape(state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(SHAPE) == Track.Shape.SLOPE ? shape(state) : VoxelShapes.empty();
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return true;
	}

	/** Los desvíos: clic derecho cambia por dónde va el tren. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (state.get(SHAPE) != Track.Shape.SWITCH) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			Track.Piece p = Track.piece(pos, state);
			boolean thrown = !state.get(THROWN);
			for (BlockPos b : p.blocks()) {
				BlockState s = world.getBlockState(b);
				if (s.isOf(this)) {
					world.setBlockState(b, s.with(THROWN, thrown), Block.NOTIFY_ALL);
				}
			}
			world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.8F, thrown ? 0.7F : 0.9F);
			world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.6F, 0.6F);
		}
		return ActionResult.success(world.isClient);
	}

	/** Al romperlo un jugador (sin creativo) por una parte que no es la principal: el trozo sale igual. */
	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && !player.isCreative() && state.get(PART) != Track.MAIN) {
			Track.Piece p = Track.piece(pos, state);
			Block.dropStack(world, pos, new ItemStack(Trains.trackItem(state.get(SHAPE), state.get(LEFT))));
			BlockState main = world.getBlockState(p.main());
			if (main.isOf(this)) {
				// el principal se quita sin soltar nada (ya ha salido el trozo)
				world.setBlockState(p.main(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
			}
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!newState.isOf(this) && !world.isClient && !removing) {
			// se va el trozo entero
			removing = true;
			try {
				Track.Piece p = Track.piece(pos, state);
				for (BlockPos b : p.blocks()) {
					if (b.equals(pos)) {
						continue;
					}
					BlockState s = world.getBlockState(b);
					if (s.isOf(this) && Track.piece(b, s).main().equals(p.main())) {
						world.setBlockState(b, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
					}
				}
			} finally {
				removing = false;
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		return new ItemStack(Trains.trackItem(state.get(SHAPE), state.get(LEFT)));
	}

	/** El bloque principal de un trozo (para dibujarlo). */
	public static class TrackBlockEntity extends BlockEntity {
		public TrackBlockEntity(BlockPos pos, BlockState state) {
			super(Trains.TRACK_ENTITY, pos, state);
		}
	}
}
