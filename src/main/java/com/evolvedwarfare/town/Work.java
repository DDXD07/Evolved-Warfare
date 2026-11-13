package com.evolvedwarfare.town;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.jetbrains.annotations.Nullable;

/**
 * El trabajo de cada oficio en su pueblo: dónde (el puesto de vanilla que le toca: el herrero en el yunque, la forja o la
 * mesa de herrería; el panadero en el ahumador; el cantero en el cortapiedras; el sastre en el telar...; el campesino en
 * los cultivos, el pescador en el agua, el leñador en los árboles, el cazador fuera de las murallas...), con qué en la
 * mano y qué se oye y se ve al trabajar. No cambia nada del mundo: es la vida del pueblo.
 */
public final class Work {
	/** Hasta dónde busca dónde trabajar (desde su casa). */
	public static final int RADIUS = 40;

	private Work() {
	}

	private static List<RegistryKey<PointOfInterestType>> stations(NpcJob job) {
		return switch (job) {
			case BLACKSMITH -> List.of(PointOfInterestTypes.ARMORER, PointOfInterestTypes.WEAPONSMITH, PointOfInterestTypes.TOOLSMITH);
			case BAKER, INNKEEPER -> List.of(PointOfInterestTypes.BUTCHER);
			case CARPENTER -> List.of(PointOfInterestTypes.FLETCHER);
			case MASON -> List.of(PointOfInterestTypes.MASON);
			case TAILOR -> List.of(PointOfInterestTypes.SHEPHERD, PointOfInterestTypes.LEATHERWORKER);
			case SHEPHERD -> List.of(PointOfInterestTypes.SHEPHERD);
			case FARMER -> List.of(PointOfInterestTypes.FARMER);
			case FISHER -> List.of(PointOfInterestTypes.FISHERMAN);
			case MERCHANT -> List.of(PointOfInterestTypes.LIBRARIAN, PointOfInterestTypes.CARTOGRAPHER);
			default -> List.of();
		};
	}

	/** Busca (o recuerda) dónde trabaja: el bloque junto al que se pone. Null: no hay nada, que pasee por el pueblo. */
	@Nullable
	public static BlockPos workplace(AbstractNpcEntity npc, ServerWorld world) {
		NpcProfile p = npc.ewProfile();
		if (p.workplace != null && stillThere(world, p.job, p.workplace)) {
			return p.workplace;
		}
		long now = world.getTime();
		if (now - p.workSearched < 1200L) {
			return null;
		}
		p.workSearched = now;
		BlockPos home = p.bed != null ? p.bed : npc.getHome() != null ? npc.getHome() : npc.getBlockPos();
		p.workplace = find(world, p.job, home, npc.getRandom());
		return p.workplace;
	}

