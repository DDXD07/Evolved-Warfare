package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Caja de cultivos: clic derecho abre su hueco (hasta 64 de un cultivo); agachado y con la mano vacía, clic derecho
 * la coge con lo que lleva dentro. Rota, también suelta la caja con lo suyo (en creativo, si lleva algo).
 */
public class CropCrateBlock extends BlockWithEntity {
	public static final MapCodec<CropCrateBlock> CODEC = createCodec(CropCrateBlock::new);
	/** Caja abierta por arriba: el fondo y las cuatro paredes (para poder mirar dentro y para la luz). */
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0, 0, 0, 16, 2, 16),
			Block.createCuboidShape(0, 2, 0, 16, 15, 2),
			Block.createCuboidShape(0, 2, 14, 16, 15, 16),
			Block.createCuboidShape(0, 2, 2, 2, 15, 14),
			Block.createCuboidShape(14, 2, 2, 16, 15, 14));
	private static final VoxelShape OUTLINE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

	public CropCrateBlock(Settings settings) {
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

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CropCrateBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		return SHAPE;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof CropCrateBlockEntity crate) {
			player.openHandledScreen(crate);
		}
		return ActionResult.success(world.isClient);
	}

	/**
	 * Agachado y con la mano vacía: se coge la caja. Va por el evento (antes que vanilla) para que funcione aunque lleves
	 * algo en la otra mano.
	 */
	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != Hand.MAIN_HAND || !player.isSneaking() || player.isSpectator() || !player.getMainHandStack().isEmpty()
					|| !world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.CROP_CRATE)) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				pickUp(world, hit.getBlockPos(), player);
			}
			return ActionResult.success(world.isClient);
		});
	}

	/** Quita la caja del mundo y se la da al jugador, con lo que lleve dentro. */
	public static boolean pickUp(World world, BlockPos pos, PlayerEntity player) {
		if (!(world.getBlockEntity(pos) instanceof CropCrateBlockEntity crate) || !player.canModifyBlocks()
				|| !world.canPlayerModifyAt(player, pos)) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		ItemStack stack = new ItemStack(state.getBlock());
		stack.applyComponentsFrom(crate.createComponentMap());
		crate.clear();
		world.removeBlock(pos, false);
		world.emitGameEvent(player, GameEvent.BLOCK_DESTROY, pos);
		if (player.getMainHandStack().isEmpty()) {
			player.setStackInHand(Hand.MAIN_HAND, stack);
		} else {
			player.getInventory().offerOrDrop(stack);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.7F, 1.15F);
		return true;
	}

	/** En creativo no se suelta nada al romper: si lleva algo, que no se pierda. */
	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && player.isCreative() && world.getBlockEntity(pos) instanceof CropCrateBlockEntity crate && !crate.isEmpty()) {
			ItemStack stack = new ItemStack(state.getBlock());
			stack.applyComponentsFrom(crate.createComponentMap());
			ItemEntity drop = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
			drop.setToDefaultPickupDelay();
			world.spawnEntity(drop);
			crate.clear();
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}
}
