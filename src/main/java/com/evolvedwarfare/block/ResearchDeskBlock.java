package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.research.Research;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Mesa de investigación: abre el árbol de investigación de su dueño; las estanterías alrededor la aceleran. */
public class ResearchDeskBlock extends BlockWithEntity {
	public static final MapCodec<ResearchDeskBlock> CODEC = createCodec(ResearchDeskBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public ResearchDeskBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
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
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ResearchDeskBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlocks.RESEARCH_DESK_ENTITY, ResearchDeskBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof ResearchDeskBlockEntity desk) {
			desk.setOwner(player.getUuid());
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer
				&& world.getBlockEntity(pos) instanceof ResearchDeskBlockEntity desk) {
			if (desk.canUse(player)) {
				desk.claim(player);
				Research.sync(serverPlayer, pos, true);
			} else {
				player.sendMessage(Text.translatable("message.evolvedwarfare.site.not_yours"), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("block.evolvedwarfare.research_desk.tooltip.0").withColor(0xAAAAAA));
		tooltip.add(Text.translatable("block.evolvedwarfare.research_desk.tooltip.1").withColor(0xAAAAAA));
	}
}
