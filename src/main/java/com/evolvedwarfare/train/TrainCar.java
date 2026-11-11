package com.evolvedwarfare.train;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Un coche del tren (la locomotora o un vagón), del tamaño de una vagoneta y sobre las vías de siempre: va enganchado al
 * de delante y lo sigue a su distancia, como un tren de verdad (sin empujarse entre ellos).
 */
public interface TrainCar {
	/** La distancia entre los centros de dos coches enganchados (bloques). */
	double SPACING = 2.05;
	/** Más lejos que esto, el enganche se suelta. */
	double SNAP = 7.0;

	Link link();

	default AbstractMinecartEntity cart() {
		return (AbstractMinecartEntity) this;
	}

	/** Los enganches: el de delante (al que sigue) y el de detrás (el que le sigue). */
	final class Link {
		@Nullable
		public UUID front, back;
		int missing;
		/** El tic del mundo en que se movió por última vez. */
		public long movedTick = -1;

		public void write(NbtCompound nbt) {
			if (front != null) {
				nbt.putUuid("TrainFront", front);
			}
			if (back != null) {
				nbt.putUuid("TrainBack", back);
			}
		}

		public void read(NbtCompound nbt) {
			front = nbt.containsUuid("TrainFront") ? nbt.getUuid("TrainFront") : null;
			back = nbt.containsUuid("TrainBack") ? nbt.getUuid("TrainBack") : null;
		}
	}

	@Nullable
	static TrainCar find(ServerWorld world, @Nullable UUID id) {
		return id != null && world.getEntity(id) instanceof TrainCar car && !car.cart().isRemoved() ? car : null;
	}

	/** Antes de moverse: si va enganchado, se pone a la velocidad del de delante, más lo que haga falta para quedar a su distancia. */
	static void follow(TrainCar self) {
		AbstractMinecartEntity me = self.cart();
		if (!(me.getWorld() instanceof ServerWorld world) || self.link().front == null) {
			return;
		}
		TrainCar front = find(world, self.link().front);
		if (front == null) {
			// puede que aún no esté cargado: se espera un poco antes de soltarse
			if (++self.link().missing > 200) {
				self.link().front = null;
			}
			return;
		}
		self.link().missing = 0;
		AbstractMinecartEntity f = front.cart();
		Vec3d d = f.getPos().subtract(me.getPos());
		double dist = d.length();
		if (dist > SNAP) {
			uncouple(world, self);
			return;
		}
		if (dist < 1e-4) {
			return;
		}
		// lo que tiene que andar este tic para quedar justo a su distancia del de delante (contando con lo que ande el de
		// delante si aún no se ha movido en este tic)
		Vec3d dir = d.multiply(1.0 / dist);
		boolean frontMoved = front.link().movedTick == world.getTime();
		double go = dist - SPACING + (frontMoved ? 0.0 : f.getVelocity().dotProduct(dir));
		// nunca más de lo que hay hasta el de delante (para no adelantarlo si él no llega a moverse)
		go = MathHelperClamp(go, -0.6, Math.min(0.8, dist - 0.6));
		me.setVelocity(dir.multiply(go).add(0, me.getVelocity().y, 0));
	}

	private static double MathHelperClamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Después de moverse. */
	static void moved(TrainCar self) {
		self.link().movedTick = self.cart().getWorld().getTime();
	}

	/** ¿Son del mismo tren? (para que no se empujen entre ellos) */
	static boolean sameTrain(TrainCar a, Entity other) {
		if (!(other instanceof TrainCar b)) {
			return false;
		}
		UUID ida = a.cart().getUuid(), idb = other.getUuid();
		return idb.equals(a.link().front) || idb.equals(a.link().back) || ida.equals(b.link().front) || ida.equals(b.link().back);
	}

	/** Engancha "back" detrás de "front". */
	static void couple(TrainCar front, TrainCar back) {
		front.link().back = back.cart().getUuid();
		back.link().front = front.cart().getUuid();
	}

	/** Lo suelta del de delante. */
	static void uncouple(ServerWorld world, TrainCar self) {
		TrainCar front = find(world, self.link().front);
		if (front != null && self.cart().getUuid().equals(front.link().back)) {
			front.link().back = null;
		}
		self.link().front = null;
	}

	/** Al quitarlo: los de delante y detrás se olvidan de él. */
	static void removed(TrainCar self) {
		if (!(self.cart().getWorld() instanceof ServerWorld world)) {
			return;
		}
		TrainCar front = find(world, self.link().front), back = find(world, self.link().back);
		if (front != null && self.cart().getUuid().equals(front.link().back)) {
			front.link().back = null;
		}
		if (back != null && self.cart().getUuid().equals(back.link().front)) {
			back.link().front = null;
		}
	}
}
