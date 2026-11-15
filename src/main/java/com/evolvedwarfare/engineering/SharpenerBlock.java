package com.evolvedwarfare.engineering;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * La afiladora (fase 79): una muela de piedra movida por la red. Se le mete un arma o una herramienta gastada y la
 * repara hasta el 80 % de su durabilidad (una muela no hace milagros) y la deja afilada: +1,5 de daño durante 40 golpes.
 * Gasta 2 por rpm.
 */
public class SharpenerBlock extends SideShaftMachineBlock {
	public static final MapCodec<SharpenerBlock> CODEC = createCodec(SharpenerBlock::new);
	public static final float IMPACT = 2.0F;
	/** Hasta dónde repara (fracción de la durabilidad que se puede gastar: 0,2 = deja al 80 %). */
	public static final float KEEP_WORN = 0.2F;
	/** Trabajo por punto de durabilidad, y por el afilado. */
	public static final int PER_POINT = 8, SHARPEN = 320;
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

	public SharpenerBlock(Settings settings) {
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

	/** ¿Es un arma o herramienta (algo que hace daño y se gasta)? */
	public static boolean sharpenable(ItemStack stack) {
		if (!stack.isDamageable()) {
			return false;
		}
		AttributeModifiersComponent mods = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		for (AttributeModifiersComponent.Entry e : mods.modifiers()) {
			if (e.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	@Override
	public MachineRecipes.Recipe recipe(ItemStack stack) {
		if (!sharpenable(stack)) {
			return null;
		}
		int floor = Math.round(stack.getMaxDamage() * KEEP_WORN);
		int repair = Math.max(0, stack.getDamage() - floor);
		boolean sharp = Sharpening.hitsLeft(stack) >= Sharpening.HITS;
		if (repair == 0 && sharp) {
			return null;
		}
		int work = Math.max(1, repair * PER_POINT + (sharp ? 0 : SHARPEN));
		return new MachineRecipes.Recipe(work, r -> {
			ItemStack out = stack.copy();
			out.setDamage(Math.min(out.getDamage(), floor));
			return List.of(Sharpening.apply(out));
		});
	}

	@Override
	public void work(ServerWorld world, BlockPos pos, BlockState state, MachineBlockEntity be, float rpm) {
		boolean working = rpm > 0.01F && !be.input().isEmpty();
		if (be.process(rpm, world.random)) {
			world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.8F, 1.2F);
		} else if (working && world.getTime() % 4 == 0) {
			world.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.15);
			if (world.getTime() % 16 == 0) {
				world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.25F, 1.6F);
			}
		}
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
