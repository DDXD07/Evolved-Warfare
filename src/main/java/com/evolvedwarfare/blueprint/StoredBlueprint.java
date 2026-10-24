package com.evolvedwarfare.blueprint;

import com.evolvedwarfare.building.Blueprint;
import com.evolvedwarfare.building.BlueprintStates;
import com.evolvedwarfare.building.BuildMaterials;
import com.evolvedwarfare.building.Material;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/**
 * Un plano guardado (escaneado por un jugador o incluido en el mod): una caja de bloques con el frente mirando al norte
 * (-z). Cada posición guarda un índice a la paleta de estados (como texto); "aire" se limpia al construir y
 * "structure_void" no se toca.
 *
 * <p>Al construirlo, el plano queda detrás del puesto del constructor (a 2 bloques), centrado y con la capa de abajo a
 * la altura del suelo.</p>
 */
public final class StoredBlueprint {
	public static final String AIR = "minecraft:air";
	public static final String VOID = "minecraft:structure_void";
	/** Bloques entre el puesto y el frente del edificio. */
	public static final int FRONT_GAP = 2;
	public static final int MAX_X = 48;
	public static final int MAX_Y = 40;
	public static final int MAX_Z = 48;

	public final String id;
	public final String name;
	public final String author;
	public final boolean builtin;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	private final List<String> palette;
	private final int[] blocks;

	public StoredBlueprint(String id, String name, String author, boolean builtin, int sizeX, int sizeY, int sizeZ,
			List<String> palette, int[] blocks) {
		this.id = id;
		this.name = name;
		this.author = author;
		this.builtin = builtin;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = List.copyOf(palette);
		this.blocks = blocks;
	}

	public int index(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}

	public String stateAt(int x, int y, int z) {
		int i = blocks[index(x, y, z)];
		return i >= 0 && i < palette.size() ? palette.get(i) : VOID;
	}

	public List<String> palette() {
		return palette;
	}

	public int[] blocks() {
		return blocks;
	}

	public static boolean isSolid(String state) {
		return !state.equals(AIR) && !state.equals(VOID);
	}

	/** Bloques que hay que poner. */
	public int blockCount() {
		boolean[] solid = new boolean[palette.size()];
		for (int i = 0; i < solid.length; i++) {
			solid[i] = isSolid(palette.get(i));
		}
		int count = 0;
		for (int b : blocks) {
			if (b >= 0 && b < solid.length && solid[b]) {
				count++;
			}
		}
		return count;
	}

	/** Qué objetos hacen falta para construirlo (de más a menos). */
	public Map<Item, Integer> materials() {
		int[] uses = new int[palette.size()];
		for (int b : blocks) {
			if (b >= 0 && b < uses.length) {
				uses[b]++;
			}
		}
		Map<Item, Integer> totals = new LinkedHashMap<>();
		for (int i = 0; i < uses.length; i++) {
			String text = palette.get(i);
			if (uses[i] == 0 || !isSolid(text)) {
				continue;
			}
			BlockState state = BlueprintStates.parse(text);
			if (BlueprintFlags.isOtherHalf(state)) {
				continue;
			}
			Item item = BuildMaterials.itemFor(state);
			if (item != Items.AIR) {
				totals.merge(item, uses[i] * BuildMaterials.costFor(state), Integer::sum);
			}
		}
		List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(totals.entrySet());
		sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
		Map<Item, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : sorted) {
			result.put(e.getKey(), e.getValue());
		}
		return result;
	}

	/** El plano en coordenadas del puesto (mirando al norte), listo para {@code BuildPlan}. */
	public Blueprint toBlueprint() {
		Blueprint blueprint = new Blueprint();
		int x0 = -(sizeX / 2);
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					String text = stateAt(x, y, z);
					int lx = x + x0;
					int ly = y - 1;
					int lz = z + FRONT_GAP;
					if (text.equals(VOID)) {
						continue;
					}
					if (text.equals(AIR)) {
						blueprint.air(lx, ly, lz);
						continue;
					}
					BlockState state = BlueprintStates.parse(text);
					int flags = BlueprintFlags.flagsFor(state);
					if (y == 0) {
						flags |= Blueprint.FOUNDATION;
					}
					blueprint.set(lx, ly, lz, text, BlueprintFlags.isOtherHalf(state) ? Material.FREE : Material.EXACT, flags);
				}
			}
		}
		return blueprint;
	}

	// ------------------------------------------------------------------ guardado

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Name", name);
		nbt.putString("Author", author);
		nbt.putInt("SizeX", sizeX);
		nbt.putInt("SizeY", sizeY);
		nbt.putInt("SizeZ", sizeZ);
		NbtList list = new NbtList();
		for (String state : palette) {
			list.add(NbtString.of(state));
		}
		nbt.put("Palette", list);
		nbt.putIntArray("Blocks", blocks);
		return nbt;
	}

	public static StoredBlueprint fromNbt(String id, NbtCompound nbt, boolean builtin) {
		NbtList list = nbt.getList("Palette", NbtElement.STRING_TYPE);
		List<String> palette = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			palette.add(list.getString(i));
		}
		int sx = Math.max(1, nbt.getInt("SizeX"));
		int sy = Math.max(1, nbt.getInt("SizeY"));
		int sz = Math.max(1, nbt.getInt("SizeZ"));
		int[] blocks = nbt.getIntArray("Blocks");
		if (blocks.length != sx * sy * sz) {
			int[] fixed = new int[sx * sy * sz];
			java.util.Arrays.fill(fixed, -1);
			System.arraycopy(blocks, 0, fixed, 0, Math.min(blocks.length, fixed.length));
			blocks = fixed;
		}
		return new StoredBlueprint(id, nbt.getString("Name"), nbt.getString("Author"), builtin, sx, sy, sz, palette, blocks);
	}
}
