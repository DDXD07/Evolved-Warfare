package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.SingleStateFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Las decoraciones del Nether bíblico: islas y peñascos de sal, costillares de gigantes medio enterrados, ruinas de casas
 * de piedra negra (también hundidas en los lagos de fuego), pilares de sal y árboles de sal de los huertos petrificados (con
 * sus frutas de piedra).
 */
public final class HellFeatures {
	public static final Feature<SingleStateFeatureConfig> BOULDER = Registry.register(Registries.FEATURE, EvolvedWarfare.id("boulder"),
			new Boulder());
	public static final Feature<DefaultFeatureConfig> RIBCAGE = Registry.register(Registries.FEATURE, EvolvedWarfare.id("ribcage"), new Ribcage());
	public static final Feature<DefaultFeatureConfig> RUIN = Registry.register(Registries.FEATURE, EvolvedWarfare.id("ruin"), new Ruin());
	public static final Feature<DefaultFeatureConfig> SALT_PILLAR = Registry.register(Registries.FEATURE, EvolvedWarfare.id("salt_pillar"),
			new SaltPillar());
	public static final Feature<DefaultFeatureConfig> SALT_TREE = Registry.register(Registries.FEATURE, EvolvedWarfare.id("salt_tree"), new SaltTree());

	private HellFeatures() {
	}

	public static void init() {
	}

	private static boolean replaceable(StructureWorldAccess world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.isAir() || s.isReplaceable() || !s.getFluidState().isEmpty();
	}

	/** Baja hasta el primer bloque sólido (o la superficie de la lava); null si no hay. */
	private static BlockPos ground(StructureWorldAccess world, BlockPos origin) {
		BlockPos.Mutable p = origin.mutableCopy();
		for (int i = 0; i < 12 && p.getY() > world.getBottomY() + 4; i++) {
			BlockState below = world.getBlockState(p.down());
			if (!below.isAir() && !below.isReplaceable() || !below.getFluidState().isEmpty()) {
				return p.toImmutable();
			}
			p.move(Direction.DOWN);
		}
		return null;
	}

