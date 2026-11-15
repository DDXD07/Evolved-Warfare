package com.evolvedwarfare.engineering;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Una máquina de la red de fuerza (fase 73): gasta fuerza por cada rpm y trabaja más deprisa cuanto más rápido gira. Las
 * que guardan cosas (muela, aserradero) se cargan con clic derecho con lo que se quiere trabajar (o tirándolo encima, o
 * con tolvas), y con la mano vacía se recoge lo hecho.
 */
public abstract class MachineBlock extends KineticBlock {
	protected MachineBlock(Settings settings) {
		super(settings);
	}

	/** La fuerza que gasta por cada rpm. */
	protected abstract float machineImpact();

	@Override
	public float impact(BlockState state) {
		return machineImpact();
	}

	public boolean hasInventory() {
		return false;
	}

	@Nullable
	public MachineRecipes.Recipe recipe(ItemStack stack) {
		return null;
	}

	/** Cada tic, en el servidor, con su velocidad (sin signo). */
	public abstract void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm);

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MachineBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, Engineering.MACHINE_ENTITY, MachineBlockEntity::tick);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			ItemScatterer.spawn(world, pos, be);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (!hasInventory() || player.isSneaking() || stack.isEmpty()) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(world.getBlockEntity(pos) instanceof MachineBlockEntity be) || recipe(stack) == null) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!world.isClient) {
			ItemStack moving = player.isCreative() ? stack.copy() : stack;
			if (be.insert(moving) > 0) {
				world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4F, 0.8F);
			}
		}
		return ItemActionResult.success(world.isClient);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.isSneaking() || !hasInventory()) {
			if (player.isSneaking() && hasInventory() && !world.isClient && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
				player.sendMessage(KineticBlock.info(be), true);
				ItemStack in = be.input();
				MachineRecipes.Recipe r = recipe(in);
				if (r != null) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.machine.contents", in.getCount() + "× " + in.getName().getString(),
							Math.round(100.0F * be.progress() / r.work())).formatted(Formatting.GRAY), false);
				}
				return ActionResult.SUCCESS;
			}
			return super.onUse(state, world, pos, player, hit);
		}
		if (!world.isClient && world.getBlockEntity(pos) instanceof MachineBlockEntity be) {
			ItemStack out = be.takeOutput();
			if (out.isEmpty()) {
				player.sendMessage(KineticBlock.info(be), true);
			} else {
				player.getInventory().offerOrDrop(out);
				world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4F, 1.2F);
			}
		}
		return ActionResult.SUCCESS;
	}
}
