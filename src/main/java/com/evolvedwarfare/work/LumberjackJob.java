package com.evolvedwarfare.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Leñador (zona 15x15): tala árboles enteros (solo árboles de verdad: troncos con hojas naturales y sin nada
 * construido pegado, así que nunca toca casas de troncos), replanta un brote en el tocón, recoge brotes, palos y manzanas del suelo y planta brotes en cuadrícula
 * (cada 3 bloques) donde haya sitio. Con harina de hueso en los suministros abona los brotes.
 */
public class LumberjackJob implements Job {
	private static final int MAX_LOGS = 256;
	private static final int REJECT_TICKS = 1200;
	static final Predicate<ItemStack> SAPLING = s -> s.isIn(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem;
	private static final Predicate<ItemStack> LOOT = s -> s.isIn(ItemTags.SAPLINGS) || s.isIn(ItemTags.LOGS)
			|| s.isOf(Items.STICK) || s.isOf(Items.APPLE);

	/** Troncos que miró y no eran un árbol (no los vuelve a mirar en un rato). */
	private final Map<BlockPos, Long> rejected = new HashMap<>();

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ServerWorld world = ctx.world;
		long now = world.getTime();
		rejected.values().removeIf(until -> until < now);

		ItemEntity item = Jobs.nearestItem(ctx, ctx.areaBox(3.0), LOOT);
		if (item != null) {
			return Jobs.pickup(item);
		}

		BlockBox area = ctx.area();
		boolean saplings = ctx.site.count(SAPLING, true) > 0;
		boolean boneMeal = ctx.site.count(Jobs.BONE_MEAL, false) > 0;
		List<BlockPos> bases = new ArrayList<>();
		Nearest spot = new Nearest();
		Nearest grow = new Nearest();
		boolean growing = false;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = area.getMinX(); x <= area.getMaxX(); x++) {
			for (int z = area.getMinZ(); z <= area.getMaxZ(); z++) {
				if (x == ctx.sitePos.getX() && z == ctx.sitePos.getZ()) {
					continue;
				}
				BlockPos ground = null;
				for (int y = area.getMaxY(); y >= area.getMinY(); y--) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (state.isIn(BlockTags.LOGS)) {
						if (world.getBlockState(pos.down()).isIn(BlockTags.DIRT)) {
							BlockPos base = pos.toImmutable();
							if (!rejected.containsKey(base) && !ctx.ignored(base)) {
								bases.add(base);
							}
							break;
						}
						continue;
					}
					if (state.getBlock() instanceof SaplingBlock) {
						growing = true;
						if (boneMeal) {
							grow.offer(ctx, pos);
						}
						break;
					}
					if (Jobs.isClear(state) || state.isIn(BlockTags.LEAVES)) {
						continue;
					}
					ground = pos.toImmutable();
					break;
				}
				if (saplings && ground != null && isGridCell(ctx, x, z) && canPlantAt(world, ground) && !ctx.ignored(ground.up())) {
					spot.offer(ctx, ground.up());
				}
			}
		}

		// el árbol más cercano (comprueba hasta 3 por vez)
		bases.sort(Comparator.comparingDouble(ctx::distanceSq));
		for (int i = 0; i < Math.min(3, bases.size()); i++) {
			BlockPos base = bases.get(i);
			List<BlockPos> logs = treeAt(world, base);
			if (logs == null) {
				rejected.put(base, now + REJECT_TICKS);
				continue;
			}
			return WorkAction.atBlock(base, ctx.ticks(30 + 3 * logs.size()), c -> fell(c, base)).breaking(base);
		}
		if (bases.size() > 3) {
			return ctx.idle(WorkStatus.WORKING);
		}
		if (spot.found()) {
			BlockPos target = spot.pos;
			return WorkAction.atBlock(target, ctx.ticks(15), c -> plantSapling(c, target));
		}
		if (grow.found()) {
			BlockPos target = grow.pos;
			return WorkAction.atBlock(target, ctx.ticks(10), c -> Jobs.fertilize(c, target));
		}
		if (growing) {
			return ctx.idle(WorkStatus.WAITING);
		}
		return ctx.idle(saplings ? WorkStatus.NOTHING_HERE : WorkStatus.NO_SUPPLIES);
	}

	/** Los brotes se plantan en una cuadrícula cada 3 bloques, para que los árboles quepan. */
	private static boolean isGridCell(WorkContext ctx, int x, int z) {
		return Math.floorMod(x - ctx.sitePos.getX(), 3) == 0 && Math.floorMod(z - ctx.sitePos.getZ(), 3) == 0;
	}

	private static boolean canPlantAt(ServerWorld world, BlockPos ground) {
		if (!world.getBlockState(ground).isIn(BlockTags.DIRT)) {
			return false;
		}
		BlockPos above = ground.up();
		if (!Jobs.isClear(world, above) || !Jobs.isClear(world, above.up())) {
			return false;
		}
		// ni troncos ni brotes cerca
		for (BlockPos p : BlockPos.iterate(above.add(-2, -1, -2), above.add(2, 2, 2))) {
			BlockState state = world.getBlockState(p);
			if (state.isIn(BlockTags.LOGS) || state.getBlock() instanceof SaplingBlock) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Todos los troncos unidos a ese tocón, o null si no es un árbol: demasiado grande, o sin ninguna hoja
	 * natural pegada (las que pone un jugador son "persistentes" y no cuentan).
	 */
	@Nullable
	static List<BlockPos> treeAt(ServerWorld world, BlockPos base) {
		if (!world.getBlockState(base).isIn(BlockTags.LOGS)) {
			return null;
		}
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> logs = new ArrayList<>();
		boolean naturalLeaves = false;
		queue.add(base);
		seen.add(base);
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			logs.add(p);
			if (logs.size() > MAX_LOGS) {
				return null;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = p.add(dx, dy, dz);
						if (n.getY() < base.getY() || n.getY() - base.getY() > 40
								|| Math.abs(n.getX() - base.getX()) > 12 || Math.abs(n.getZ() - base.getZ()) > 12
								|| !seen.add(n)) {
							continue;
						}
						BlockState state = world.getBlockState(n);
						if (state.isIn(BlockTags.LOGS)) {
							queue.add(n);
						} else if (isBuilt(state)) {
							// pegado a tablones, puertas, cristal... es una construcción, no un árbol
							return null;
						} else if (!naturalLeaves && state.isIn(BlockTags.LEAVES)
								&& state.contains(Properties.PERSISTENT) && !state.get(Properties.PERSISTENT)) {
							naturalLeaves = true;
						}
					}
				}
			}
		}
		return naturalLeaves ? logs : null;
	}

	/** Bloques de construcción que nunca están pegados a un árbol natural. */
	private static boolean isBuilt(BlockState state) {
		return state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.STAIRS) || state.isIn(BlockTags.SLABS)
				|| state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.FENCES)
				|| state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.WALLS) || state.isIn(BlockTags.WOOL)
				|| state.isIn(BlockTags.BEDS) || state.isOf(Blocks.GLASS) || state.isOf(Blocks.GLASS_PANE)
				|| state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.BRICKS)
				|| state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.LANTERN)
				|| state.isOf(Blocks.CHEST) || state.isOf(Blocks.CRAFTING_TABLE);
	}

	private static boolean fell(WorkContext ctx, BlockPos base) {
		ServerWorld world = ctx.world;
		List<BlockPos> logs = treeAt(world, base);
		if (logs == null) {
			return false;
		}
		Block logBlock = world.getBlockState(base).getBlock();
		logs.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos log : logs) {
			ctx.harvest(log);
		}
		ctx.damageTool(Math.max(1, logs.size() / 4));
		replant(ctx, base, logBlock);
		return true;
	}

	private static void replant(WorkContext ctx, BlockPos base, Block log) {
		ServerWorld world = ctx.world;
		if (!world.getBlockState(base).isAir() || !world.getBlockState(base.down()).isIn(BlockTags.DIRT)) {
			return;
		}
		Item preferred = saplingFor(log);
		ItemStack sapling = preferred != null ? ctx.site.find(s -> s.isOf(preferred), true) : null;
		if (sapling == null) {
			sapling = ctx.site.find(SAPLING, true);
		}
		if (sapling == null) {
			return;
		}
		BlockState state = ((BlockItem) sapling.getItem()).getBlock().getDefaultState();
		if (state.canPlaceAt(world, base)) {
			world.setBlockState(base, state);
			sapling.decrement(1);
			ctx.site.markDirty();
		}
	}

	/** El brote que corresponde a un tronco (roble → brote de roble...). */
	@Nullable
	private static Item saplingFor(Block log) {
		Identifier id = Registries.BLOCK.getId(log);
		String path = id.getPath().replace("stripped_", "");
		String sapling = path.startsWith("mangrove") ? "mangrove_propagule"
				: path.replace("_log", "_sapling").replace("_wood", "_sapling");
		Item item = Registries.ITEM.get(Identifier.of(id.getNamespace(), sapling));
		return item == Items.AIR ? null : item;
	}

	private static boolean plantSapling(WorkContext ctx, BlockPos pos) {
		ServerWorld world = ctx.world;
		if (!Jobs.isClear(world, pos) || !world.getBlockState(pos.down()).isIn(BlockTags.DIRT)) {
			return false;
		}
		for (int i = 0; i < ctx.site.size(); i++) {
			ItemStack stack = ctx.site.getStack(i);
			if (!SAPLING.test(stack)) {
				continue;
			}
			BlockState state = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
			if (!state.canPlaceAt(world, pos)) {
				continue;
			}
			if (!world.getBlockState(pos).isAir()) {
				world.breakBlock(pos, false, ctx.worker);
			}
			world.setBlockState(pos, state);
			stack.decrement(1);
			ctx.site.markDirty();
			ctx.sound(pos, SoundEvents.BLOCK_GRASS_PLACE, 1.0F, MathHelper.nextFloat(ctx.random(), 0.8F, 1.2F));
			return true;
		}
		return false;
	}
}
