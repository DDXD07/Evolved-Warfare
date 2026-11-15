package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * El aserradero: una mesa con una sierra circular. Sierra lo que le echen encima (o le metan) y lo echa por delante (a un
 * cofre o tolva si lo hay). Corta a quien se suba.
 */
public class SawmillBlock extends SideShaftMachineBlock {
	public static final MapCodec<SawmillBlock> CODEC = createCodec(SawmillBlock::new);
	public static final float IMPACT = 3.0F, DAMAGE = 3.0F;
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 12, 16);

	public SawmillBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected float machineImpact() {
		return IMPACT;
	}

	@Override
	public boolean hasInventory() {
		return true;
	}

	@Nullable
	@Override
	public MachineRecipes.Recipe recipe(ItemStack stack) {
		return MachineRecipes.saw(stack);
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		Box top = new Box(pos.getX(), pos.getY() + 0.6, pos.getZ(), pos.getX() + 1, pos.getY() + 1.5, pos.getZ() + 1);
		if (world.getTime() % 10 == 0) {
			be.suck(world, top);
			if (rpm > 0.01F) {
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, top, LivingEntity::isAlive)) {
					e.damage(world.getDamageSources().generic(), DAMAGE);
				}
			}
		}
		ItemStack in = be.input().copy();
		if (be.process(rpm, world.random)) {
			world.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundCategory.BLOCKS, 0.6F, 0.8F + world.random.nextFloat() * 0.3F);
			if (in.getItem() instanceof net.minecraft.item.BlockItem bi) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, bi.getBlock().getDefaultState()), pos.getX() + 0.5, pos.getY() + 0.8,
						pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.05);
			}
		} else if (rpm > 0.01F && !in.isEmpty() && world.getTime() % 6 == 0) {
			world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.15F, 1.6F);
		}
		eject(world, pos, state, be);
	}

	/** Echa lo hecho por delante: a un cofre o tolva, o al suelo si está libre. */
	private static void eject(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be) {
		Direction f = facing(state);
		BlockPos front = pos.offset(f);
		Inventory inv = HopperBlockEntity.getInventoryAt(world, front);
		for (int i = 1; i < MachineBlockEntity.SIZE; i++) {
			ItemStack s = be.items.get(i);
			if (s.isEmpty()) {
				continue;
			}
			if (inv != null) {
				ItemStack rest = HopperBlockEntity.transfer(null, inv, s.copy(), f.getOpposite());
				if (rest.getCount() != s.getCount()) {
					be.items.set(i, rest);
					be.changed();
				}
			} else if (world.getBlockState(front).getCollisionShape(world, front).isEmpty()) {
				ItemEntity e = new ItemEntity(world, pos.getX() + 0.5 + f.getOffsetX() * 0.7, pos.getY() + 0.8, pos.getZ() + 0.5 + f.getOffsetZ() * 0.7,
						s.copy(), f.getOffsetX() * 0.12, 0.05, f.getOffsetZ() * 0.12);
				e.setToDefaultPickupDelay();
				world.spawnEntity(e);
				be.items.set(i, ItemStack.EMPTY);
				be.changed();
			}
		}
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
