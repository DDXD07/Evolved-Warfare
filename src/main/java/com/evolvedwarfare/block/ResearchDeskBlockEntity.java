package com.evolvedwarfare.block;

import com.evolvedwarfare.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La mesa avanza la investigación de su dueño mientras esté cargada. Cada estantería a menos de dos bloques (como en la
 * mesa de encantamientos, hasta 15) la hace un 10 % más rápida.
 */
public class ResearchDeskBlockEntity extends BlockEntity {
	public static final int MAX_SHELVES = 15;
	@Nullable
	private UUID owner;
	private int shelves;

	public ResearchDeskBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.RESEARCH_DESK_ENTITY, pos, state);
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		markDirty();
	}

	public boolean canUse(PlayerEntity player) {
		return owner == null || owner.equals(player.getUuid()) || player.getAbilities().creativeMode;
	}

	public void claim(PlayerEntity player) {
		if (owner == null) {
			setOwner(player.getUuid());
		}
	}

	/** 1 + 10 % por estantería. */
	public double speed() {
		return 1.0 + 0.1 * shelves;
	}

	private int countShelves(World world) {
		int count = 0;
		for (BlockPos at : BlockPos.iterate(pos.add(-2, 0, -2), pos.add(2, 2, 2))) {
			if ((Math.abs(at.getX() - pos.getX()) == 2 || Math.abs(at.getZ() - pos.getZ()) == 2)
					&& world.getBlockState(at).isOf(Blocks.BOOKSHELF)) {
				count++;
			}
		}
		return Math.min(MAX_SHELVES, count);
	}

	public static void tick(World world, BlockPos pos, BlockState state, ResearchDeskBlockEntity desk) {
		if (!(world instanceof ServerWorld serverWorld) || desk.owner == null) {
			return;
		}
		if (world.getTime() % 100 == 0) {
			desk.shelves = desk.countShelves(world);
		}
		if (world.getTime() % 40 == 0 && world.random.nextInt(3) == 0) {
			serverWorld.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.2);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
	}
}