	/** Un peñasco irregular; si cae en lava, hace una isla que asoma. */
	static class Boulder extends Feature<SingleStateFeatureConfig> {
		Boulder() {
			super(SingleStateFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<SingleStateFeatureConfig> ctx) {
			StructureWorldAccess world = ctx.getWorld();
			Random r = ctx.getRandom();
			BlockPos at = ground(world, ctx.getOrigin());
			if (at == null) {
				return false;
			}
			boolean lava = !world.getFluidState(at.down()).isEmpty();
			BlockPos center = lava ? at.down(2) : at;
			int rad = 2 + r.nextInt(lava ? 4 : 2);
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					for (int dy = -rad; dy <= rad; dy++) {
						double d = Math.sqrt(dx * dx + dz * dz + dy * dy * (lava ? 2.5 : 1.4)) + r.nextDouble() * 0.9;
						if (d > rad) {
							continue;
						}
						BlockPos p = center.add(dx, dy, dz);
						if (replaceable(world, p) || world.getBlockState(p).isOf(Blocks.BLACKSTONE)) {
							world.setBlockState(p, ctx.getConfig().state, 2);
						}
					}
				}
			}
			return true;
		}
	}

	/** Las costillas de un gigante medio enterrado: arcos de hueso que salen del suelo. */
	static class Ribcage extends Feature<DefaultFeatureConfig> {
		Ribcage() {
			super(DefaultFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
			StructureWorldAccess world = ctx.getWorld();
			Random r = ctx.getRandom();
			BlockPos at = ground(world, ctx.getOrigin());
			if (at == null) {
				return false;
			}
			boolean alongX = r.nextBoolean();
			int ribs = 4 + r.nextInt(4);
			int span = 4 + r.nextInt(3);
			int height = 5 + r.nextInt(4);
			BlockState bone = Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
			for (int k = 0; k < ribs; k++) {
				int along = k * 2 - ribs;
				int h = height - Math.abs(along) / 2;
				for (int side = -1; side <= 1; side += 2) {
					for (int t = 0; t <= h; t++) {
						// el arco: sube y se curva hacia dentro
						double curve = span * Math.cos(t / (double) h * Math.PI / 2.2);
						int w = (int) Math.round(curve) * side;
						BlockPos p = alongX ? at.add(along, t - 2, w) : at.add(w, t - 2, along);
						if (replaceable(world, p) || t < 2) {
							world.setBlockState(p, bone, 2);
						}
					}
				}
			}
			// la columna, a ras de suelo
			for (int k = -ribs - 2; k <= ribs; k++) {
				BlockPos p = alongX ? at.add(k, -1, 0) : at.add(0, -1, k);
				world.setBlockState(p, bone.with(PillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z), 2);
			}
			return true;
		}
	}

	/** Una casa en ruinas de piedra negra: paredes rotas, a medio hundir, con ceniza y azufre dentro. */
	static class Ruin extends Feature<DefaultFeatureConfig> {
		Ruin() {
			super(DefaultFeatureConfig.CODEC);
		}

		private static BlockState wall(Random r) {
			return switch (r.nextInt(6)) {
				case 0 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
				case 1 -> Blocks.NETHER_BRICKS.getDefaultState();
				case 2 -> Blocks.BLACKSTONE.getDefaultState();
				default -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
			};
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
			StructureWorldAccess world = ctx.getWorld();
			Random r = ctx.getRandom();
			BlockPos at = ground(world, ctx.getOrigin());
			if (at == null) {
				return false;
			}
			int sx = 5 + r.nextInt(5);
			int sz = 5 + r.nextInt(5);
			int sink = 1 + r.nextInt(3);
			int tall = 3 + r.nextInt(4);
			BlockPos base = at.add(-sx / 2, -sink, -sz / 2);
			for (int x = 0; x < sx; x++) {
				for (int z = 0; z < sz; z++) {
					boolean edge = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
					// el suelo de dentro
					BlockPos floor = base.add(x, 0, z);
					if (!edge) {
						world.setBlockState(floor, r.nextInt(5) == 0 ? ModBlocks.BRIMSTONE_BLOCK.getDefaultState()
								: r.nextInt(3) == 0 ? ModBlocks.ASH_BLOCK.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState(), 2);
						for (int y = 1; y <= tall + 1; y++) {
							BlockPos in = base.add(x, y, z);
							if (!world.getBlockState(in).getFluidState().isEmpty()) {
								break;
							}
							world.setBlockState(in, Blocks.AIR.getDefaultState(), 2);
						}
						continue;
					}
					// la pared, rota a trozos (y con la puerta)
					boolean door = (x == sx / 2 && z == 0);
					int h = Math.max(0, tall - r.nextInt(tall + 2) + (x % 3 == 0 ? 1 : 0));
					for (int y = 0; y <= h; y++) {
						if (door && y > 0 && y < 3) {
							continue;
						}
						world.setBlockState(base.add(x, y, z), wall(r), 2);
					}
					if (h > 1 && r.nextInt(5) == 0) {
						world.setBlockState(base.add(x, h + 1, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState(), 2);
					}
				}
			}
			// cascotes por fuera
			for (int i = 0; i < 6; i++) {
				BlockPos p = ground(world, at.add(r.nextInt(sx + 6) - sx / 2 - 3, 4, r.nextInt(sz + 6) - sz / 2 - 3));
				if (p != null && replaceable(world, p) && world.getFluidState(p.down()).isEmpty()) {
					world.setBlockState(p, r.nextBoolean() ? Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getDefaultState() : wall(r), 2);
				}
			}
			return true;
		}
	}

	/** Un pilar de sal: de 3 a 10 de alto, más gordo abajo, con la punta irregular; algunos parecen una persona. */
	static class SaltPillar extends Feature<DefaultFeatureConfig> {
		SaltPillar() {
			super(DefaultFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
			StructureWorldAccess world = ctx.getWorld();
			Random r = ctx.getRandom();
			BlockPos at = ground(world, ctx.getOrigin());
			if (at == null || !world.getFluidState(at.down()).isEmpty()) {
				return false;
			}
			BlockState salt = ModBlocks.SALT_BLOCK.getDefaultState();
			if (r.nextInt(3) == 0) {
				// una figura: piernas, cuerpo, brazos pegados y cabeza
				world.setBlockState(at, salt, 2);
				world.setBlockState(at.up(), salt, 2);
				world.setBlockState(at.up(2), salt, 2);
				world.setBlockState(at.up(3), ModBlocks.SALT_BLOCK.getDefaultState(), 2);
				world.setBlockState(at.up(4), Blocks.CALCITE.getDefaultState(), 2);
				return true;
			}
			int h = 3 + r.nextInt(8);
			boolean thick = h > 6;
			for (int y = -1; y < h; y++) {
				world.setBlockState(at.up(y), salt, 2);
				if (thick && y < h / 2) {
					for (Direction d : Direction.Type.HORIZONTAL) {
						if (r.nextInt(3) != 0) {
							world.setBlockState(at.up(y).offset(d), salt, 2);
						}
					}
				}
			}
			return true;
		}
	}

	/** Un árbol de sal (de un huerto petrificado): tronco y ramas de sal, con frutas de piedra colgando. */
	static class SaltTree extends Feature<DefaultFeatureConfig> {
		SaltTree() {
			super(DefaultFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
			StructureWorldAccess world = ctx.getWorld();
			Random r = ctx.getRandom();
			BlockPos at = ground(world, ctx.getOrigin());
			if (at == null || !world.getFluidState(at.down()).isEmpty()) {
				return false;
			}
			BlockState salt = ModBlocks.SALT_BLOCK.getDefaultState();
			int trunk = 3 + r.nextInt(3);
			for (int y = 0; y < trunk; y++) {
				world.setBlockState(at.up(y), salt, 2);
			}
			BlockPos top = at.up(trunk);
			for (Direction d : Direction.Type.HORIZONTAL) {
				if (r.nextInt(4) == 0) {
					continue;
				}
				BlockPos p = top;
				int len = 2 + r.nextInt(2);
				for (int i = 0; i < len; i++) {
					p = p.offset(d).up(i % 2);
					if (!replaceable(world, p)) {
						break;
					}
					world.setBlockState(p, salt, 2);
					BlockPos under = p.down();
					if (i > 0 && r.nextInt(2) == 0 && world.getBlockState(under).isAir()) {
						world.setBlockState(under, ModBlocks.STONE_FRUIT.getDefaultState(), 2);
					}
				}
			}
			world.setBlockState(top, salt, 2);
			if (r.nextBoolean()) {
				world.setBlockState(top.up(), salt, 2);
			}
			return true;
		}
	}
}
