package com.evolvedwarfare.world.tower;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModStructures;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Map;
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
import net.minecraft.util.BlockRotation;
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
 * Coloca una torre de vigilancia: la vuelve a sacar de su semilla ({@link TowerDesigner}) y pone lo que cae en el chunk
 * que se está generando, girada. Los cimientos bajan hasta el suelo, los caminos solo pisan tierra, los cofres llevan su
 * botín y aparecen los reclutas de guardia.
 */
public class WatchtowerPiece extends StructurePiece {
	/** Hasta dónde bajan los cimientos. */
	private static final int MAX_FOUNDATION = 12;
	private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

	private final BlockPos origin;
	private final long seed;
	private final TowerDesigner.Style style;
	private final boolean snowy;
	private final BlockRotation rotation;
	@Nullable
	private TowerPlan plan;

	public WatchtowerPiece(BlockPos origin, long seed, TowerDesigner.Style style, boolean snowy, BlockRotation rotation) {
		super(ModStructures.WATCHTOWER_PIECE, 0, box(TowerDesigner.design(seed, style, snowy), origin, rotation));
		this.origin = origin;
		this.seed = seed;
		this.style = style;
		this.snowy = snowy;
		this.rotation = rotation;
	}

	public WatchtowerPiece(NbtCompound nbt) {
		super(ModStructures.WATCHTOWER_PIECE, nbt);
		this.origin = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
		this.seed = nbt.getLong("Seed");
		TowerDesigner.Style read;
		try {
			read = TowerDesigner.Style.valueOf(nbt.getString("Style"));
		} catch (IllegalArgumentException e) {
			read = TowerDesigner.Style.TEMPERATE;
		}
		this.style = read;
		this.snowy = nbt.getBoolean("Snowy");
		BlockRotation rot;
		try {
			rot = BlockRotation.valueOf(nbt.getString("Rot"));
		} catch (IllegalArgumentException e) {
			rot = BlockRotation.NONE;
		}
		this.rotation = rot;
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putInt("OX", origin.getX());
		nbt.putInt("OY", origin.getY());
		nbt.putInt("OZ", origin.getZ());
		nbt.putLong("Seed", seed);
		nbt.putString("Style", style.name());
		nbt.putBoolean("Snowy", snowy);
		nbt.putString("Rot", rotation.name());
	}

	public TowerPlan plan() {
		if (plan == null) {
			plan = TowerDesigner.design(seed, style, snowy);
		}
		return plan;
	}

	public BlockPos origin() {
		return origin;
	}

	public BlockRotation rotation() {
		return rotation;
	}

	/**
	 * La caja empieza un bloque por encima del suelo: así, al adaptar el terreno ("beard_thin"), el patio queda llano
	 * a la altura del suelo (lo de debajo se rellena y lo de encima se quita).
	 */
	private static BlockBox box(TowerPlan plan, BlockPos origin, BlockRotation rotation) {
		int[] b = plan.bounds();
		BlockPos a = world(origin, rotation, b[0], 1, b[2]);
		BlockPos c = world(origin, rotation, b[3], b[4], b[5]);
		return new BlockBox(Math.min(a.getX(), c.getX()), origin.getY() + 1, Math.min(a.getZ(), c.getZ()), Math.max(a.getX(), c.getX()),
				origin.getY() + Math.max(2, b[4]), Math.max(a.getZ(), c.getZ()));
	}

	/** De coordenadas de la torre (puerta al norte) al mundo, girada. */
	public static BlockPos world(BlockPos origin, BlockRotation rotation, int x, int y, int z) {
		int rx;
		int rz;
		switch (rotation) {
			case CLOCKWISE_90 -> {
				rx = -z;
				rz = x;
			}
			case CLOCKWISE_180 -> {
				rx = -x;
				rz = -z;
			}
			case COUNTERCLOCKWISE_90 -> {
				rx = z;
				rz = -x;
			}
			default -> {
				rx = x;
				rz = z;
			}
		}
		return origin.add(rx, y, rz);
	}

