package com.evolvedwarfare.world.city;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.CityGuardEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.entity.TraderEntity;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModStructures;
import com.evolvedwarfare.trade.TraderType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * Coloca una ciudad amurallada: la vuelve a sacar de su semilla ({@link CityDesigner}) y pone lo que cae en el chunk que
 * se está generando. Dentro de la muralla allana el terreno (rellena por debajo y quita lo que sobra por encima: árboles,
 * lomas), y hace aparecer a los mercaderes (en su mostrador, con su cama), los guardias y los vecinos.
 */
public class CityPiece extends StructurePiece {
	/** Hasta dónde bajan los cimientos. */
	private static final int MAX_FOUNDATION = 24;
	/** Hasta dónde se limpia por encima del suelo dentro de la muralla. */
	private static final int CLEAR_HEIGHT = 40;
	private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
	/** Las últimas ciudades diseñadas (cada una se usa en muchos chunks seguidos). */
	private static final Map<Long, Layout> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Layout> eldest) {
			return size() > 6;
		}
	};

	/** El plano y sus bloques repartidos por chunks. */
	private record Layout(CityPlan plan, Map<Long, List<CityPlan.Block>> byChunk) {
	}

	private final BlockPos origin;
	private final long seed;
	private final CityDesigner.Style style;

	public CityPiece(BlockPos origin, long seed, CityDesigner.Style style) {
		super(ModStructures.CITY_PIECE, 0, box(layout(seed, style).plan(), origin));
		this.origin = origin;
		this.seed = seed;
		this.style = style;
	}

	public CityPiece(StructureContext context, NbtCompound nbt) {
		super(ModStructures.CITY_PIECE, nbt);
		this.origin = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
		this.seed = nbt.getLong("Seed");
		CityDesigner.Style read;
		try {
			read = CityDesigner.Style.valueOf(nbt.getString("Style"));
		} catch (IllegalArgumentException e) {
			read = CityDesigner.Style.MEDIEVAL;
		}
		this.style = read;
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putInt("OX", origin.getX());
		nbt.putInt("OY", origin.getY());
		nbt.putInt("OZ", origin.getZ());
		nbt.putLong("Seed", seed);
		nbt.putString("Style", style.name());
	}

	public BlockPos origin() {
		return origin;
	}

	public long seed() {
		return seed;
	}

	public CityDesigner.Style style() {
		return style;
	}

	/** El nombre de la ciudad. */
	public String cityName() {
		return CityNames.of(seed, style);
	}

	/** La clave de la ciudad (el chunk de su plaza): la llevan sus mercaderes y guardias. */
	public long cityKey() {
		return new ChunkPos(origin).toLong();
	}

	public int radius() {
		CityPlan plan = layout(seed, style).plan();
		return Math.max(plan.halfX, plan.halfZ);
	}

	/** La ciudad de una estructura, si es una de estas. */
	public static Optional<CityPiece> of(@Nullable StructureStart start) {
		if (start == null || !start.hasChildren()) {
			return Optional.empty();
		}
		for (StructurePiece piece : start.getChildren()) {
			if (piece instanceof CityPiece city) {
				return Optional.of(city);
			}
		}
		return Optional.empty();
	}

	private static Layout layout(long seed, CityDesigner.Style style) {
		long key = seed * 31L + style.ordinal();
		synchronized (CACHE) {
			Layout cached = CACHE.get(key);
			if (cached != null) {
				return cached;
			}
		}
		CityPlan plan = CityDesigner.design(seed, style);
		Map<Long, List<CityPlan.Block>> byChunk = new HashMap<>();
		for (CityPlan.Block b : plan.blocks()) {
			byChunk.computeIfAbsent(ChunkPos.toLong(b.x() >> 4, b.z() >> 4), k -> new ArrayList<>()).add(b);
		}
		Layout layout = new Layout(plan, byChunk);
		synchronized (CACHE) {
			CACHE.put(key, layout);
		}
		return layout;
	}

	/** La caja empieza un bloque por encima del suelo: al adaptar el terreno ("beard_thin") queda llano a esa altura. */
	private static BlockBox box(CityPlan plan, BlockPos origin) {
		int[] b = plan.bounds();
		return new BlockBox(origin.getX() + b[0], origin.getY() + 1, origin.getZ() + b[2], origin.getX() + b[3],
				origin.getY() + Math.max(2, b[4]), origin.getZ() + b[5]);
	}

	/** El bloque de un texto como "minecraft:oak_stairs[facing=north]" (aire si no existe). */
	public static BlockState state(String text) {
		return STATES.computeIfAbsent(text, t -> {
			try {
				return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), t, false).blockState();
			} catch (CommandSyntaxException e) {
				EvolvedWarfare.LOGGER.error("Bloque desconocido en una ciudad: {}", t);
				return Blocks.AIR.getDefaultState();
			}
		});
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		try {
			place(world, chunkBox);
		} catch (RuntimeException e) {
			EvolvedWarfare.LOGGER.error("No se pudo colocar la ciudad de {}", origin, e);
		}
	}

	private void place(StructureWorldAccess world, BlockBox chunkBox) {
		Layout layout = layout(seed, style);
		CityPlan plan = layout.plan();
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// 1) dentro de la muralla, fuera lo que sobre por encima del suelo (árboles, lomas, agua)
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = chunkBox.getMinX(); x <= chunkBox.getMaxX(); x++) {
			for (int z = chunkBox.getMinZ(); z <= chunkBox.getMaxZ(); z++) {
				int lx = x - ox;
				int lz = z - oz;
				if (!plan.inside(lx, lz)) {
					continue;
				}
				for (int ly = 1; ly <= CLEAR_HEIGHT; ly++) {
					if (!plan.has(lx, ly, lz)) {
						pos.set(x, oy + ly, z);
						if (!world.getBlockState(pos).isAir()) {
							world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
		// 2) los bloques de la ciudad que caen en este chunk
		List<CityPlan.Block> mine = new ArrayList<>();
		for (int cx = (chunkBox.getMinX() - ox) >> 4; cx <= (chunkBox.getMaxX() - ox) >> 4; cx++) {
			for (int cz = (chunkBox.getMinZ() - oz) >> 4; cz <= (chunkBox.getMaxZ() - oz) >> 4; cz++) {
				mine.addAll(layout.byChunk().getOrDefault(ChunkPos.toLong(cx, cz), List.of()));
			}
		}
		BlockPos.Mutable below = new BlockPos.Mutable();
		for (CityPlan.Block block : mine) {
			pos.set(ox + block.x(), oy + block.y(), oz + block.z());
			if (!chunkBox.contains(pos)) {
				continue;
			}
			BlockState state = state(block.state());
			if (block.has(CityPlan.GROUND) && !natural(world.getBlockState(pos))) {
				continue;
			}
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			if (needsShapeUpdate(state)) {
				world.getChunk(pos).markBlockForPostProcessing(pos);
			}
			if (block.has(CityPlan.FOUNDATION)) {
				BlockState fill = fillFor(state);
				below.set(pos);
				for (int i = 0; i < MAX_FOUNDATION; i++) {
					below.move(0, -1, 0);
					if (plan.has(block.x(), below.getY() - oy, block.z())) {
						break;
					}
					BlockState under = world.getBlockState(below);
					if (!under.isAir() && !under.isReplaceable() && under.getFluidState().isEmpty()) {
						break;
					}
					world.setBlockState(below, fill, Block.NOTIFY_LISTENERS);
				}
			}
		}
		// 3) botín
		for (CityPlan.Loot loot : plan.lootSpots()) {
			pos.set(ox + loot.x(), oy + loot.y(), oz + loot.z());
			if (chunkBox.contains(pos) && world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
				RegistryKey<LootTable> table = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(loot.table()));
				container.setLootTable(table, seed ^ pos.asLong());
			}
		}
		// 4) la gente
		long key = cityKey();
		BlockPos center = origin.up();
		int radius = Math.max(plan.halfX, plan.halfZ);
		for (CityPlan.Trader t : plan.traders()) {
			BlockPos at = new BlockPos(ox + t.x(), oy + t.y(), oz + t.z());
			if (!chunkBox.contains(at)) {
				continue;
			}
			TraderEntity trader = ModEntities.TRADER.create(world.toServerWorld());
			TraderType type = TraderType.byId(t.type());
			if (trader != null) {
				if (type != null) {
					trader.setTraderType(type);
				}
				trader.setCityHome(at, new BlockPos(ox + t.bedX(), oy + t.bedY(), oz + t.bedZ()), key);
				spawn(world, trader, at, t.yaw());
			}
		}
		for (CityPlan.Guard g : plan.guards()) {
			BlockPos at = new BlockPos(ox + g.x(), oy + g.y(), oz + g.z());
			if (!chunkBox.contains(at)) {
				continue;
			}
			CityGuardEntity guard = ModEntities.CITY_GUARD.create(world.toServerWorld());
			if (guard != null) {
				guard.presetRole(NpcRole.byId(g.role(), NpcRole.WARRIOR));
				guard.setCity(key, center, radius, style);
				spawn(world, guard, at, g.yaw());
			}
		}
		for (CityPlan.Resident r : plan.residents()) {
			BlockPos at = new BlockPos(ox + r.x(), oy + r.y(), oz + r.z());
			if (chunkBox.contains(at)) {
				MobEntity recruit = ModEntities.RECRUIT.create(world.toServerWorld());
				if (recruit != null) {
					spawn(world, recruit, at, world.getRandom().nextFloat() * 360.0F);
				}
			}
		}
	}

	private static void spawn(StructureWorldAccess world, MobEntity mob, BlockPos at, float yaw) {
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setPersistent();
		mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
		world.spawnEntityAndPassengers(mob);
	}

	/** Con qué se rellena debajo: tierra bajo la hierba, la nieve y los caminos; arenisca bajo la arena; si no, lo mismo. */
	private static BlockState fillFor(BlockState state) {
		if (state.isIn(BlockTags.DIRT) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.DIRT_PATH)
				|| state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.COARSE_DIRT)) {
			return Blocks.DIRT.getDefaultState();
		}
		if (state.isIn(BlockTags.SAND)) {
			return Blocks.SANDSTONE.getDefaultState();
		}
		if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return Blocks.STONE.getDefaultState();
		}
		return state;
	}

	/** Lo que puede pisar un camino: tierra, hierba, arena, grava, nieve o piedra. */
	private static boolean natural(BlockState state) {
		return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)
				|| state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.STONE) || state.isOf(Blocks.ANDESITE);
	}

	/** Vallas, muros, barrotes, cristales y escaleras: se unen con lo de al lado al terminar el chunk. */
	private static boolean needsShapeUpdate(BlockState state) {
		Block block = state.getBlock();
		return state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS) || block instanceof PaneBlock || block instanceof StairsBlock;
	}
}
