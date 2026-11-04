package com.evolvedwarfare.orchard;

import com.evolvedwarfare.climate.CropSeasons;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;

/**
 * Las hojas de un frutal: con el tiempo (en sus estaciones) florecen (edad 1), echan fruta verde (2) y la fruta madura (3),
 * que se ve colgando de las hojas. Clic derecho a la fruta madura para cogerla. Fuera de temporada no crecen y la fruta
 * madura acaba cayéndose. Con harina de hueso, un paso más.
 */
public class FruitLeavesBlock extends LeavesBlock implements Fertilizable {
	public static final IntProperty AGE = Properties.AGE_3;
	public static final int RIPE = 3;
	/** Una de cada tantas veces que le toca, crece (unos 3 minutos por paso). */
	private static final int GROW_CHANCE = 3;
	public final FruitKind kind;

	public FruitLeavesBlock(FruitKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
		setDefaultState(getDefaultState().with(AGE, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(AGE);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (shouldDecay(state)) {
			super.randomTick(state, world, pos, random);
			return;
		}
		int age = state.get(AGE);
		if (CropSeasons.outOfSeason(world, this)) {
			// fuera de temporada: la fruta madura se cae y las flores se pierden
			if (age > 0 && random.nextInt(4) == 0) {
				world.setBlockState(pos, state.with(AGE, 0), Block.NOTIFY_LISTENERS);
			}
			return;
		}
		if (age < RIPE && random.nextInt(GROW_CHANCE) == 0) {
			world.setBlockState(pos, state.with(AGE, age + 1), Block.NOTIFY_LISTENERS);
		}
	}

	/** Coge la fruta madura (de 1 a 3): la deja caer y las hojas vuelven a empezar. Devuelve cuántas. */
	public static int harvest(World world, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof FruitLeavesBlock leaves) || state.get(AGE) < RIPE) {
			return 0;
		}
		int n = 1 + world.random.nextInt(3);
		Block.dropStack(world, pos, new ItemStack(leaves.kind.fruit(), n));
		world.setBlockState(pos, state.with(AGE, 0), Block.NOTIFY_LISTENERS);
		world.playSound(null, pos, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
		world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(state));
		return n;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (state.get(AGE) < RIPE) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			harvest(world, pos, state);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		return state.get(AGE) < RIPE;
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return !CropSeasons.outOfSeason(world, this);
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(AGE, Math.min(RIPE, state.get(AGE) + 1)), Block.NOTIFY_LISTENERS);
	}
}
