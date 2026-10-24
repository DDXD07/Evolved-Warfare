package com.evolvedwarfare.blueprint;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.building.BlueprintStates;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Biblioteca de planos. Los que escanean los jugadores se guardan en {@code config/evolvedwarfare/blueprints/} (así sirven
 * en todos los mundos); además, los edificios de las estructuras del mod vienen de serie ("builtin/...").
 */
public final class BlueprintLibrary {
	public static final String BUILTIN_PREFIX = "builtin/";
	/** Edificios del mod que se ofrecen como planos (están hechos con la fachada al oeste). */
	private static final String[] BUILTINS = {
			"city/houses/cottage", "city/houses/townhouse", "city/houses/barracks", "city/houses/tavern",
			"city/houses/blacksmith", "city/houses/guard_post", "city/houses/market_stalls", "city/houses/garden",
			"watchtower/tower", "desert_city/houses/adobe_house", "desert_city/houses/courtyard_house",
			"desert_city/houses/bazaar", "desert_city/houses/caravanserai", "desert_city/houses/barracks",
			"desert_city/houses/watchtower", "snow_city/houses/log_cabin", "snow_city/houses/hall_house",
			"snow_city/houses/tavern", "snow_city/houses/smithy", "snow_city/houses/barracks", "snow_city/houses/tower",
			"viking/hut", "viking/smithy", "viking/longhouse", "viking/longship", "viking/watch", "pirate/tavern",
			"pirate/lookout"};

	private static final Map<String, StoredBlueprint> CACHE = new HashMap<>();
	private static boolean scanned;

