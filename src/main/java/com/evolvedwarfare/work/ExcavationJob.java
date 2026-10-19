package com.evolvedwarfare.work;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Minero y excavador: vacían un pozo cuadrado detrás del puesto, capa a capa, guardando lo que sale.
 *
 * <ul>
 *   <li>No tocan cofres ni otros bloques con contenido, ni obsidiana, ni roca madre.</li>
 *   <li>No abren bloques pegados a lava (para no inundar el pozo).</li>
 *   <li>Sin la herramienta de su oficio no pueden con los bloques que la necesitan (piedra, nieve): se paran ahí.
 *       Con una herramienta demasiado floja para una mena (diamante con pico de piedra) la dejan en su sitio.</li>
 *   <li>El excavador solo saca lo que se cava con pala y baja como mucho {@code maxDepth} capas.</li>
 * </ul>
 */
public class ExcavationJob implements Job {
	private static final int LAYERS_PER_PLAN = 4;
	private final boolean shovelOnly;
	private final int maxDepth;

	public ExcavationJob(boolean shovelOnly, int maxDepth) {
		this.shovelOnly = shovelOnly;
		this.maxDepth = maxDepth;
	}

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ServerWorld world = ctx.world;
		WorkArea area = ctx.profession.area();
		BlockPos center = area.center(ctx.sitePos, ctx.facing);
		int half = area.radius();
		int limit = world.getBottomY();
		if (maxDepth > 0) {
			limit = Math.max(limit, ctx.sitePos.getY() - maxDepth);
		}
		int y = ctx.site.getMineY();
		if (y == WorkSiteBlockEntity.UNSET) {
			y = ctx.sitePos.getY() - 1;
			ctx.site.setMineY(y);
		}
		for (int layer = 0; layer < LAYERS_PER_PLAN; layer++, y--) {
			if (y < limit) {
				return ctx.idle(WorkStatus.DONE);
			}
			boolean blocked = false;
			for (int dz = -half; dz <= half; dz++) {
				for (int dx = -half; dx <= half; dx++) {
					BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					if (!canDig(world, pos, state)) {
						continue;
					}
					if (state.isToolRequired() && !ctx.tool().isSuitableFor(state)) {
						if (!ctx.hasTool()) {
							blocked = true;
						}
						continue;
					}
					float hardness = state.getHardness(world, pos);
					int ticks = ctx.ticks(MathHelper.clamp(Math.round(10 + hardness * 12), 8, 160));
					return WorkAction.fromSite(ctx.sitePos, Vec3d.ofCenter(pos), ticks, c -> dig(c, pos)).breaking(pos);
				}
			}
			if (blocked) {
				return ctx.idle(WorkStatus.NEED_TOOL);
			}
			ctx.site.setMineY(y - 1);
		}
		return ctx.idle(WorkStatus.WORKING);
	}

	private boolean canDig(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof FluidBlock || state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getHardness(world, pos);
		if (hardness < 0.0F || hardness >= 50.0F) {
			return false;
		}
		if (shovelOnly && !state.isIn(BlockTags.SHOVEL_MINEABLE)) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (world.getFluidState(pos.offset(direction)).isIn(FluidTags.LAVA)) {
				return false;
			}
		}
		return true;
	}

	private boolean dig(WorkContext ctx, BlockPos pos) {
		BlockState state = ctx.world.getBlockState(pos);
		if (!canDig(ctx.world, pos, state)) {
			return false;
		}
		if (state.isToolRequired() && !ctx.tool().isSuitableFor(state)) {
			return false;
		}
		boolean hard = state.getHardness(ctx.world, pos) > 0.0F;
		ctx.harvest(pos);
		if (hard) {
			ctx.damageTool(1);
		}
		return true;
	}
}
