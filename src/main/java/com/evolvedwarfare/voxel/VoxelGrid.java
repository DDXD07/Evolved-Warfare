package com.evolvedwarfare.voxel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Una rejilla de vóxeles pegada a una entidad (la carreta; luego, el tren): bloques de verdad, uno por celda, en las
 * coordenadas de la entidad (x a lo ancho, y hacia arriba, z a lo largo). Los bloques se guardan con su orientación en
 * esas coordenadas, así que giran con la entidad. Lo funcional (cofres, hornos, fogatas) lleva además su {@link VoxelMachine}.
 *
 * <p>La celda {@code (x, y, z)} ocupa {@code [x0 + x, x0 + x + 1]} etc.; su índice es {@code (y * sz + z) * sx + x}.</p>
 */
public final class VoxelGrid {
	public final int sx, sy, sz;
	public final double x0, y0, z0;
	private final BlockState[] states;
	private final VoxelMachine[] machines;
	private int version;
	private int cacheVersion = -1;
	private List<Box> collision = List.of(), outline = List.of();

	public VoxelGrid(int sx, int sy, int sz, double x0, double y0, double z0) {
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.x0 = x0;
		this.y0 = y0;
		this.z0 = z0;
		this.states = new BlockState[sx * sy * sz];
		this.machines = new VoxelMachine[states.length];
		Arrays.fill(states, Blocks.AIR.getDefaultState());
	}

	public int size() {
		return states.length;
	}

	public boolean inside(int x, int y, int z) {
		return x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz;
	}

	public int index(int x, int y, int z) {
		return (y * sz + z) * sx + x;
	}

	public int x(int i) {
		return i % sx;
	}

	public int z(int i) {
		return i / sx % sz;
	}

	public int y(int i) {
		return i / (sx * sz);
	}

	/** El índice de la celda vecina en esa dirección, o -1 si se sale. */
	public int neighbor(int i, Direction d) {
		int x = x(i) + d.getOffsetX(), y = y(i) + d.getOffsetY(), z = z(i) + d.getOffsetZ();
		return inside(x, y, z) ? index(x, y, z) : -1;
	}

	/** La celda que contiene ese punto (coordenadas de la entidad), o -1. */
	public int cellAt(Vec3d local) {
		int x = (int) Math.floor(local.x - x0), y = (int) Math.floor(local.y - y0), z = (int) Math.floor(local.z - z0);
		return inside(x, y, z) ? index(x, y, z) : -1;
	}

	public BlockState get(int i) {
		return i < 0 || i >= states.length ? Blocks.AIR.getDefaultState() : states[i];
	}

	public boolean isEmpty(int i) {
		return get(i).isAir();
	}

	public boolean isEmpty() {
		for (BlockState s : states) {
			if (!s.isAir()) {
				return false;
			}
		}
		return true;
	}

	public void set(int i, BlockState state) {
		states[i] = state;
		version++;
	}

	@Nullable
	public VoxelMachine machine(int i) {
		return i < 0 || i >= machines.length ? null : machines[i];
	}

	public void setMachine(int i, @Nullable VoxelMachine m) {
		machines[i] = m;
	}

	/** Cambia cada vez que cambia un bloque (para no recalcular las cajas). */
	public int version() {
		return version;
	}

	/** Esquina de abajo de una celda (coordenadas de la entidad). */
	public Vec3d corner(int i) {
		return new Vec3d(x0 + x(i), y0 + y(i), z0 + z(i));
	}

	public Vec3d center(int i) {
		return corner(i).add(0.5, 0.5, 0.5);
	}

	public Box cell(int i) {
		Vec3d c = corner(i);
		return new Box(c.x, c.y, c.z, c.x + 1, c.y + 1, c.z + 1);
	}

