package com.evolvedwarfare.block;

import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.trade.TraderType;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El Catálogo del mercader: un libro encima del mostrador, con la tapa de su oficio. Clic derecho abre el catálogo del
 * mercader de esa tienda (si está y no duerme); el libro se dibuja en 3D y se abre ({@code CatalogBookRenderer}).
 * FACING es hacia donde están los clientes; TRADE, el oficio (el orden de {@link TraderType}).
 */
public class CatalogBlock extends BlockWithEntity {
	public static final MapCodec<CatalogBlock> CODEC = createCodec(CatalogBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final IntProperty TRADE = IntProperty.of("trade", 0, TraderType.values().length - 1);
	/** Hasta dónde se busca al mercader de la tienda. */
	public static final double REACH = 7.0;
	private static final VoxelShape SHAPE_NS = Block.createCuboidShape(2, 0, 3.5, 14, 2.5, 12.5);
	private static final VoxelShape SHAPE_EW = Block.createCuboidShape(3.5, 0, 2, 12.5, 2.5, 14);

	public CatalogBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(TRADE, 0));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, TRADE);
	}

	/** Mirando hacia quien lo pone, con el oficio del mercader más cercano. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
		TraderEntity trader = nearest(ctx.getWorld(), ctx.getBlockPos(), null);
		return trader != null ? state.with(TRADE, trader.getTraderType().ordinal()) : state;
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
		return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
	}

	/** El libro lo dibuja su renderizador (para poder abrirlo). */
	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CatalogBlockEntity(pos, state);
	}

	public static TraderType trade(BlockState state) {
		TraderType[] types = TraderType.values();
		return types[Math.floorMod(state.get(TRADE), types.length)];
	}

	/** El mercader de la tienda: el más cercano (del oficio del libro, si se dice) a menos de {@link #REACH} bloques. */
	@Nullable
	public static TraderEntity nearest(World world, BlockPos pos, @Nullable TraderType type) {
		List<TraderEntity> traders = world.getEntitiesByClass(TraderEntity.class, new Box(pos).expand(REACH),
				t -> t.isAlive() && (type == null || t.getTraderType() == type));
		TraderEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (TraderEntity t : traders) {
			double d = t.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (d < bestDistance) {
				bestDistance = d;
				best = t;
			}
		}
		return best;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			TraderEntity trader = nearest(world, pos, trade(state));
			if (trader == null) {
				trader = nearest(world, pos, null);
			}
			if (trader == null) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.catalog.nobody"), true);
			} else if (trader.isSleeping()) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.catalog.closed", trader.getDisplayName()), true);
			} else {
				trader.openCatalog(serverPlayer, pos);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("block.evolvedwarfare.trader_catalog.tooltip").formatted(Formatting.GRAY));
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
	}
}
