package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * El Nether vivo (fase 37): la flora de los biomas nuevos (Bosque Calcinado, Selva de Espinas, Ciénaga de los Ahorcados y
 * Pradera de Brasas), las lianas del techo y los árboles de los ahorcados, y la fauna: polillas de brasa, ciervos de ceniza
 * y sapos de lava. (Las texturas, árboles y matas: tools/ew_hellflora.py.)
 */
public final class HellFlora {
	public static final List<Item> CREATIVE = new ArrayList<>();

	// ------------------------------------------------------------------ las plantas

	/** Una planta del infierno: crece sobre cualquier suelo macizo (tierra chamuscada, piedra negra, barro, almas...). */
	public static class Plant extends PlantBlock {
		public static final MapCodec<Plant> CODEC = createCodec(Plant::new);
		private final VoxelShape shape;

		public Plant(Settings settings) {
			this(settings, Block.createCuboidShape(2, 0, 2, 14, 13, 14));
		}

		public Plant(Settings settings, VoxelShape shape) {
			super(settings);
			this.shape = shape;
		}

		@Override
		protected MapCodec<? extends PlantBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
			return floor.isSideSolidFullSquare(world, pos, Direction.UP) && !floor.isOf(Blocks.MAGMA_BLOCK) && !floor.isOf(Blocks.BEDROCK);
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return shape;
		}
	}

	/** Una planta que brilla y suelta chispas (la flor infernal, la hierba de brasa). */
	public static class Glowing extends Plant {
		public static final MapCodec<Glowing> CODEC = createCodec(Glowing::new);

		public Glowing(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends PlantBlock> getCodec() {
			return CODEC;
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			if (random.nextInt(6) == 0) {
				world.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.6, pos.getZ() + 0.3
						+ random.nextDouble() * 0.4, 0, 0.01, 0);
			}
		}
	}

	/** La zarza de espinas: frena y pincha a lo que la atraviesa (menos a los ciervos de ceniza). */
	public static class Thorns extends Plant {
		public static final MapCodec<Thorns> CODEC = createCodec(Thorns::new);

		public Thorns(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends PlantBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
			if (!(entity instanceof LivingEntity) || entity instanceof AshStagEntity || entity.getType() == EntityType.HOGLIN) {
				return;
			}
			entity.slowMovement(state, new Vec3d(0.8, 0.75, 0.8));
			if (!world.isClient && (entity.lastRenderX != entity.getX() || entity.lastRenderZ != entity.getZ())) {
				double dx = Math.abs(entity.getX() - entity.lastRenderX), dz = Math.abs(entity.getZ() - entity.lastRenderZ);
				if (dx >= 0.003 || dz >= 0.003) {
					entity.damage(world.getDamageSources().sweetBerryBush(), 1.5F);
				}
			}
		}
	}

	/** Lo que cuelga del techo (la liana de brasas): se cae si se queda sin nada encima. */
	public static class Hanging extends Block {
		public static final MapCodec<Hanging> CODEC = createCodec(Hanging::new);
		private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 16, 12);

		public Hanging(Settings settings) {
			super(settings);
		}

		@Override
		protected MapCodec<? extends Block> getCodec() {
			return CODEC;
		}

		@Override
		protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
			BlockState up = world.getBlockState(pos.up());
			return up.isOf(this) || up.isSideSolidFullSquare(world, pos.up(), Direction.DOWN) || up.isIn(BlockTags.LEAVES);
		}

		@Override
		protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
				BlockPos neighborPos) {
			return direction == Direction.UP && !canPlaceAt(state, world, pos) ? Blocks.AIR.getDefaultState()
					: super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
		}

		@Override
		protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return SHAPE;
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			if (random.nextInt(10) == 0 && world.getBlockState(pos.down()).isAir()) {
				world.addParticle(ParticleTypes.DRIPPING_LAVA, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0, 0);
			}
		}
	}

	/** Las hojas de brasa: brillan y sueltan pavesas. */
	public static class EmberLeaves extends LeavesBlock {
		public EmberLeaves(Settings settings) {
			super(settings);
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			super.randomDisplayTick(state, world, pos, random);
			if (random.nextInt(8) == 0 && world.getBlockState(pos.down()).isAir()) {
				world.addParticle(ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY(), pos.getZ() + random.nextDouble(), 0, 0, 0);
			} else if (random.nextInt(4) == 0) {
				world.addParticle(ParticleTypes.ASH, pos.getX() + random.nextDouble(), pos.getY(), pos.getZ() + random.nextDouble(), 0, -0.02, 0);
			}
		}
	}

	private static AbstractBlock.Settings plant(MapColor color, int light) {
		return AbstractBlock.Settings.create().mapColor(color).noCollision().breakInstantly().sounds(BlockSoundGroup.NETHER_SPROUTS)
				.offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY).luminance(s -> light);
	}

	public static final Block SCORCHED_SOIL = block("scorched_soil", new Block(AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.6F)
			.sounds(BlockSoundGroup.SOUL_SOIL)));
	public static final Block CHARRED_LOG = block("charred_log", new PillarBlock(AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(2.0F)
			.sounds(BlockSoundGroup.NETHER_STEM)));
	public static final Block CHARRED_PLANKS = block("charred_planks", new Block(AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(2.0F, 3.0F)
			.sounds(BlockSoundGroup.NETHER_WOOD)));
	public static final Block EMBER_LEAVES = block("ember_leaves", new EmberLeaves(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(MapColor.ORANGE)
			.luminance(s -> 7).sounds(BlockSoundGroup.WART_BLOCK)));
	public static final Block THORN_LEAVES = block("thorn_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(MapColor.DARK_RED)
			.sounds(BlockSoundGroup.WART_BLOCK)));
	public static final Block EMBER_GRASS = block("ember_grass", new Glowing(plant(MapColor.ORANGE, 3).replaceable()));
	public static final Block EMBER_FERN = block("ember_fern", new Plant(plant(MapColor.DARK_RED, 0).replaceable()));
	public static final Block HELLBLOOM = block("hellbloom", new Glowing(plant(MapColor.ORANGE, 10)));
	public static final Block CORPSE_LILY = block("corpse_lily", new Plant(plant(MapColor.WHITE_GRAY, 0)));
	public static final Block BLOODCAP = block("bloodcap", new Plant(plant(MapColor.RED, 6), Block.createCuboidShape(5, 0, 5, 11, 6, 11)));
	public static final Block THORN_BUSH = block("thorn_bush", new Thorns(plant(MapColor.DARK_RED, 0)));
	public static final Block EMBER_VINE = block("ember_vine", new Hanging(AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).noCollision()
			.breakInstantly().luminance(s -> 8).sounds(BlockSoundGroup.WEEPING_VINES).pistonBehavior(PistonBehavior.DESTROY)));

	public static final Item RAW_ASH_VENISON = item("raw_ash_venison", new Item(new Item.Settings().food(new FoodComponent.Builder().nutrition(3)
			.saturationModifier(0.3F).build())));
	public static final Item COOKED_ASH_VENISON = item("cooked_ash_venison", new Item(new Item.Settings().food(new FoodComponent.Builder().nutrition(8)
			.saturationModifier(0.8F).statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0), 0.3F).build())));

	// ------------------------------------------------------------------ la fauna

	public static final EntityType<EmberMothEntity> EMBER_MOTH = type("ember_moth", EntityType.Builder.create(EmberMothEntity::new, SpawnGroup.AMBIENT)
			.dimensions(0.5F, 0.5F).eyeHeight(0.25F).makeFireImmune().maxTrackingRange(5), 0x3A2A22, 0xFF7820);
	public static final EntityType<AshStagEntity> ASH_STAG = type("ash_stag", EntityType.Builder.create(AshStagEntity::new, SpawnGroup.CREATURE)
			.dimensions(0.9F, 1.6F).eyeHeight(1.45F).makeFireImmune().maxTrackingRange(10), 0x46403E, 0xE65A1E);
	public static final EntityType<LavaToadEntity> LAVA_TOAD = type("lava_toad", EntityType.Builder.create(LavaToadEntity::new, SpawnGroup.CREATURE)
			.dimensions(0.7F, 0.45F).eyeHeight(0.3F).makeFireImmune().maxTrackingRange(8), 0x281E1C, 0xFF5A14);

	// ------------------------------------------------------------------ los rasgos del mundo

	public static final Feature<DefaultFeatureConfig> GALLOWS_TREE = Registry.register(Registries.FEATURE, EvolvedWarfare.id("gallows_tree"), new Gallows());
	public static final Feature<DefaultFeatureConfig> CEILING_VINES = Registry.register(Registries.FEATURE, EvolvedWarfare.id("ceiling_vines"),
			new CeilingVines());

	private HellFlora() {
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EvolvedWarfare.id(name), block);
		CREATIVE.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), new BlockItem(block, new Item.Settings())));
		return block;
	}

	private static Item item(String name, Item item) {
		CREATIVE.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	private static <T extends MobEntity> EntityType<T> type(String id, EntityType.Builder<T> builder, int a, int b) {
		EntityType<T> type = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id(id), builder.build(id));
		item(id + "_spawn_egg", new SpawnEggItem(type, a, b, new Item.Settings()));
		return type;
	}

	/** ¿Hay suelo macizo debajo (para que aparezcan los animales)? */
	private static boolean ground(WorldAccess world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.down());
		return below.isSideSolidFullSquare(world, pos.down(), Direction.UP) && !below.isOf(Blocks.BEDROCK) && world.getBlockState(pos).isAir();
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(EMBER_MOTH, EmberMothEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ASH_STAG, AshStagEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LAVA_TOAD, LavaToadEntity.createAttributes());
		SpawnRestriction.register(EMBER_MOTH, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				(type, world, reason, pos, random) -> world.getBlockState(pos).isAir() && pos.getY() < 240);
		SpawnRestriction.register(ASH_STAG, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				(type, world, reason, pos, random) -> ground(world, pos));
		SpawnRestriction.register(LAVA_TOAD, SpawnLocationTypes.UNRESTRICTED, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				(type, world, reason, pos, random) -> ground(world, pos) || world.getFluidState(pos.down()).isIn(net.minecraft.registry.tag.FluidTags.LAVA)
						&& world.getBlockState(pos).isAir());
	}

	// ------------------------------------------------------------------ los árboles de los ahorcados y las lianas del techo

	/** Un árbol muerto de los ahorcados: tronco calcinado, una rama y de ella cuelga una cadena con un farol de almas o una calavera. */
	static class Gallows extends Feature<DefaultFeatureConfig> {
		Gallows() {
			super(DefaultFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
			StructureWorldAccess world = context.getWorld();
			BlockPos origin = context.getOrigin();
			Random random = context.getRandom();
			if (!world.getBlockState(origin).isAir() || !world.getBlockState(origin.down()).isSideSolidFullSquare(world, origin.down(), Direction.UP)) {
				return false;
			}
			int h = 5 + random.nextInt(4);
			for (int y = 0; y < h; y++) {
				if (!world.getBlockState(origin.up(y)).isAir() && !world.getBlockState(origin.up(y)).isReplaceable()) {
					return false;
				}
			}
			world.setBlockState(origin.down(), SCORCHED_SOIL.getDefaultState(), Block.NOTIFY_LISTENERS);
			for (int y = 0; y < h; y++) {
				world.setBlockState(origin.up(y), CHARRED_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			Direction dir = Direction.Type.HORIZONTAL.random(random);
			int len = 2 + random.nextInt(2);
			BlockPos branch = origin.up(h - 1);
			for (int k = 1; k <= len; k++) {
				BlockPos p = branch.offset(dir, k);
				if (world.getBlockState(p).isAir()) {
					world.setBlockState(p, CHARRED_LOG.getDefaultState().with(PillarBlock.AXIS, dir.getAxis()), Block.NOTIFY_LISTENERS);
				}
			}
			// la cuerda (una cadena) y lo que cuelga
			BlockPos rope = branch.offset(dir, len).down();
			int chain = 1 + random.nextInt(3);
			for (int k = 0; k < chain; k++) {
				if (!world.getBlockState(rope.down(k)).isAir()) {
					return true;
				}
				world.setBlockState(rope.down(k), Blocks.CHAIN.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			BlockPos end = rope.down(chain);
			if (world.getBlockState(end).isAir()) {
				world.setBlockState(end, random.nextBoolean() ? Blocks.SOUL_LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true)
						: Blocks.SKELETON_SKULL.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			// raíces de brasa a veces, y una rama corta al otro lado
			if (random.nextBoolean()) {
				BlockPos other = branch.down(1 + random.nextInt(2)).offset(dir.getOpposite());
				if (world.getBlockState(other).isAir()) {
					world.setBlockState(other, CHARRED_LOG.getDefaultState().with(PillarBlock.AXIS, dir.getAxis()), Block.NOTIFY_LISTENERS);
				}
			}
			return true;
		}
	}

	/** Las lianas de brasas: desde donde caiga, busca el techo hacia arriba y cuelga de él una liana larga. */
	static class CeilingVines extends Feature<DefaultFeatureConfig> {
		CeilingVines() {
			super(DefaultFeatureConfig.CODEC);
		}

		@Override
		public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
			return hang(context.getWorld(), context.getOrigin(), context.getRandom()) > 0;
		}

		/** Devuelve cuántos bloques de liana puso. */
		static int hang(StructureWorldAccess world, BlockPos origin, Random random) {
			if (!world.getBlockState(origin).isAir()) {
				return 0;
			}
			BlockPos.Mutable p = origin.mutableCopy();
			int up = 0;
			while (up < 48 && world.getBlockState(p.up()).isAir() && p.getY() < world.getTopY() - 2) {
				p.move(Direction.UP);
				up++;
			}
			BlockPos roof = p.up();
			BlockState r = world.getBlockState(roof);
			if (up >= 48 || !r.isSideSolidFullSquare(world, roof, Direction.DOWN) || !r.getFluidState().isEmpty()) {
				return 0;
			}
			int len = 3 + random.nextInt(14);
			int placed = 0;
			for (int k = 0; k < len; k++) {
				BlockPos v = roof.down(k + 1);
				if (!world.getBlockState(v).isAir()) {
					break;
				}
				world.setBlockState(v, EMBER_VINE.getDefaultState(), Block.NOTIFY_LISTENERS);
				placed++;
			}
			return placed;
		}
	}

	/** Para las pruebas: hace crecer un árbol de los ahorcados en pos (devuelve si pudo). */
	public static boolean growGallows(ServerWorld world, BlockPos pos) {
		return GALLOWS_TREE.generateIfValid(DefaultFeatureConfig.INSTANCE, world, world.getChunkManager().getChunkGenerator(), world.random, pos);
	}

	/** Para las pruebas: cuelga lianas del techo que haya encima de pos. */
	public static int hangVines(ServerWorld world, BlockPos pos) {
		return CeilingVines.hang(world, pos, world.random);
	}
}