	/** Las capas ocupadas desde abajo (0 si está vacía). */
	public int height() {
		for (int i = states.length - 1; i >= 0; i--) {
			if (!states[i].isAir()) {
				return y(i) + 1;
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------ cajas

	private void refresh() {
		if (cacheVersion == version) {
			return;
		}
		List<Box> c = new ArrayList<>(), o = new ArrayList<>();
		for (int i = 0; i < states.length; i++) {
			BlockState s = states[i];
			if (s.isAir()) {
				continue;
			}
			Vec3d at = corner(i);
			for (Box b : s.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes()) {
				c.add(b.offset(at));
			}
			for (Box b : s.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes()) {
				o.add(b.offset(at));
			}
		}
		collision = c;
		outline = o;
		cacheVersion = version;
	}

	/** Lo macizo (para chocar y andar encima), en coordenadas de la entidad. */
	public List<Box> collisionBoxes() {
		refresh();
		return collision;
	}

	/** Lo que se puede apuntar, en coordenadas de la entidad. */
	public List<Box> outlineBoxes() {
		refresh();
		return outline;
	}

	/** Dónde da un rayo: en qué celda (o -1 si es en la base), por qué cara y en qué punto (coordenadas de la entidad). */
	public record Hit(int index, Direction side, Vec3d pos) {
	}

	/**
	 * El primer bloque (o trozo de la base) que corta el rayo de {@code a} a {@code b} (coordenadas de la entidad), o null.
	 * La base son cajas macizas que no son de la rejilla (la tabla de la carreta): sobre su cara de arriba se puede construir.
	 */
	@Nullable
	public Hit raycast(Vec3d a, Vec3d b, List<Box> base) {
		Hit best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < states.length; i++) {
			BlockState s = states[i];
			if (s.isAir()) {
				continue;
			}
			Vec3d at = corner(i);
			VoxelShape shape = s.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
			if (shape.isEmpty()) {
				continue;
			}
			BlockHitResult r = shape.raycast(a.subtract(at), b.subtract(at), BlockPos.ORIGIN);
			if (r != null) {
				Vec3d p = r.getPos().add(at);
				double d = p.squaredDistanceTo(a);
				if (d < bestD) {
					bestD = d;
					best = new Hit(i, r.getSide(), p);
				}
			}
		}
		for (Box box : base) {
			BlockHitResult r = Box.raycast(List.of(box), a, b, BlockPos.ORIGIN);
			if (r != null) {
				double d = r.getPos().squaredDistanceTo(a);
				if (d < bestD) {
					bestD = d;
					best = new Hit(-1, r.getSide(), r.getPos());
				}
			}
		}
		return best;
	}

	/** La celda donde iría un bloque puesto apuntando ahí (al lado de lo apuntado, o encima de la base), o -1. */
	public int target(Hit hit) {
		if (hit.index() >= 0) {
			return neighbor(hit.index(), hit.side());
		}
		if (hit.side() != Direction.UP || Math.abs(hit.pos().y - y0) > 0.01) {
			return -1;
		}
		return cellAt(hit.pos().add(0, 0.5, 0));
	}

	// ------------------------------------------------------------------ guardar

	/** Los bloques (y, si {@code full}, lo que tienen dentro; al cliente le basta con lo que se ve). */
	public NbtCompound write(RegistryWrapper.WrapperLookup registries, boolean full) {
		NbtCompound n = new NbtCompound();
		NbtList palette = new NbtList();
		List<BlockState> seen = new ArrayList<>();
		int[] cells = new int[states.length];
		for (int i = 0; i < states.length; i++) {
			BlockState s = states[i];
			if (s.isAir()) {
				continue;
			}
			int p = seen.indexOf(s);
			if (p < 0) {
				p = seen.size();
				seen.add(s);
				palette.add(NbtHelper.fromBlockState(s));
			}
			cells[i] = p + 1;
		}
		n.put("Palette", palette);
		n.putIntArray("Cells", cells);
		NbtList ms = new NbtList();
		for (int i = 0; i < machines.length; i++) {
			if (machines[i] != null) {
				NbtCompound m = machines[i].write(registries, full);
				m.putInt("I", i);
				ms.add(m);
			}
		}
		n.put("Machines", ms);
		return n;
	}

	public void read(NbtCompound n, RegistryWrapper.WrapperLookup registries) {
		Arrays.fill(states, Blocks.AIR.getDefaultState());
		Arrays.fill(machines, null);
		NbtList palette = n.getList("Palette", NbtElement.COMPOUND_TYPE);
		List<BlockState> seen = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			seen.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), palette.getCompound(i)));
		}
		int[] cells = n.getIntArray("Cells");
		for (int i = 0; i < Math.min(cells.length, states.length); i++) {
			int p = cells[i] - 1;
			if (p >= 0 && p < seen.size()) {
				states[i] = seen.get(p);
			}
		}
		NbtList ms = n.getList("Machines", NbtElement.COMPOUND_TYPE);
		for (int k = 0; k < ms.size(); k++) {
			NbtCompound m = ms.getCompound(k);
			int i = m.getInt("I");
			VoxelMachine machine = VoxelMachine.read(m, registries);
			if (machine != null && i >= 0 && i < machines.length && VoxelMachine.Kind.of(states[i].getBlock()) == machine.kind) {
				machines[i] = machine;
			}
		}
		version++;
	}
}
