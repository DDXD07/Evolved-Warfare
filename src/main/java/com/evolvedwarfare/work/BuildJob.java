package com.evolvedwarfare.work;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import com.evolvedwarfare.building.BuildMaterials;
import com.evolvedwarfare.building.BuildPlan;
import com.evolvedwarfare.building.Material;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * La obra del edificio del puesto, antes de ponerse con su oficio:
 * <ol>
 *   <li>limpia los huecos del edificio de lo natural que haya (tierra, piedra, hierba, árboles...), de arriba abajo;</li>
 *   <li>rellena con tierra los huecos bajo el suelo (hasta 4 bloques), para que no quede colgando;</li>
 *   <li>pone los bloques de abajo arriba, gastando materiales de los suministros (lo que había donde va cada bloque,
 *       si es natural, lo guarda en la producción); antorchas y faroles al final.</li>
 * </ol>
 * Nunca toca lo que no es natural (cosas puestas por jugadores): esas piezas se quedan sin poner.
 */
public class BuildJob implements Job {
	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		WorkSiteBlockEntity site = ctx.site;
		BuildPlan plan = site.getBuildPlan();
		ServerWorld world = ctx.world;

		// 1. huecos
		for (BuildPlan.Entry entry : plan.clearOrder()) {
			BlockPos pos = entry.pos;
			BlockState current = world.getBlockState(pos);
			if (current.isAir() || !isNatural(current) || ctx.ignored(pos)) {
				continue;
			}
			int ticks = ctx.ticks(MathHelper.clamp(Math.round(6 + current.getHardness(world, pos) * 8), 6, 60));
			return WorkAction.fromSite(entry.standFor(pos), Vec3d.ofCenter(pos), ticks, c -> clear(c, pos)).breaking(pos);
		}

		// 2. cimientos
		for (BuildPlan.Entry entry : plan.foundations()) {
			BlockPos hole = deepestHole(world, entry.pos);
			if (hole != null && !ctx.ignored(hole)) {
				return WorkAction.fromSite(entry.standFor(entry.pos), Vec3d.ofCenter(hole), ctx.ticks(6), c -> fill(c, hole));
			}
		}

