package com.evolvedwarfare.engineering;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.smithing.ForgeMaterial;
import com.evolvedwarfare.smithing.ForgedPart;
import com.evolvedwarfare.smithing.PartData;
import com.evolvedwarfare.smithing.Smithing;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * El martinete: un mazo de hierro en un brazo que levantan las levas del eje (cuatro golpes por vuelta). Golpea el yunque
 * que tiene delante, un bloque más abajo: las piezas forjadas en bruto que haya encima se afilan solas tras sus golpes,
 * con una nota media (sin el ojo del herrero no salen perfectas).
 */
public class TripHammerBlock extends SideShaftMachineBlock {
	public static final MapCodec<TripHammerBlock> CODEC = createCodec(TripHammerBlock::new);
	public static final float IMPACT = 3.0F, DAMAGE = 6.0F;
	/** La nota del afilado del martinete (de 0 a 1). */
	public static final float HAMMER_SCORE = 0.6F;
	/** Grados por golpe (cuatro levas). */
	public static final float CAM = 90.0F;

	public TripHammerBlock(Settings settings) {
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

	/** Donde cae el mazo: el bloque de delante (el yunque está debajo). */
	public static BlockPos strikePos(BlockPos pos, BlockState state) {
		return pos.offset(facing(state));
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		if (rpm < 0.01F) {
			return;
		}
		float before = be.phase;
		be.phase += rpm * 0.3F;
		if ((int) (be.phase / CAM) != (int) (before / CAM)) {
			strike(world, pos, state, be);
		}
		if (be.phase >= 360.0F) {
			be.phase -= 360.0F;
		}
	}

	private static void strike(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be) {
		BlockPos at = strikePos(pos, state);
		BlockPos anvil = at.down();
		Box box = new Box(at.getX(), at.getY() - 0.2, at.getZ(), at.getX() + 1, at.getY() + 0.6, at.getZ() + 1);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			e.damage(world.getDamageSources().fallingAnvil(null), DAMAGE);
		}
		if (!world.getBlockState(anvil).isIn(BlockTags.ANVIL)) {
			world.playSound(null, at, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.6F, 0.6F);
			return;
		}
		world.spawnParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.2);
		ItemEntity work = null;
		for (ItemEntity e : world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive)) {
			if (part(e.getStack()) != null) {
				work = e;
				break;
			}
		}
		if (work == null) {
			be.target = -1;
			be.hits = 0;
			world.playSound(null, at, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.25F, 1.5F);
			return;
		}
		if (work.getId() != be.target) {
			be.target = work.getId();
			be.hits = 0;
		}
		be.hits++;
		ItemStack stack = work.getStack();
		PartData data = stack.get(ModComponents.FORGED_PART);
		ForgedPart part = ForgedPart.byId(data.part());
		ForgeMaterial material = ForgeMaterial.byId(data.material());
		world.spawnParticles(ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 2, 0.1, 0.0, 0.1, 0.0);
		if (be.hits >= part.strikes) {
			float cast = stack.getOrDefault(ModComponents.CAST_SCORE, 0.5F);
			work.setStack(Smithing.part(part, material, Smithing.finalQuality(cast, HAMMER_SCORE)));
			be.target = -1;
			be.hits = 0;
			world.playSound(null, at, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8F, 1.0F);
		} else {
			world.playSound(null, at, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.5F, 1.2F + world.random.nextFloat() * 0.2F);
		}
	}

	/** La pieza en bruto (calidad 0) que se puede afilar, o null. */
	@Nullable
	public static ForgedPart part(ItemStack stack) {
		PartData data = stack.get(ModComponents.FORGED_PART);
		if (!stack.isOf(ModItems.FORGED_PART) || data == null || data.quality() > 0) {
			return null;
		}
		ForgedPart part = ForgedPart.byId(data.part());
		ForgeMaterial material = ForgeMaterial.byId(data.material());
		return part == null || material == null || part.result(material) == null ? null : part;
	}
}
