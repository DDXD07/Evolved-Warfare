package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El molde de arcilla en el suelo: al ponerlo se elige la pieza y se talla (el minijuego); luego se vierte el metal del
 * crisol (manteniendo clic derecho con él), se espera a que cuaje y se enfríe, y a golpes (clic derecho) se rompe y sale la
 * pieza en bruto. Tocar el metal caliente quema.
 */
public class MoldBlock extends BlockWithEntity {
	public static final MapCodec<MoldBlock> CODEC = createCodec(MoldBlock::new);
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

	public MoldBlock(Settings settings) {
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
		return new MoldBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlocks.CLAY_MOLD_ENTITY, MoldBlockEntity::tick);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient && placer instanceof ServerPlayerEntity player) {
			ServerPlayNetworking.send(player, new MoldPayloads.Open(pos));
		}
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (stack.getItem() instanceof CrucibleItem) {
			return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // lo hace el crisol (verter)
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof MoldBlockEntity mold)) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		switch (mold.stage()) {
			case MoldBlockEntity.BLANK -> {
				if (player instanceof ServerPlayerEntity sp) {
					ServerPlayNetworking.send(sp, new MoldPayloads.Open(pos));
				}
			}
			case MoldBlockEntity.POURING, MoldBlockEntity.COOLING -> {
				// ¡quema!
				player.damage(world.getDamageSources().hotFloor(), 1.0F);
				world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.8F);
			}
			case MoldBlockEntity.SOLID -> {
				ItemStack piece = mold.crack();
				player.swingHand(Hand.MAIN_HAND, true);
				if (piece != null) {
					world.removeBlock(pos, false);
					world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_SHATTER, SoundCategory.BLOCKS, 1.0F, 0.8F);
					drop(world, pos, piece);
					drop(world, pos, new ItemStack(Items.CLAY_BALL));
				}
			}
			default -> {
			}
		}
		return ActionResult.SUCCESS;
	}

	private static void drop(World world, BlockPos pos, ItemStack stack) {
		ItemEntity e = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, stack);
		e.setToDefaultPickupDelay();
		world.spawnEntity(e);
	}

	/** Al romperlo: en blanco, el molde; si no, la arcilla (y la pieza, si ya ha cuajado). */
	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof MoldBlockEntity mold && !player.isCreative()) {
			if (mold.stage() == MoldBlockEntity.BLANK) {
				drop(world, pos, new ItemStack(ModBlocks.CLAY_MOLD));
			} else {
				drop(world, pos, new ItemStack(Items.CLAY_BALL));
				ItemStack piece = mold.result();
				if (piece != null) {
					drop(world, pos, piece);
				}
			}
		}
		return super.onBreak(world, pos, state, player);
	}
}
