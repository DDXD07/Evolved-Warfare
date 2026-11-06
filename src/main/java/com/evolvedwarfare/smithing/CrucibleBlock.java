package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * El crisol: un cuenco de ladrillo refractario. Encima de una hoguera, fuego, lava o magma, se le echan lingotes al rojo
 * (clic derecho con ellos) y se funden; con la mano vacía se coge (con el metal dentro) para verterlo en un molde.
 */
public class CrucibleBlock extends BlockWithEntity {
	public static final MapCodec<CrucibleBlock> CODEC = createCodec(CrucibleBlock::new);
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(3, 0, 3, 13, 2, 13),
			Block.createCuboidShape(2, 2, 2, 14, 10, 4),
			Block.createCuboidShape(2, 2, 12, 14, 10, 14),
			Block.createCuboidShape(2, 2, 4, 4, 10, 12),
			Block.createCuboidShape(12, 2, 4, 14, 10, 12));
	private static final VoxelShape OUTLINE = Block.createCuboidShape(2, 0, 2, 14, 10, 14);

	public CrucibleBlock(Settings settings) {
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
		return new CrucibleBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlocks.CRUCIBLE_ENTITY, CrucibleBlockEntity::tick);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	@Override
	protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		return SHAPE;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockHitResult hit) {
		if (!(stack.getItem() instanceof HotIngotItem hot)) {
			if (stack.isOf(net.minecraft.item.Items.IRON_INGOT) || stack.isOf(net.minecraft.item.Items.GOLD_INGOT)) {
				if (!world.isClient) {
					player.sendMessage(net.minecraft.text.Text.translatable("message.evolvedwarfare.crucible.cold")
							.formatted(net.minecraft.util.Formatting.GOLD), true);
				}
				return ItemActionResult.SUCCESS;
			}
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (!crucible.insert(hot.material)) {
			world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT_FAIL, SoundCategory.BLOCKS, 0.8F, 1.0F);
			return ItemActionResult.FAIL;
		}
		stack.decrementUnlessCreative(1, player);
		world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.4F);
		world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 0.8F, 0.7F);
		if (world instanceof net.minecraft.server.world.ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.15, 0.05, 0.15, 0.0);
		}
		return ItemActionResult.SUCCESS;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!player.getMainHandStack().isEmpty()) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			pickUp(world, pos, player);
		}
		return ActionResult.success(world.isClient);
	}

	/** Coge el crisol con lo que lleva (si estaba al fuego, empieza a enfriarse). */
	public static boolean pickUp(World world, BlockPos pos, PlayerEntity player) {
		if (!(world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) || !player.canModifyBlocks()) {
			return false;
		}
		ItemStack stack = stackOf(world, crucible);
		world.removeBlock(pos, false);
		world.emitGameEvent(player, GameEvent.BLOCK_DESTROY, pos);
		if (player.getMainHandStack().isEmpty()) {
			player.setStackInHand(Hand.MAIN_HAND, stack);
		} else {
			player.getInventory().offerOrDrop(stack);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_HIT, SoundCategory.BLOCKS, 0.8F, 0.8F);
		return true;
	}

	private static ItemStack stackOf(World world, CrucibleBlockEntity crucible) {
		ItemStack stack = new ItemStack(ModBlocks.CRUCIBLE);
		CrucibleContents c = crucible.contents();
		if (!c.isEmpty()) {
			if (c.molten() > 0.01F && c.coolAt() == 0L) {
				c = new CrucibleContents(c.metal(), c.solid(), c.molten(), world.getTime() + CrucibleBlockEntity.COOL_TICKS);
			}
			stack.set(ModComponents.CRUCIBLE, c);
		}
		return stack;
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible && (!player.isCreative() || !crucible.contents().isEmpty())) {
			ItemEntity drop = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stackOf(world, crucible));
			drop.setToDefaultPickupDelay();
			world.spawnEntity(drop);
			crucible.setContents(CrucibleContents.EMPTY);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (world.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible && crucible.contents().molten() > 0.01F) {
			double y = pos.getY() + 0.15 + crucible.contents().molten() / CrucibleBlockEntity.CAPACITY * 0.45;
			if (random.nextInt(3) == 0) {
				world.addParticle(ParticleTypes.LAVA, pos.getX() + 0.3 + random.nextDouble() * 0.4, y, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
			}
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, y + 0.1, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0,
					0.02, 0);
			if (random.nextInt(8) == 0) {
				world.playSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 0.3F, 1.2F, false);
			}
		}
	}
}
