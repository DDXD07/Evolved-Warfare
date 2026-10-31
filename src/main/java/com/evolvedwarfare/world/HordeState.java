package com.evolvedwarfare.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** Las hordas (se guarda con el mundo): el último día que vino una, los avisos ya dados y las que están en marcha. */
public class HordeState extends PersistentState {
	private static final String ID = "evolvedwarfare_hordes";
	public static final Type<HordeState> TYPE = new Type<>(HordeState::new, HordeState::fromNbt, null);

	/** Último día con horda (para que no vuelva a venir ese día). */
	long lastDay;
	/** Último día avisado al amanecer y la víspera. */
	long dawnWarned;
	long eveWarned;
	final List<Hordes.Horde> hordes = new ArrayList<>();

	public static HordeState get(ServerWorld world) {
		return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	private static HordeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		HordeState state = new HordeState();
		state.lastDay = nbt.getLong("LastDay");
		state.dawnWarned = nbt.getLong("DawnWarned");
		state.eveWarned = nbt.getLong("EveWarned");
		NbtList list = nbt.getList("Active", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			state.hordes.add(Hordes.Horde.fromNbt(list.getCompound(i)));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putLong("LastDay", lastDay);
		nbt.putLong("DawnWarned", dawnWarned);
		nbt.putLong("EveWarned", eveWarned);
		NbtList list = new NbtList();
		for (Hordes.Horde horde : hordes) {
			list.add(horde.toNbt());
		}
		nbt.put("Active", list);
		return nbt;
	}

	/** El último día que vino una horda (para el comando y las pruebas). */
	public long lastDay() {
		return lastDay;
	}
}