	private BlueprintLibrary() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("evolvedwarfare").resolve("blueprints");
	}

	/** Resumen de un plano (para listas). */
	public record Summary(String id, String name, String author, boolean builtin, int sizeX, int sizeY, int sizeZ, int blocks) {
	}

	public static Summary summary(StoredBlueprint b) {
		return new Summary(b.id, b.name, b.author, b.builtin, b.sizeX, b.sizeY, b.sizeZ, b.blockCount());
	}

	/** Todos los planos: primero los de los jugadores (por nombre) y luego los del mod. */
	public static synchronized List<Summary> list(MinecraftServer server) {
		loadAll(server);
		List<StoredBlueprint> all = new ArrayList<>(CACHE.values());
		all.sort(Comparator.comparing((StoredBlueprint b) -> b.builtin).thenComparing(b -> b.name.toLowerCase(Locale.ROOT)));
		List<Summary> out = new ArrayList<>();
		for (StoredBlueprint b : all) {
			out.add(summary(b));
		}
		return out;
	}

	@Nullable
	public static synchronized StoredBlueprint get(MinecraftServer server, String id) {
		StoredBlueprint cached = CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		StoredBlueprint loaded = id.startsWith(BUILTIN_PREFIX) ? loadBuiltin(server, id.substring(BUILTIN_PREFIX.length()))
				: loadFile(id);
		if (loaded != null) {
			CACHE.put(id, loaded);
		}
		return loaded;
	}

	/** Un nombre de archivo libre a partir del nombre que le pone el jugador. */
	public static synchronized String newId(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (base.isEmpty()) {
			base = "plano";
		}
		if (base.length() > 40) {
			base = base.substring(0, 40);
		}
		String id = base;
		for (int n = 2; CACHE.containsKey(id) || Files.exists(dir().resolve(id + ".nbt")); n++) {
			id = base + "_" + n;
		}
		return id;
	}

	public static synchronized boolean save(StoredBlueprint blueprint) {
		try {
			Files.createDirectories(dir());
			NbtIo.writeCompressed(blueprint.toNbt(), dir().resolve(blueprint.id + ".nbt"));
			CACHE.put(blueprint.id, blueprint);
			return true;
		} catch (IOException e) {
			EvolvedWarfare.LOGGER.error("No se pudo guardar el plano {}", blueprint.id, e);
			return false;
		}
	}

	public static synchronized boolean delete(String id) {
		if (id.startsWith(BUILTIN_PREFIX)) {
			return false;
		}
		CACHE.remove(id);
		try {
			return Files.deleteIfExists(dir().resolve(id + ".nbt"));
		} catch (IOException e) {
			EvolvedWarfare.LOGGER.error("No se pudo borrar el plano {}", id, e);
			return false;
		}
	}

	// ------------------------------------------------------------------ carga

	private static void loadAll(MinecraftServer server) {
		if (!scanned) {
			scanned = true;
			for (String path : BUILTINS) {
				get(server, BUILTIN_PREFIX + path);
			}
		}
		if (Files.isDirectory(dir())) {
			try (Stream<Path> files = Files.list(dir())) {
				for (Path file : (Iterable<Path>) files::iterator) {
					String fileName = file.getFileName().toString();
					if (fileName.endsWith(".nbt")) {
						String id = fileName.substring(0, fileName.length() - 4);
						if (!CACHE.containsKey(id)) {
							StoredBlueprint loaded = loadFile(id);
							if (loaded != null) {
								CACHE.put(id, loaded);
							}
						}
					}
				}
			} catch (IOException e) {
				EvolvedWarfare.LOGGER.error("No se pudo leer la carpeta de planos", e);
			}
		}
	}

	@Nullable
	private static StoredBlueprint loadFile(String id) {
		if (!id.matches("[a-z0-9_]+")) {
			return null;
		}
		Path file = dir().resolve(id + ".nbt");
		if (!Files.exists(file)) {
			return null;
		}
		try {
			return StoredBlueprint.fromNbt(id, NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes()), false);
		} catch (IOException | RuntimeException e) {
			EvolvedWarfare.LOGGER.error("Plano dañado: {}", file, e);
			return null;
		}
	}

	/**
	 * Un edificio del mod (plantilla de estructura .nbt). Los bloques jigsaw pasan a ser lo que dejan al generarse; las
	 * posiciones vacías no se tocan. Se gira para que la fachada (al oeste) mire al norte.
	 */
	@Nullable
	private static StoredBlueprint loadBuiltin(MinecraftServer server, String path) {
		Identifier file = EvolvedWarfare.id("structure/" + path + ".nbt");
		Optional<Resource> resource = server.getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return null;
		}
		NbtCompound nbt;
		try (InputStream in = resource.get().getInputStream()) {
			nbt = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		} catch (IOException e) {
			EvolvedWarfare.LOGGER.error("No se pudo leer {}", file, e);
			return null;
		}
		NbtList size = nbt.getList("size", NbtElement.INT_TYPE);
		int sx = size.getInt(0);
		int sy = size.getInt(1);
		int sz = size.getInt(2);
		NbtList paletteNbt = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
		List<String> source = new ArrayList<>();
		for (int i = 0; i < paletteNbt.size(); i++) {
			NbtCompound entry = paletteNbt.getCompound(i);
			StringBuilder sb = new StringBuilder(entry.getString("Name"));
			NbtCompound props = entry.getCompound("Properties");
			if (!props.isEmpty()) {
				sb.append('[');
				boolean first = true;
				for (String key : props.getKeys()) {
					if (!first) {
						sb.append(',');
					}
					first = false;
					sb.append(key).append('=').append(props.getString(key));
				}
				sb.append(']');
			}
			source.add(sb.toString());
		}
		// la fachada está al oeste: girar 90º en el sentido de las agujas para que mire al norte
		BlockRotation rotation = BlockRotation.CLOCKWISE_90;
		Map<Long, String> placed = new LinkedHashMap<>();
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		NbtList blocks = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
		List<Object[]> rotated = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			NbtCompound block = blocks.getCompound(i);
			NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
			int stateIndex = block.getInt("state");
			if (stateIndex < 0 || stateIndex >= source.size()) {
				continue;
			}
			String text = source.get(stateIndex);
			if (text.startsWith("minecraft:jigsaw")) {
				text = block.getCompound("nbt").getString("final_state");
				if (text.isEmpty()) {
					text = StoredBlueprint.VOID;
				}
			}
			if (text.equals(StoredBlueprint.VOID) || text.startsWith("minecraft:structure_void")) {
				continue;
			}
			BlockPos local = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)).rotate(rotation);
			String state = rotateState(text, rotation);
			rotated.add(new Object[] {local, state});
			minX = Math.min(minX, local.getX());
			minZ = Math.min(minZ, local.getZ());
			maxX = Math.max(maxX, local.getX());
			maxZ = Math.max(maxZ, local.getZ());
		}
		if (rotated.isEmpty()) {
			return null;
		}
		int nx = maxX - minX + 1;
		int nz = maxZ - minZ + 1;
		List<String> palette = new ArrayList<>();
		Map<String, Integer> indexOf = new HashMap<>();
		int[] data = new int[nx * sy * nz];
		java.util.Arrays.fill(data, -1);
		for (Object[] entry : rotated) {
			BlockPos p = (BlockPos) entry[0];
			String state = (String) entry[1];
			int idx = indexOf.computeIfAbsent(state, s -> {
				palette.add(s);
				return palette.size() - 1;
			});
			int x = p.getX() - minX;
			int z = p.getZ() - minZ;
			int y = p.getY();
			if (y >= 0 && y < sy) {
				data[(y * nz + z) * nx + x] = idx;
			}
		}
		// las posiciones vacías: "no tocar"
		int voidIndex = palette.size();
		palette.add(StoredBlueprint.VOID);
		for (int i = 0; i < data.length; i++) {
			if (data[i] < 0) {
				data[i] = voidIndex;
			}
		}
		String name = "blueprint.evolvedwarfare." + path.replace('/', '.');
		return new StoredBlueprint(BUILTIN_PREFIX + path, name, "Evolved Warfare", true, nx, sy, nz, palette, data);
	}

	private static String rotateState(String text, BlockRotation rotation) {
		try {
			BlockState state = BlueprintStates.parse(text).rotate(rotation);
			return BlockArgumentParser.stringifyBlockState(state);
		} catch (RuntimeException e) {
			return text;
		}
	}
}
