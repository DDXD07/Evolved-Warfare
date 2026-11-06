package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEffects;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Los bloques del Nether bíblico que hacen algo. */
public final class InfernoBlocks {
	private InfernoBlocks() {
	}

	/** Sal fina: frena al andar y da sed de sal (en el Nether, hasta salir). */
	public static class FineSalt extends Block {
		public static final MapCodec<FineSalt> CODEC = createCodec(FineSalt::new);

		public FineSalt(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
			if (!world.isClient && entity instanceof PlayerEntity player && world.getTime() % 20 == 0 && Curses.inNether(player)) {
				Curses.curse(player, ModEffects.SALT_THIRST);
			}
			super.onSteppedOn(world, pos, state, entity);
		}
	}

	/** Grieta del Abismo: echa humo negro por donde da al aire. */
	public static class Vent extends Block {
		public static final MapCodec<Vent> CODEC = createCodec(Vent::new);

		public Vent(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			for (Direction d : Direction.values()) {
				BlockPos out = pos.offset(d);
				if (random.nextInt(3) != 0 || !world.getBlockState(out).isAir()) {
					continue;
				}
				double x = out.getX() + 0.5 - d.getOffsetX() * 0.45 + (random.nextDouble() - 0.5) * 0.5;
				double y = out.getY() + 0.5 - d.getOffsetY() * 0.45 + (random.nextDouble() - 0.5) * 0.5;
				double z = out.getZ() + 0.5 - d.getOffsetZ() * 0.45 + (random.nextDouble() - 0.5) * 0.5;
				world.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, d.getOffsetX() * 0.03, 0.03 + d.getOffsetY() * 0.03, d.getOffsetZ() * 0.03);
				if (random.nextInt(5) == 0) {
					world.addParticle(ParticleTypes.SQUID_INK, x, y, z, d.getOffsetX() * 0.05, 0.05, d.getOffsetZ() * 0.05);
				}
			}
		}
	}

	/** Fruta de piedra (de los huertos petrificados): al tocarla se deshace en polvo. */
	public static class StoneFruit extends Block {
		public static final MapCodec<StoneFruit> CODEC = createCodec(StoneFruit::new);
		private static final VoxelShape SHAPE = Block.createCuboidShape(5, 3, 5, 11, 16, 11);

		public StoneFruit(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return SHAPE;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return net.minecraft.util.shape.VoxelShapes.empty();
		}

		private static void crumble(World world, BlockPos pos, BlockState state) {
			if (world instanceof ServerWorld sw) {
				sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, net.minecraft.block.Blocks.GRAVEL.getDefaultState()),
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.2, 0.2, 0.2, 0.0);
				sw.playSound(null, pos, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 0.8F, 0.6F);
			}
			world.removeBlock(pos, false);
		}

		@Override
		protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
			if (!world.isClient) {
				crumble(world, pos, state);
			}
			return ActionResult.success(world.isClient);
		}

		@Override
		protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
			if (!world.isClient) {
				crumble(world, pos, state);
			}
		}

		@Override
		protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
			if (!world.isClient && entity instanceof LivingEntity) {
				crumble(world, pos, state);
			}
		}

		@Override
		protected boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
			return !world.getBlockState(pos.up()).isAir();
		}
	}

	/** Piedra maldita (lo que corrompe la corrupción): pisarla trae una maldición y quema el alma. */
	public static class Cursed extends Block {
		public static final MapCodec<Cursed> CODEC = createCodec(Cursed::new);

		public Cursed(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
			if (!world.isClient && entity instanceof PlayerEntity player && world.getTime() % 40 == 0) {
				player.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.WITHER, 40, 0));
				if (Curses.inNether(player) && world.random.nextInt(4) == 0) {
					Curses.curse(player, Curses.random(world.random));
				}
			}
			super.onSteppedOn(world, pos, state, entity);
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			if (random.nextInt(4) == 0 && world.getBlockState(pos.up()).isAir()) {
				world.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
			}
		}
	}
}
