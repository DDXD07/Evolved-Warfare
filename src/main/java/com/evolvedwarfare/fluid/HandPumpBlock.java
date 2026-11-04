package com.evolvedwarfare.fluid;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bomba de mano: va en la tubería. Cada golpe de palanca (clic derecho) sube 4 capas de agua (hasta 64 guardadas) para
 * los grifos que estén más altos que la toma.
 */
public class HandPumpBlock extends BlockWithEntity {
	public static final MapCodec<HandPumpBlock> CODEC = createCodec(HandPumpBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/** La palanca arriba o abajo (cambia con cada golpe). */
	public static final BooleanProperty DOWN = BooleanProperty.of("stroke");
	private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 16, 12);

	public HandPumpBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH).with(DOWN, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, DOWN);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof HandPumpBlockEntity be && be.stroke(world.getTime())) {
			world.setBlockState(pos, state.with(DOWN, !state.get(DOWN)), Block.NOTIFY_ALL);
			world.playSound(null, pos, state.get(DOWN) ? SoundEvents.BLOCK_PISTON_EXTEND : SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS,
					0.35F, 1.5F);
			player.addExhaustion(0.08F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.pump.charge", be.charge(), HandPumpBlockEntity.MAX), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new HandPumpBlockEntity(pos, state);
	}
}