		// 3. bloques
		int placed = 0;
		boolean waiting = false;
		Material missing = null;
		WorkAction next = null;
		for (BuildPlan.Entry entry : plan.placeOrder()) {
			BlockPos pos = plan.resolve(world, entry);
			if (pos == null || entry.state == null) {
				continue;
			}
			BlockState current = world.getBlockState(pos);
			if (current.isOf(entry.state.getBlock())) {
				placed++;
				continue;
			}
			if (next != null || entry.piece.has(com.evolvedwarfare.building.Blueprint.UPPER_HALF) || ctx.ignored(pos)) {
				continue;
			}
			if (!canBuildOver(current) || !entry.state.canPlaceAt(world, pos)) {
				continue;
			}
			if (occupied(world, pos)) {
				waiting = true;
				continue;
			}
			if (!BuildMaterials.available(site, entry.piece.material())) {
				if (missing == null) {
					missing = entry.piece.material();
				}
				continue;
			}
			next = WorkAction.fromSite(entry.standFor(pos), Vec3d.ofCenter(pos), ctx.ticks(8), c -> place(c, entry, pos));
		}
		site.setBuildProgress(placed, plan.total());
		if (next != null) {
			site.setNote(WorkSiteBlockEntity.NOTE_NONE);
			return next;
		}
		if (missing != null) {
			site.setNote(WorkSiteBlockEntity.noteMissing(missing));
			return ctx.idle(WorkStatus.NO_MATERIALS);
		}
		if (waiting) {
			return ctx.idle(WorkStatus.BUILDING);
		}
		site.finishBuilding(ctx.worker);
		return ctx.idle(WorkStatus.WORKING);
	}

	// ------------------------------------------------------------------ reglas

	/** Lo que se puede quitar sin preguntar: terreno, plantas, árboles, nieve... nunca cosas construidas. */
	public static boolean isNatural(BlockState state) {
		if (state.isAir() || state.hasBlockEntity()) {
			return false;
		}
		if (state.isReplaceable() && state.getFluidState().isEmpty()) {
			return true;
		}
		return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.SAND)
				|| state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.FLOWERS)
				|| state.isIn(BlockTags.SAPLINGS) || state.isIn(BlockTags.SNOW) || state.isIn(BlockTags.TERRACOTTA)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.CLAY) || state.isOf(Blocks.SANDSTONE)
				|| state.isOf(Blocks.RED_SANDSTONE) || state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.DIRT_PATH)
				|| state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.BROWN_MUSHROOM)
				|| state.isOf(Blocks.RED_MUSHROOM) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SUGAR_CANE)
				|| state.isOf(Blocks.PUMPKIN) || state.isOf(Blocks.MELON) || state.isOf(Blocks.COBWEB);
	}

	/** Hueco que se puede ocupar: aire, plantas bajas, agua o terreno natural. */
	private static boolean canBuildOver(BlockState state) {
		return state.isAir() || isNatural(state)
				|| (state.getBlock() instanceof FluidBlock && state.getFluidState().isIn(FluidTags.WATER));
	}

	private static boolean isHole(BlockState state) {
		return state.isAir() || (state.isReplaceable() && !state.getFluidState().isIn(FluidTags.LAVA));
	}

	/** El hueco más hondo bajo esa pieza (se rellena de abajo arriba), o null si no hay. */
	@Nullable
	private static BlockPos deepestHole(ServerWorld world, BlockPos above) {
		BlockPos deepest = null;
		BlockPos cursor = above.down();
		for (int i = 0; i < 4; i++) {
			if (!isHole(world.getBlockState(cursor))) {
				break;
			}
			deepest = cursor;
			cursor = cursor.down();
		}
		return deepest;
	}

	private static boolean occupied(ServerWorld world, BlockPos pos) {
		return !world.getEntitiesByClass(LivingEntity.class, new Box(pos), Entity::isAlive).isEmpty();
	}

	// ------------------------------------------------------------------ tareas

	private static boolean clear(WorkContext ctx, BlockPos pos) {
		BlockState current = ctx.world.getBlockState(pos);
		if (current.isAir() || !isNatural(current)) {
			return false;
		}
		ctx.harvest(pos);
		return true;
	}

	private static boolean fill(WorkContext ctx, BlockPos pos) {
		if (!isHole(ctx.world.getBlockState(pos))) {
			return false;
		}
		BlockState dirt = Blocks.DIRT.getDefaultState();
		ctx.world.setBlockState(pos, dirt);
		ctx.world.playSound(null, pos, dirt.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.9F);
		return true;
	}

	private static boolean place(WorkContext ctx, BuildPlan.Entry entry, BlockPos pos) {
		ServerWorld world = ctx.world;
		BlockState target = entry.state;
		BlockState current = world.getBlockState(pos);
		if (target == null || current.isOf(target.getBlock()) || !canBuildOver(current) || !target.canPlaceAt(world, pos)
				|| occupied(world, pos)) {
			return false;
		}
		if (!BuildMaterials.consume(ctx.site, entry.piece.material())) {
			return false;
		}
		// lo natural que había ahí (tierra, piedra...) se guarda
		if (!current.isAir() && !current.isReplaceable()) {
			ctx.storeAll(ctx.drops(pos, current));
		}
		Block block = target.getBlock();
		if (block instanceof DoorBlock) {
			world.setBlockState(pos, target.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			world.setBlockState(pos.up(), target.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		} else {
			BlockState state = target;
			// vallas, cristales y escaleras se unen con lo que ya hay al lado
			if (block instanceof FenceBlock || block instanceof PaneBlock || block instanceof StairsBlock) {
				state = Block.postProcessState(target, world, pos);
			}
			world.setBlockState(pos, state);
		}
		world.playSound(null, pos, target.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.9F);
		return true;
	}
}
