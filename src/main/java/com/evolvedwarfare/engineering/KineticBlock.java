package com.evolvedwarfare.engineering;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Lo común a los bloques de la red de fuerza: su entidad, avisar a la red al ponerlos o quitarlos, y la información. */
public abstract class KineticBlock extends BlockWithEntity implements Kinetic {
	protected KineticBlock(Settings settings) {
		super(settings);
	}

	/** Los que solo se ven con su modelo girando (los demás son bloques normales). */
	protected boolean drawnByRenderer() {
		return true;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return drawnByRenderer() ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new KineticBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, Engineering.KINETIC_ENTITY, KineticBlockEntity::tick);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (world instanceof ServerWorld sw) {
			if (world.getBlockEntity(pos) instanceof KineticBlockEntity be) {
				be.refreshSource();
			}
			KineticNetwork.markDirty(sw, pos);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
		if (world instanceof ServerWorld sw && !state.isOf(newState.getBlock())) {
			KineticNetwork.markDirty(sw, pos);
		}
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (!world.isClient && world.getBlockEntity(pos) instanceof KineticBlockEntity be) {
			player.sendMessage(info(be), true);
		}
		return ActionResult.SUCCESS;
	}

	/** «12 rpm · fuerza 320/1024», o lo que le pasa. */
	public static Text info(KineticBlockEntity be) {
		if (be.networkState() == KineticBlockEntity.JAMMED) {
			return Text.translatable("message.evolvedwarfare.kinetic.jammed").formatted(Formatting.RED);
		}
		if (be.networkState() == KineticBlockEntity.OVERSPEED) {
			return Text.translatable("message.evolvedwarfare.kinetic.overspeed").formatted(Formatting.RED);
		}
		if (be.networkState() == KineticBlockEntity.OVERSTRESSED) {
			return Text.translatable("message.evolvedwarfare.kinetic.overstressed", Math.round(be.stress()), Math.round(be.capacity()))
					.formatted(Formatting.RED);
		}
		if (be.problem() == 1) {
			return Text.translatable("message.evolvedwarfare.kinetic.blocked").formatted(Formatting.YELLOW);
		}
		if (be.problem() == 2) {
			return Text.translatable("message.evolvedwarfare.kinetic.no_water").formatted(Formatting.YELLOW);
		}
		if (Math.abs(be.speed()) < 0.01F) {
			return Text.translatable("message.evolvedwarfare.kinetic.still").formatted(Formatting.GRAY);
		}
		return Text.translatable("message.evolvedwarfare.kinetic.info", String.format("%.1f", Math.abs(be.speed())), Math.round(be.stress()),
				Math.round(be.capacity())).formatted(Formatting.GOLD);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
	}
}
