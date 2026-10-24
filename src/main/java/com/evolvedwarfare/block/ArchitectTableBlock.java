package com.evolvedwarfare.block;

import com.evolvedwarfare.network.BlueprintNetworking;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Mesa del arquitecto: la biblioteca de planos, con vista previa en 3D, para imprimir copias o borrar los tuyos. */
public class ArchitectTableBlock extends HorizontalFacingBlock {
	public static final MapCodec<ArchitectTableBlock> CODEC = createCodec(ArchitectTableBlock::new);

	public ArchitectTableBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			BlueprintNetworking.openLibrary(serverPlayer, pos);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("block.evolvedwarfare.architect_table.tooltip").withColor(0xAAAAAA));
	}
}
