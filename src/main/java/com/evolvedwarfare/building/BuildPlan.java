package com.evolvedwarfare.building;

import com.evolvedwarfare.work.Profession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un plano colocado en el mundo: cada pieza con su posición real (girada según hacia dónde mira el puesto), su estado
 * de bloque y el sitio desde donde la pone el trabajador. Las piezas van en el orden de obra.
 */
public final class BuildPlan {
	/** Una pieza en el mundo. */
	public static final class Entry {
		public final Blueprint.Piece piece;
		/** Posición (en las piezas que siguen el terreno, la y es provisional). */
		public final BlockPos pos;
		@Nullable
		public final BlockState state;
		/** Desde dónde la pone (relativo a la pieza, ya girado). */
		private final BlockPos standOffset;

		Entry(Blueprint.Piece piece, BlockPos pos, @Nullable BlockState state, BlockPos standOffset) {
			this.piece = piece;
			this.pos = pos;
			this.state = state;
			this.standOffset = standOffset;
		}

		public boolean followsGround() {
			return piece.has(Blueprint.GROUND);
		}

		public BlockPos standFor(BlockPos actual) {
			return actual.add(standOffset);
		}
	}

	private final BlockPos site;
	private final List<Entry> clearOrder = new ArrayList<>();
	private final List<Entry> foundations = new ArrayList<>();
	private final List<Entry> placeOrder = new ArrayList<>();

	private BuildPlan(BlockPos site) {
		this.site = site.toImmutable();
	}

	public static BuildPlan create(Profession profession, int seed, BlockPos site, Direction facing) {
		Blueprint blueprint = BlueprintGenerator.generate(profession.id(), seed);
		BlockRotation rotation = BlueprintStates.rotationFor(facing);
		BuildPlan plan = new BuildPlan(site);

		// centro del edificio (sin contar el cercado) para saber hacia dónde ponerse
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Blueprint.Piece piece : blueprint.pieces()) {
			if (!piece.has(Blueprint.GROUND)) {
				minZ = Math.min(minZ, piece.z());
				maxZ = Math.max(maxZ, piece.z());
			}
		}
		int centerZ = minZ <= maxZ ? (minZ + maxZ) / 2 : 0;

		List<Entry> late = new ArrayList<>();
		for (Blueprint.Piece piece : blueprint.pieces()) {
			BlockPos local = new BlockPos(piece.x(), piece.y(), piece.z());
			BlockPos pos = site.add(local.rotate(rotation));
			BlockState state = piece.isAir() ? null : BlueprintStates.parse(piece.state()).rotate(rotation);
			// arriba (tejado): desde el suelo de debajo; abajo: un paso hacia el centro del edificio
			BlockPos standLocal;
			if (piece.y() >= 2 && !piece.has(Blueprint.GROUND)) {
				standLocal = new BlockPos(0, -piece.y(), 0);
			} else {
				int dx = -Integer.signum(piece.x());
				int dz = -Integer.signum(piece.z() - centerZ);
				if (dx == 0 && dz == 0) {
					dz = -1;
				}
				standLocal = new BlockPos(dx, 0, dz);
			}
			Entry entry = new Entry(piece, pos, state, standLocal.rotate(rotation));
			if (piece.isAir()) {
				plan.clearOrder.add(entry);
				continue;
			}
			if (piece.has(Blueprint.FOUNDATION)) {
				plan.foundations.add(entry);
			}
			(piece.has(Blueprint.LATE) ? late : plan.placeOrder).add(entry);
		}
		plan.clearOrder.sort(Comparator.comparingInt((Entry e) -> e.pos.getY()).reversed());
		plan.placeOrder.sort(Comparator.comparingInt((Entry e) -> e.piece.y()));
		plan.placeOrder.addAll(late);
		return plan;
	}

	public List<Entry> clearOrder() {
		return Collections.unmodifiableList(clearOrder);
	}

	public List<Entry> foundations() {
		return Collections.unmodifiableList(foundations);
	}

	public List<Entry> placeOrder() {
		return Collections.unmodifiableList(placeOrder);
	}

	public int total() {
		return placeOrder.size();
	}

	/**
	 * Posición real de una pieza. Las que siguen el terreno (vallas de un cercado) van encima del suelo de su columna;
	 * si ahí hay agua o no se encuentra suelo, null.
	 */
	@Nullable
	public BlockPos resolve(World world, Entry entry) {
		if (!entry.followsGround()) {
			return entry.pos;
		}
		BlockPos.Mutable cursor = new BlockPos.Mutable(entry.pos.getX(), site.getY() + 4, entry.pos.getZ());
		for (int i = 0; i < 14; i++) {
			BlockState state = world.getBlockState(cursor);
			if (entry.state != null && state.isOf(entry.state.getBlock())) {
				return cursor.toImmutable();
			}
			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			boolean passable = state.isAir() || state.isReplaceable() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS);
			if (!passable) {
				return new BlockPos(cursor.getX(), cursor.getY() + 1 + entry.piece.y(), cursor.getZ());
			}
			cursor.move(Direction.DOWN);
		}
		return null;
	}
}
