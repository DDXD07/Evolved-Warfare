package com.evolvedwarfare.fluid;

import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Lo bombeado que queda (capas de agua que se pueden subir). */
public class HandPumpBlockEntity extends BlockEntity {
	public static final int MAX = 32;
	public static final int PER_STROKE = 2;
	private int charge;
	private long lastStroke = -100L;

	public HandPumpBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.HAND_PUMP_ENTITY, pos, state);
	}

	public int charge() {
		return charge;
	}

	/** Un golpe de palanca (como mucho uno cada 5 tics). */
	public boolean stroke(long now) {
		if (now - lastStroke < 5L) {
			return false;
		}
		lastStroke = now;
		charge = Math.min(MAX, charge + PER_STROKE);
		markDirty();
		return true;
	}

	public void use(int units) {
		charge = Math.max(0, charge - units);
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Charge", charge);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		charge = nbt.getInt("Charge");
	}
}
