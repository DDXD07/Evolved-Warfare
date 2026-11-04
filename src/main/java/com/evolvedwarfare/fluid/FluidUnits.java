package com.evolvedwarfare.fluid;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Las capas exactas de los bloques de líquido a medio llenar (de 1 a 24 de 25; el bloque solo enseña 7 alturas). Solo se
 * guardan los que no están llenos; si el bloque ha cambiado por otra cosa (y su altura ya no cuadra), no se usa.
 */
public class FluidUnits extends PersistentState {
	private static final String ID = "evolvedwarfare_fluid_units";
	public static final Type<FluidUnits> TYPE = new Type<>(FluidUnits::new, FluidUnits::fromNbt, null);

	private final Long2ByteOpenHashMap units = new Long2ByteOpenHashMap();

	public static FluidUnits get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	/** Lo guardado, o 0. */
	public int get(BlockPos pos) {
		return units.get(pos.asLong());
	}

	public void set(BlockPos pos, int amount) {
		long key = pos.asLong();
		if (amount <= 0 || amount >= FiniteFluids.FULL) {
			if (units.remove(key) != 0) {
				markDirty();
			}
		} else if (units.put(key, (byte) amount) != amount) {
			markDirty();
		}
	}

	private static FluidUnits fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		FluidUnits state = new FluidUnits();
		long[] keys = nbt.getLongArray("Pos");
		byte[] values = nbt.getByteArray("Units");
		for (int i = 0; i < Math.min(keys.length, values.length); i++) {
			state.units.put(keys[i], values[i]);
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		long[] keys = new long[units.size()];
		byte[] values = new byte[units.size()];
		int i = 0;
		for (Long2ByteMap.Entry e : units.long2ByteEntrySet()) {
			keys[i] = e.getLongKey();
			values[i++] = e.getByteValue();
		}
		nbt.putLongArray("Pos", keys);
		nbt.putByteArray("Units", values);
		return nbt;
	}
}
