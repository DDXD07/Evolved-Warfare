package com.evolvedwarfare.fluid;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Grifo de palanca: va al final de una tubería (pegado a ella por detrás o colgando de ella). Con la palanca abierta
 * echa agua debajo, capa a capa: en un caldero (lo llena), en el suelo o en un pilón. El agua viene de la toma de las
 * tuberías: si está a su altura o más arriba, baja sola; si está más abajo, hay que darle a la bomba de mano. Con un cubo,
 * un frasco o una cantimplora vacíos se llenan en él directamente.
 */
public class FaucetBlock extends BlockWithEntity {
	public static final MapCodec<FaucetBlock> CODEC = createCodec(FaucetBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = Properties.OPEN;
	private static final VoxelShape[] SHAPES = new VoxelShape[4];

	static {
		// pegado detrás (al norte si mira al sur), con el caño delante
		for (Direction d : Direction.Type.HORIZONTAL) {
			double[] box = {5, 4, 0, 11, 13, 11};
			SHAPES[d.getHorizontal()] = rotate(box, d);
		}
	}

	private static VoxelShape rotate(double[] b, Direction facing) {
		// b: con el grifo mirando al sur (+z), la tubería al norte
		return switch (facing) {
			case NORTH -> Block.createCuboidShape(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
			case EAST -> Block.createCuboidShape(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
			case WEST -> Block.createCuboidShape(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
			default -> Block.createCuboidShape(b[0], b[1], b[2], b[3], b[4], b[5]);
		};
	}

	public FaucetBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(OPEN, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
		return getDefaultState().with(FACING, facing);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPES[state.get(FACING).getHorizontal()];
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
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) {
			boolean open = !state.get(OPEN);
			world.setBlockState(pos, state.with(OPEN, open), Block.NOTIFY_ALL);
			world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5F, open ? 0.7F : 0.55F);
		}
		return ActionResult.success(world.isClient);
	}

	/** Un cubo, un frasco o una cantimplora vacíos se llenan en el grifo (4, 1 y 2 capas). */
	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		ItemStack filled;
		int units;
		if (stack.isOf(Items.BUCKET)) {
			filled = new ItemStack(Items.WATER_BUCKET);
			units = FiniteFluids.BUCKET;
		} else if (stack.isOf(Items.GLASS_BOTTLE)) {
			filled = PotionContentsComponent.createStack(Items.POTION, Potions.WATER);
			units = FiniteFluids.BOTTLE;
		} else if (stack.isOf(ModItems.CANTEEN) || stack.isOf(ModItems.WATER_CANTEEN) && stack.getDamage() > 0) {
			filled = new ItemStack(ModItems.WATER_CANTEEN);
			units = FiniteFluids.CANTEEN;
		} else {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!world.isClient) {
			if (!Plumbing.take(world, pos, units)) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.faucet.dry"), true);
				return ItemActionResult.CONSUME;
			}
			player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, filled));
			world.playSound(null, pos, stack.isOf(Items.BUCKET) ? SoundEvents.ITEM_BUCKET_FILL : SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS,
					1.0F, 1.0F);
		}
		return ItemActionResult.success(world.isClient);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FaucetBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlocks.FAUCET_ENTITY, FaucetBlockEntity::tick);
	}
}
