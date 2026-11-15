package com.evolvedwarfare.engineering;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lo que guarda cada bloque de la red de fuerza: su velocidad, la fuerza que hay y la que se gasta en su red, si está
 * atascada o sobrecargada, y (las fuentes) lo que dan. En el cliente lleva el ángulo para dibujarlo girando sin saltos.
 */
public class KineticBlockEntity extends BlockEntity {
	public static final int OK = 0, JAMMED = 1, OVERSTRESSED = 2;
	/** La velocidad de este bloque (rpm, con signo según su eje). */
	float speed;
	float capacity, stress;
	int state;
	/** Lo que da como fuente (rpm con signo) y su fuerza; lo que impide que dé (0 nada, 1 estorbo, 2 sin agua). */
	float generated, generatedCapacity;
	int problem;
	/** Para pruebas y depuración: un gasto de fuerza añadido (por rpm). */
	float extraImpact;
	/** La manivela: tics que le quedan de girar. */
	int crankTicks;
	/** En el cliente: el ángulo (en grados) para dibujarlo. */
	public float angle, prevAngle;

	public KineticBlockEntity(BlockPos pos, BlockState state) {
		super(Engineering.KINETIC_ENTITY, pos, state);
	}

	protected KineticBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public float speed() {
		return speed;
	}

	public float capacity() {
		return capacity;
	}

	public float stress() {
		return stress;
	}

	public int networkState() {
		return state;
	}

	public float generated() {
		return generated;
	}

	public int problem() {
		return problem;
	}

	public void setExtraImpact(float impact) {
		extraImpact = impact;
		if (world instanceof ServerWorld sw) {
			KineticNetwork.markDirty(sw, pos);
		}
	}

	/** Para pruebas: hace girar la manivela durante esos tics. */
	public void turnCrank(int ticks) {
		crankTicks = Math.max(crankTicks, ticks);
	}

	public static void tick(World world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		if (world.isClient) {
			be.prevAngle = be.angle;
			be.angle = (be.angle + be.speed * 0.3F) % 360.0F;
			return;
		}
		if (!(state.getBlock() instanceof Kinetic k) || !(world instanceof ServerWorld sw)) {
			return;
		}
		if (be.crankTicks > 0) {
			be.crankTicks--;
		}
		int every = k.sourceInterval();
		if (every > 0 && (world.getTime() + pos.asLong()) % every == 0) {
			float g = k.generate(sw, pos, state, be);
			float c = g == 0 ? 0 : k.capacity(sw, pos, state, be);
			if (Math.abs(g - be.generated) > 0.01F || Math.abs(c - be.generatedCapacity) > 0.5F) {
				be.generated = g;
				be.generatedCapacity = c;
				KineticNetwork.markDirty(sw, pos);
			}
		}
	}

	/** Vuelve a mirar ya lo que da como fuente (al girar la manivela, al ponerla...). */
	public void refreshSource() {
		if (world instanceof ServerWorld sw && getCachedState().getBlock() instanceof Kinetic k && k.sourceInterval() > 0) {
			float g = k.generate(sw, pos, getCachedState(), this);
			float c = g == 0 ? 0 : k.capacity(sw, pos, getCachedState(), this);
			if (Math.abs(g - generated) > 0.01F || Math.abs(c - generatedCapacity) > 0.5F) {
				generated = g;
				generatedCapacity = c;
				KineticNetwork.markDirty(sw, pos);
			}
		}
	}

	/** Lo pone el solucionador de la red; avisa a los clientes si ha cambiado. */
	void apply(float speed, float capacity, float stress, int state) {
		boolean changed = Math.abs(speed - this.speed) > 0.01F || Math.abs(capacity - this.capacity) > 0.5F || Math.abs(stress - this.stress) > 0.5F
				|| state != this.state;
		this.speed = speed;
		this.capacity = capacity;
		this.stress = stress;
		this.state = state;
		if (changed && world != null) {
			markDirty();
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		speed = nbt.getFloat("Speed");
		capacity = nbt.getFloat("Capacity");
		stress = nbt.getFloat("Stress");
		state = nbt.getInt("State");
		generated = nbt.getFloat("Generated");
		generatedCapacity = nbt.getFloat("GenCapacity");
		problem = nbt.getInt("Problem");
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putFloat("Speed", speed);
		nbt.putFloat("Capacity", capacity);
		nbt.putFloat("Stress", stress);
		nbt.putInt("State", state);
		nbt.putFloat("Generated", generated);
		nbt.putFloat("GenCapacity", generatedCapacity);
		nbt.putInt("Problem", problem);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