	private static boolean stillThere(ServerWorld world, NpcJob job, BlockPos pos) {
		if (!world.isChunkLoaded(pos)) {
			return true;
		}
		BlockState s = world.getBlockState(pos);
		return switch (job) {
			case FARMER -> s.getBlock() instanceof CropBlock || world.getPointOfInterestStorage().getType(pos).isPresent();
			case FISHER -> !s.getFluidState().isEmpty() || world.getPointOfInterestStorage().getType(pos).isPresent();
			case LUMBERJACK -> s.isIn(BlockTags.LOGS);
			case MINER -> s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES);
			case HUNTER, LABORER, SHEPHERD, GUARD, MERCENARY -> true;
			default -> world.getPointOfInterestStorage().getType(pos).isPresent();
		};
	}

	@Nullable
	static BlockPos find(ServerWorld world, NpcJob job, BlockPos home, Random random) {
		List<RegistryKey<PointOfInterestType>> types = stations(job);
		if (!types.isEmpty()) {
			Optional<BlockPos> station = world.getPointOfInterestStorage().getPosition(e -> types.stream().anyMatch(e::matchesKey), pos -> true,
					PointOfInterestStorage.OccupationStatus.ANY, home, RADIUS, random);
			if (station.isPresent()) {
				return station.get();
			}
		}
		return switch (job) {
			case FARMER -> sample(world, home, random, Heightmap.Type.WORLD_SURFACE, s -> s.getBlock() instanceof CropBlock);
			case FISHER -> sample(world, home, random, Heightmap.Type.WORLD_SURFACE, s -> s.getFluidState().isStill() && !s.getFluidState().isEmpty());
			case LUMBERJACK -> sample(world, home, random, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, s -> s.isIn(BlockTags.LOGS));
			case MINER -> sample(world, home, random, Heightmap.Type.WORLD_SURFACE, s -> s.isIn(BlockTags.BASE_STONE_OVERWORLD));
			case HUNTER -> outside(world, home, random);
			default -> null;
		};
	}

	/** Prueba sitios al azar en la superficie hasta dar con lo que busca. */
	@Nullable
	private static BlockPos sample(ServerWorld world, BlockPos home, Random random, Heightmap.Type map, java.util.function.Predicate<BlockState> ok) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < 160; i++) {
			int x = home.getX() + random.nextInt(2 * RADIUS + 1) - RADIUS;
			int z = home.getZ() + random.nextInt(2 * RADIUS + 1) - RADIUS;
			if (!world.isChunkLoaded(m.set(x, home.getY(), z))) {
				continue;
			}
			int y = world.getTopY(map, x, z) - 1;
			m.set(x, y, z);
			if (ok.test(world.getBlockState(m))) {
				double d = m.getSquaredDistance(home);
				if (d < bestD) {
					bestD = d;
					best = m.toImmutable();
				}
			}
		}
		return best;
	}

	/** El cazador sale fuera: un sitio a 30-50 bloques de casa. */
	private static BlockPos outside(ServerWorld world, BlockPos home, Random random) {
		double a = random.nextDouble() * Math.PI * 2.0;
		int d = 30 + random.nextInt(20);
		int x = home.getX() + (int) (Math.cos(a) * d), z = home.getZ() + (int) (Math.sin(a) * d);
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Un sitio de pie junto a ese bloque (suelo firme y dos de aire), lo más cerca posible; o el propio sitio. */
	public static BlockPos standNear(ServerWorld world, BlockPos target, int salt) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int k = 0;
		for (BlockPos p : BlockPos.iterate(target.add(-2, -2, -2), target.add(2, 2, 2))) {
			k++;
			if (!standable(world, p)) {
				continue;
			}
			// los que trabajan en el mismo sitio no se ponen todos en el mismo bloque
			double d = p.getSquaredDistance(target) + ((k + salt) % 5) * 0.3;
			if (d < bestD) {
				bestD = d;
				best = p.toImmutable();
			}
		}
		return best != null ? best : target;
	}

	public static boolean standable(ServerWorld world, BlockPos p) {
		BlockState below = world.getBlockState(p.down());
		return !below.getCollisionShape(world, p.down()).isEmpty() && below.getFluidState().isEmpty()
				&& world.getBlockState(p).getCollisionShape(world, p).isEmpty() && world.getBlockState(p).getFluidState().isEmpty()
				&& world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty();
	}

	// ------------------------------------------------------------------ lo que se ve y se oye

	/** Lo que lleva en la mano mientras trabaja (si no lleva nada suyo). */
	public static ItemStack tool(NpcJob job) {
		Item item = switch (job) {
			case FARMER -> Items.IRON_HOE;
			case BAKER -> Items.BREAD;
			case BLACKSMITH -> Items.IRON_PICKAXE;
			case CARPENTER -> Items.WOODEN_AXE;
			case LUMBERJACK -> Items.IRON_AXE;
			case MINER -> Items.STONE_PICKAXE;
			case HUNTER -> Items.BOW;
			case FISHER -> Items.FISHING_ROD;
			case SHEPHERD, TAILOR -> Items.SHEARS;
			case MASON -> Items.BRICK;
			case INNKEEPER -> Items.GLASS_BOTTLE;
			case LABORER -> Items.WHEAT;
			default -> Items.AIR;
		};
		return new ItemStack(item);
	}

	/** Un golpe de trabajo: el brazo, el ruido de su oficio y algo de polvo, chispas o humo en el sitio. */
	public static void act(AbstractNpcEntity npc, ServerWorld world, NpcJob job, BlockPos at) {
		npc.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		SoundEvent sound = switch (job) {
			case BLACKSMITH -> SoundEvents.BLOCK_ANVIL_USE;
			case BAKER, INNKEEPER -> SoundEvents.BLOCK_SMOKER_SMOKE;
			case CARPENTER, LUMBERJACK -> SoundEvents.BLOCK_WOOD_HIT;
			case MINER, MASON -> SoundEvents.BLOCK_STONE_HIT;
			case TAILOR -> SoundEvents.UI_LOOM_SELECT_PATTERN;
			case SHEPHERD -> SoundEvents.ENTITY_SHEEP_SHEAR;
			case FARMER -> SoundEvents.BLOCK_CROP_BREAK;
			case FISHER -> SoundEvents.ENTITY_FISHING_BOBBER_SPLASH;
			case MERCHANT -> SoundEvents.ITEM_BOOK_PAGE_TURN;
			default -> SoundEvents.BLOCK_GRAVEL_HIT;
		};
		float volume = job == NpcJob.BLACKSMITH ? 0.25F : 0.5F;
		world.playSound(null, at, sound, SoundCategory.NEUTRAL, volume, 0.9F + npc.getRandom().nextFloat() * 0.2F);
		ParticleEffect particle = switch (job) {
			case BLACKSMITH -> ParticleTypes.CRIT;
			case BAKER, INNKEEPER -> ParticleTypes.SMOKE;
			case FISHER -> ParticleTypes.SPLASH;
			case FARMER -> ParticleTypes.COMPOSTER;
			default -> null;
		};
		if (particle != null) {
			world.spawnParticles(particle, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.02);
		}
	}

	/** El pastor va hacia las ovejas (si hay cerca). */
	@Nullable
	public static BlockPos flock(AbstractNpcEntity npc, ServerWorld world) {
		List<SheepEntity> sheep = world.getEntitiesByClass(SheepEntity.class, new Box(npc.getBlockPos()).expand(32.0), e -> true);
		return sheep.isEmpty() ? null : sheep.get(npc.getRandom().nextInt(sheep.size())).getBlockPos();
	}
}
