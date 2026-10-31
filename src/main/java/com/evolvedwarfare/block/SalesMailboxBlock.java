package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Buzón de Ventas: lo que metas en sus 16 huecos se vende solo cada vez que empieza un día nuevo, a la cuarta parte de
 * lo que cuesta en el mercado, y las Glory Coins quedan en su hueco aparte (ver {@link SalesMailboxBlockEntity}). La
 * banderita se levanta cuando hay monedas que recoger.
 */
public class SalesMailboxBlock extends BlockWithEntity {
	public static final MapCodec<SalesMailboxBlock> CODEC = createCodec(SalesMailboxBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/** Banderita levantada: hay monedas dentro. */
	public static final BooleanProperty FLAG = BooleanProperty.of("flag");

	/** Poste y caja, mirando al norte (la puerta delante); al este/oeste, la caja va de lado. */
	private static final VoxelShape NORTH_SOUTH = VoxelShapes.union(
			Block.createCuboidShape(6.5, 0, 6.5, 9.5, 9, 9.5),
			Block.createCuboidShape(4, 9, 0.5, 12, 15.5, 15));
	private static final VoxelShape EAST_WEST = VoxelShapes.union(
			Block.createCuboidShape(6.5, 0, 6.5, 9.5, 9, 9.5),
			Block.createCuboidShape(1, 9, 4, 15.5, 15.5, 12));

	public SalesMailboxBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(FLAG, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, FLAG);
	}

	/** La puerta mira hacia quien lo pone. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SalesMailboxBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlocks.SALES_MAILBOX_ENTITY, SalesMailboxBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof SalesMailboxBlockEntity mailbox) {
			mailbox.setOwner(player.getUuid());
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof SalesMailboxBlockEntity mailbox) {
			if (!mailbox.canAccess(player)) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.sales_mailbox.not_yours"), true);
			} else {
				if (mailbox.getOwner() == null) {
					mailbox.setOwner(player.getUuid());
				}
				player.openHandledScreen(mailbox);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof SalesMailboxBlockEntity mailbox) {
				ItemScatterer.spawn(world, pos, mailbox);
				world.updateComparators(pos, this);
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("block.evolvedwarfare.sales_mailbox.tooltip").formatted(Formatting.GRAY));
	}
}
