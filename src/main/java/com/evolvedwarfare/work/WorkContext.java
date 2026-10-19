package com.evolvedwarfare.work;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.entity.WorkerEntity;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Todo lo que necesita un oficio para planear y hacer una tarea: el mundo, el trabajador y su puesto. */
public final class WorkContext {
	public final ServerWorld world;
	public final WorkerEntity worker;
	public final WorkSiteBlockEntity site;
	public final BlockPos sitePos;
	public final Direction facing;
	public final Profession profession;
	private WorkStatus status = WorkStatus.WAITING;

	public WorkContext(ServerWorld world, WorkerEntity worker, WorkSiteBlockEntity site, Profession profession) {
		this.world = world;
		this.worker = worker;
		this.site = site;
		this.sitePos = site.getPos();
		this.facing = site.getFacing();
		this.profession = profession;
	}

	// ------------------------------------------------------------------ estado

	/** Deja el motivo por el que no hay tarea y devuelve null (para usarlo como {@code return ctx.idle(...)}). */
	@Nullable
	public WorkAction idle(WorkStatus status) {
		this.status = status;
		return null;
	}

	public WorkStatus status() {
		return status;
	}

	// ------------------------------------------------------------------ herramienta y tiempos

	public ItemStack tool() {
		return worker.getMainHandStack();
	}

	public boolean hasTool() {
		return profession.isTool(tool());
	}

	/** Duración de una tarea según la herramienta (mínimo 4 ticks). */
	public int ticks(int base) {
		return Math.max(4, Math.round(base / profession.speed(tool())));
	}

	public void damageTool(int amount) {
		worker.damageTool(amount);
	}

	public Random random() {
		return worker.getRandom();
	}

	// ------------------------------------------------------------------ zona

	public BlockBox area() {
		return profession.area().box(sitePos, facing);
	}

	/** La zona como caja para buscar entidades, ampliada {@code grow} bloques. */
	public Box areaBox(double grow) {
		BlockBox b = area();
		return new Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX() + 1, b.getMaxY() + 1, b.getMaxZ() + 1).expand(grow);
	}

	public double distanceSq(BlockPos pos) {
		return worker.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	/** Sitios y entidades a los que no consiguió llegar hace poco (los evita un rato). */
	public boolean ignored(BlockPos pos) {
		return worker.isIgnored(pos);
	}

	public boolean ignored(Entity entity) {
		return worker.isIgnored(entity);
	}

	// ------------------------------------------------------------------ almacén

	public boolean outputFull() {
		return site.isOutputFull();
	}

	/** Guarda en la producción; lo que no quepa cae al suelo junto al puesto. */
	public void store(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack rest = site.addOutput(stack);
		if (!rest.isEmpty()) {
			Block.dropStack(world, sitePos.up(), rest);
		}
	}

	public void storeAll(List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			store(stack);
		}
	}

	// ------------------------------------------------------------------ bloques

	/** Lo que soltaría ese bloque al romperlo con la herramienta del trabajador (respeta Fortuna y Toque de seda). */
	public List<ItemStack> drops(BlockPos pos, BlockState state) {
		return Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos), worker, tool());
	}

	/** Rompe el bloque y guarda lo que suelta. */
	public void harvest(BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		List<ItemStack> drops = drops(pos, state);
		world.breakBlock(pos, false, worker);
		storeAll(drops);
	}

	public void sound(BlockPos pos, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, pos, sound, SoundCategory.NEUTRAL, volume, pitch);
	}
}
