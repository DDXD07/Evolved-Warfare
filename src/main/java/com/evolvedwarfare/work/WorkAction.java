package com.evolvedwarfare.work;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Una tarea concreta: ir a un sitio (o junto a una entidad), trabajar un rato y, al terminar, hacer el cambio
 * en el mundo. El cambio se vuelve a comprobar al final, porque el mundo puede haber cambiado mientras tanto.
 */
public final class WorkAction {
	/** Lo que pasa al terminar. Devuelve {@code true} si de verdad se hizo (cuenta como tarea hecha). */
	@FunctionalInterface
	public interface Performer {
		boolean perform(WorkContext ctx);
	}

	/** Efectos mientras trabaja (partículas, sonidos). */
	@FunctionalInterface
	public interface Effect {
		void tick(WorkContext ctx, int ticks);
	}

	private final BlockPos walkTo;
	@Nullable
	private final Entity entity;
	private final Vec3d lookAt;
	private final int duration;
	private final Performer performer;
	private double reach = 2.8;
	private boolean remote;
	@Nullable
	private BlockPos breaking;
	@Nullable
	private Effect effect;

	private WorkAction(BlockPos walkTo, @Nullable Entity entity, Vec3d lookAt, int duration, Performer performer) {
		this.walkTo = walkTo.toImmutable();
		this.entity = entity;
		this.lookAt = lookAt;
		this.duration = Math.max(1, duration);
		this.performer = performer;
	}

	/** Ir junto a un bloque y trabajar en él. */
	public static WorkAction atBlock(BlockPos pos, int duration, Performer performer) {
		return new WorkAction(pos, null, Vec3d.ofCenter(pos), duration, performer);
	}

	/** Ir junto a una entidad (un animal, un objeto en el suelo). */
	public static WorkAction atEntity(Entity entity, int duration, Performer performer) {
		return new WorkAction(entity.getBlockPos(), entity, entity.getPos(), duration, performer).reach(2.2);
	}

	/**
	 * Trabajo que se hace desde el puesto (mina, pesca, talleres): va al puesto y, si no consigue llegar,
	 * trabaja desde donde esté.
	 */
	public static WorkAction fromSite(BlockPos site, Vec3d lookAt, int duration, Performer performer) {
		WorkAction action = new WorkAction(site, null, lookAt, duration, performer);
		action.remote = true;
		action.reach = 3.0;
		return action;
	}

	public WorkAction reach(double reach) {
		this.reach = reach;
		return this;
	}

	/** Muestra las grietas de "picando" sobre ese bloque mientras trabaja. */
	public WorkAction breaking(BlockPos pos) {
		this.breaking = pos.toImmutable();
		return this;
	}

	public WorkAction effect(Effect effect) {
		this.effect = effect;
		return this;
	}

	public BlockPos walkTo() {
		return walkTo;
	}

	@Nullable
	public Entity entity() {
		return entity;
	}

	/** Punto al que tiene que acercarse. */
	public Vec3d target() {
		return entity != null ? entity.getPos() : Vec3d.ofCenter(walkTo);
	}

	public Vec3d look() {
		return entity != null ? entity.getPos().add(0.0, entity.getHeight() * 0.5, 0.0) : lookAt;
	}

	public int duration() {
		return duration;
	}

	public double reach() {
		return reach;
	}

	public boolean remote() {
		return remote;
	}

	@Nullable
	public BlockPos breaking() {
		return breaking;
	}

	@Nullable
	public Effect effect() {
		return effect;
	}

	public boolean perform(WorkContext ctx) {
		return performer.perform(ctx);
	}
}