	private static float yaw(BlockRotation rotation, float yaw) {
		return yaw + switch (rotation) {
			case CLOCKWISE_90 -> 90.0F;
			case CLOCKWISE_180 -> 180.0F;
			case COUNTERCLOCKWISE_90 -> 270.0F;
			default -> 0.0F;
		};
	}

	/** El bloque de un texto como "minecraft:oak_stairs[facing=north,half=top]" (aire si no existe). */
	public static BlockState state(String text) {
		return STATES.computeIfAbsent(text, t -> {
			try {
				return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), t, false).blockState();
			} catch (CommandSyntaxException e) {
				EvolvedWarfare.LOGGER.error("Bloque desconocido en una torre de vigilancia: {}", t);
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
			EvolvedWarfare.LOGGER.error("No se pudo colocar una torre de vigilancia en {}", origin, e);
		}
	}

	private void place(StructureWorldAccess world, BlockBox chunkBox) {
		TowerPlan tower = plan();
		BlockPos.Mutable below = new BlockPos.Mutable();
		for (TowerPlan.Block block : tower.blocks()) {
			BlockPos pos = world(origin, rotation, block.x(), block.y(), block.z());
			if (!chunkBox.contains(pos)) {
				continue;
			}
			BlockState state = state(block.state()).rotate(rotation);
			if (block.has(TowerPlan.GROUND) && !natural(world.getBlockState(pos))) {
				continue;
			}
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			if (needsShapeUpdate(state)) {
				world.getChunk(pos).markBlockForPostProcessing(pos);
			}
			if (block.has(TowerPlan.FOUNDATION)) {
				BlockState fill = state.isIn(BlockTags.DIRT) ? Blocks.DIRT.getDefaultState() : state;
				below.set(pos);
				for (int i = 0; i < MAX_FOUNDATION; i++) {
					below.move(0, -1, 0);
					BlockState under = world.getBlockState(below);
					if (!under.isAir() && !under.isReplaceable() && under.getFluidState().isEmpty()) {
						break;
					}
					world.setBlockState(below, fill, Block.NOTIFY_LISTENERS);
				}
			}
		}
		for (TowerPlan.Loot loot : tower.lootSpots()) {
			BlockPos pos = world(origin, rotation, loot.x(), loot.y(), loot.z());
			if (chunkBox.contains(pos)) {
				BlockEntity entity = world.getBlockEntity(pos);
				if (entity instanceof LootableContainerBlockEntity container) {
					RegistryKey<LootTable> table = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(loot.table()));
					container.setLootTable(table, seed ^ pos.asLong());
				}
			}
		}
		for (TowerPlan.Guard guard : tower.guards()) {
			BlockPos pos = world(origin, rotation, guard.x(), guard.y(), guard.z());
			if (chunkBox.contains(pos)) {
				MobEntity recruit = ModEntities.RECRUIT.create(world.toServerWorld());
				if (recruit != null) {
					recruit.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw(rotation, guard.yaw()), 0.0F);
					recruit.setHeadYaw(recruit.getYaw());
					recruit.setBodyYaw(recruit.getYaw());
					recruit.setPersistent();
					recruit.initialize(world, world.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null);
					world.spawnEntityAndPassengers(recruit);
				}
			}
		}
	}

	/** Lo que puede pisar un camino: tierra, hierba, arena, grava o nieve. */
	private static boolean natural(BlockState state) {
		return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)
				|| state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.STONE) || state.isOf(Blocks.ANDESITE);
	}

	/** Vallas, muros, barrotes, cristales, escaleras y enredaderas: se unen con lo de al lado al terminar el chunk. */
	private static boolean needsShapeUpdate(BlockState state) {
		Block block = state.getBlock();
		return state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS) || block instanceof PaneBlock || block instanceof StairsBlock
				|| state.isOf(Blocks.VINE);
	}
}
