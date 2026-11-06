package com.evolvedwarfare.hell;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El círculo de ritual: se ponen encima las ofrendas (clic derecho con cada una) y se toca con la mano vacía para hacer
 * el rito que formen ({@link Rituals}). Agachado y con la mano vacía, se recoge la última.
 */
public class RitualCircleBlock extends BlockWithEntity {
	public static final MapCodec<RitualCircleBlock> CODEC = createCodec(RitualCircleBlock::new);
	private static final VoxelShape SHAPE = VoxelShapes.cuboid(0, 0, 0, 1, 2 / 16.0, 1);

	public RitualCircleBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RitualCircleBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof RitualCircleBlockEntity circle)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!world.isClient) {
			if (circle.add(stack)) {
				stack.decrementUnlessCreative(1, player);
				world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.6F, 0.8F + circle.count() * 0.08F);
				player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.placed", circle.count(), RitualCircleBlockEntity.SLOTS)
						.formatted(Formatting.GRAY), true);
			} else {
				player.sendMessage(Text.translatable("message.evolvedwarfare.ritual.full").formatted(Formatting.GRAY), true);
			}
		}
		return ItemActionResult.success(world.isClient);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof RitualCircleBlockEntity circle)) {
			return ActionResult.PASS;
		}
		if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
			if (player.isSneaking()) {
				ItemStack back = circle.pop();
				if (!back.isEmpty() && !player.giveItemStack(back)) {
					player.dropItem(back, false);
				}
			} else {
				Rituals.perform(sw, pos, sp, circle);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof RitualCircleBlockEntity circle) {
			ItemScatterer.spawn(world, pos, circle.items());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("block.evolvedwarfare.ritual_circle.desc").formatted(Formatting.GRAY));
		for (String rite : Rituals.NAMES) {
			tooltip.add(Text.literal("• ").append(Text.translatable("rite.evolvedwarfare." + rite)).formatted(Formatting.DARK_PURPLE));
		}
	}
}
