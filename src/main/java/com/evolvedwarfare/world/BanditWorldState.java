package com.evolvedwarfare.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** Cuándo toca la próxima patrulla y el próximo asalto (se guarda con el mundo). */
public class BanditWorldState extends PersistentState {
	private static final String ID = "evolvedwarfare_bandits";
	public static final Type<BanditWorldState> TYPE = new Type<>(BanditWorldState::new, BanditWorldState::fromNbt, null);

	long nextPatrol = -1L;
	long nextRaid = -1L;

	public static BanditWorldState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	private static BanditWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		BanditWorldState state = new BanditWorldState();
		state.nextPatrol = nbt.getLong("NextPatrol");
		state.nextRaid = nbt.getLong("NextRaid");
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putLong("NextPatrol", nextPatrol);
		nbt.putLong("NextRaid", nextRaid);
		return nbt;
	}
}
