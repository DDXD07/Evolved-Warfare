package com.evolvedwarfare.backpack;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** Una mochila puesta en el suelo: se abre como un cofre; al romperla vuelve a ser el objeto, con todo dentro. */
public class BackpackBlock extends BlockWithEntity {
	public static final MapCodec<BackpackBlock> CODEC = createCodec(BackpackBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 14, 14);

	public BackpackBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BackpackBlockEntity(pos, state);
	}

	/** La pone en el suelo en pos (si se puede). */
	public static boolean place(ServerWorld world, BlockPos pos, ItemStack stack, Direction facing) {
		if (!world.getBlockState(pos).isReplaceable() || !Backpacks.BACKPACK_BLOCK.getDefaultState().canPlaceAt(world, pos)) {
			return false;
		}
		world.setBlockState(pos, Backpacks.BACKPACK_BLOCK.getDefaultState().with(FACING, facing));
		if (world.getBlockEntity(pos) instanceof BackpackBlockEntity be) {
			be.load(stack);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof BackpackBlockEntity be)) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity sp) {
			int slots = be.slots();
			sp.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
				@Override
				public Integer getScreenOpeningData(ServerPlayerEntity p) {
					return slots;
				}

				@Override
				public Text getDisplayName() {
					return be.backpack().getName();
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
					return new BackpackScreenHandler(syncId, inv, be, slots);
				}
			});
			world.playSound(null, pos, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.BLOCKS, 0.6F, 1.0F);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof BackpackBlockEntity be) {
			ItemStack item = be.toItem();
			if (!item.isEmpty()) {
				ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), item);
			}
			be.clear();
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		return world.getBlockEntity(pos) instanceof BackpackBlockEntity be ? be.backpack().copy() : ItemStack.EMPTY;
	}
}
