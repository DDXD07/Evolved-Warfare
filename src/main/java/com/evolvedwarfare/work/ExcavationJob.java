package com.evolvedwarfare.work;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Minero y excavador: vacían un pozo detrás del puesto, capa a capa, guardando lo que sale.
 *
 * <p><b>Excavador</b>: pozo de 7x7 abierto desde la superficie, separado del puesto por un pasillo de un bloque, de
 * hasta {@code maxDepth} capas. Solo saca lo que se cava con pala.</p>
 *
 * <p><b>Minero</b>: trabaja bajo tierra, sin abrir un agujero en la superficie.</p>
 *
 * <pre>
 *  vista de lado (el puesto mira a la izquierda; "detrás" es la derecha)
 *
 *   P .                      P = puesto, . = pasillo de un bloque
 *   ##~~~ ######## superficie
 *   ###~~~########
 *   ####~~~###########       ~ = entrada: 5 escalones (3 de alto) que bajan un bloque cada uno
 *   #####~~~~########        R = rellano, a 5 bloques de profundidad
 *   ######~~R[pozo...]       el pozo empieza junto al rellano, con 3 bloques de techo encima,
 *   ########[  ...   ]       y baja capa a capa
 * </pre>
 *
 * <ol>
 *   <li>Primero cava la <b>entrada</b> (escalones y rellano). Lo que sale de ahí lo guarda siempre.</li>
 *   <li>Si hay <b>antorchas</b> en los suministros, pone una en la escalera y otra en el rellano.</li>
 *   <li>Después vacía el <b>pozo</b> capa a capa, trabajando desde el rellano.</li>
 * </ol>
 *
 * <p>En el pozo: no tocan cofres ni otros bloques con contenido, ni obsidiana, ni roca madre, ni bloques pegados a
 * lava. En la primera capa no quitan la arena o grava que sostiene el techo. Sin la herramienta de su oficio no
 * pueden con los bloques que la necesitan (piedra, nieve) y se paran ahí; con una demasiado floja para una mena
 * (diamante con pico de piedra) la dejan en su sitio.</p>
 */
public class ExcavationJob implements Job {
	/** Escalones de la entrada (cada uno baja un bloque). */
	public static final int STEPS = 5;
	/** Distancia detrás del puesto a la que empieza el pozo: pasillo, 5 escalones y rellano. */
	public static final int PIT_START = STEPS + 3;
	private static final int LAYERS_PER_PLAN = 4;
	private static final Predicate<ItemStack> TORCH = s -> s.isOf(Items.TORCH);

	private final boolean shovelOnly;
	private final int maxDepth;

	public ExcavationJob(boolean shovelOnly, int maxDepth) {
		this.shovelOnly = shovelOnly;
		this.maxDepth = maxDepth;
	}

	// ------------------------------------------------------------------ geometría

	/**
	 * Dónde se pisa en cada tramo de la entrada: 0 = pasillo detrás del puesto, 1-5 = escalones (un bloque más
	 * abajo cada uno), 6 = rellano.
	 */
	public static BlockPos stepFeet(BlockPos site, Direction back, int step) {
		if (step <= STEPS) {
			return site.offset(back, step + 1).down(step);
		}
		return site.offset(back, STEPS + 2).down(STEPS);
	}

	/** El rellano: el sitio desde donde trabaja el pozo. */
	public static BlockPos landing(BlockPos site, Direction back) {
		return stepFeet(site, back, STEPS + 1);
	}

	/** Primera capa del pozo (la de la cabeza en el rellano). */
	public static int pitTop(BlockPos site) {
		return site.getY() - STEPS + 1;
	}

	/** Bloques que hay que vaciar para hacer la entrada, en orden (cada tramo, de arriba abajo). */
	public static List<BlockPos> entranceBlocks(BlockPos site, Direction back) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int step = 1; step <= STEPS + 1; step++) {
			BlockPos feet = stepFeet(site, back, step);
			blocks.add(feet.up(2));
			blocks.add(feet.up());
			blocks.add(feet);
		}
		return blocks;
	}

	// ------------------------------------------------------------------ plan

	@Override
	@Nullable
	public WorkAction plan(WorkContext ctx) {
		if (ctx.outputFull()) {
			return ctx.idle(WorkStatus.STORAGE_FULL);
		}
		ServerWorld world = ctx.world;
		Direction back = ctx.facing.getOpposite();
		WorkArea area = ctx.profession.area();
		boolean underground = area.underground();

		// 1. la entrada (solo la mina bajo tierra)
		List<BlockPos> entrance = underground ? entranceBlocks(ctx.sitePos, back) : List.of();
		for (int i = 0; i < entrance.size(); i++) {
			BlockPos pos = entrance.get(i);
			BlockState state = world.getBlockState(pos);
			if (isOpen(state) || !canDig(world, pos, state, false)) {
				continue;
			}
			// se pone en el tramo anterior (o en el pasillo para el primero)
			BlockPos stand = stepFeet(ctx.sitePos, back, i / 3);
			int ticks = ctx.ticks(digTicks(world, pos, state));
			return WorkAction.fromSite(stand, Vec3d.ofCenter(pos), ticks, c -> digEntrance(c, pos)).breaking(pos);
		}

		// 2. antorchas, si se las das: en la escalera y en el rellano
		if (underground && ctx.site.count(TORCH, false) > 0) {
			BlockState torch = Blocks.TORCH.getDefaultState();
			for (BlockPos spot : new BlockPos[] {stepFeet(ctx.sitePos, back, 3), landing(ctx.sitePos, back)}) {
				if (world.getBlockState(spot).isAir() && torch.canPlaceAt(world, spot)) {
					return WorkAction.atBlock(spot, ctx.ticks(10), c -> placeTorch(c, spot, torch)).reach(2.5);
				}
			}
		}

		// 3. el pozo, capa a capa: la mina desde el rellano; el pozo abierto, desde el puesto
		BlockPos stand = underground ? landing(ctx.sitePos, back) : ctx.sitePos;
		BlockPos center = area.center(ctx.sitePos, ctx.facing);
		int half = area.radius();
		int top = underground ? pitTop(ctx.sitePos) : ctx.sitePos.getY() - 1;
		int limit = world.getBottomY();
		if (maxDepth > 0) {
			limit = Math.max(limit, top - maxDepth + 1);
		}
		int y = ctx.site.getMineY();
		if (y == WorkSiteBlockEntity.UNSET || y > top) {
			y = top;
			ctx.site.setMineY(y);
		}
		BlockPos feet = ctx.worker.getBlockPos();
		for (int layer = 0; layer < LAYERS_PER_PLAN; layer++, y--) {
			if (y < limit) {
				return ctx.idle(WorkStatus.DONE);
			}
			boolean blocked = false;
			for (int dz = -half; dz <= half; dz++) {
				for (int dx = -half; dx <= half; dx++) {
					BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					if (!canDig(world, pos, state, shovelOnly)) {
						continue;
					}
					// nunca el bloque que pisa (para no caerse al pozo)
					if (pos.getX() == feet.getX() && pos.getZ() == feet.getZ() && pos.getY() < feet.getY()) {
						continue;
					}
					// en la primera capa no quita lo que sostiene arena o grava: el techo se queda en su sitio
					if (underground && y == top && world.getBlockState(pos.up()).getBlock() instanceof FallingBlock) {
						continue;
					}
					if (state.isToolRequired() && !ctx.tool().isSuitableFor(state)) {
						if (!ctx.hasTool()) {
							blocked = true;
						}
						continue;
					}
					int ticks = ctx.ticks(digTicks(world, pos, state));
					return WorkAction.fromSite(stand, Vec3d.ofCenter(pos), ticks, c -> digPit(c, pos)).breaking(pos);
				}
			}
			if (blocked) {
				return ctx.idle(WorkStatus.NEED_TOOL);
			}
			ctx.site.setMineY(y - 1);
		}
		return ctx.idle(WorkStatus.WORKING);
	}

	/** Hueco ya abierto: aire, hierba o similar, o una antorcha que puso él. */
	private static boolean isOpen(BlockState state) {
		return Jobs.isClear(state) || state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH);
	}

	private static int digTicks(ServerWorld world, BlockPos pos, BlockState state) {
		return MathHelper.clamp(Math.round(10 + state.getHardness(world, pos) * 12), 8, 160);
	}

	private static boolean canDig(ServerWorld world, BlockPos pos, BlockState state, boolean shovelOnly) {
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

	// ------------------------------------------------------------------ tareas

	/** Un bloque de la entrada: se cava sea cual sea (la escalera tiene que quedar abierta). */
	private static boolean digEntrance(WorkContext ctx, BlockPos pos) {
		BlockState state = ctx.world.getBlockState(pos);
		if (isOpen(state) || !canDig(ctx.world, pos, state, false)) {
			return false;
		}
		boolean hard = state.getHardness(ctx.world, pos) > 0.0F;
		ctx.harvest(pos);
		if (hard) {
			ctx.damageTool(1);
		}
		return true;
	}

	private boolean digPit(WorkContext ctx, BlockPos pos) {
		BlockState state = ctx.world.getBlockState(pos);
		if (!canDig(ctx.world, pos, state, shovelOnly)) {
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

	private static boolean placeTorch(WorkContext ctx, BlockPos pos, BlockState torch) {
		if (!ctx.world.getBlockState(pos).isAir() || !torch.canPlaceAt(ctx.world, pos) || !ctx.site.consume(TORCH, 1, false)) {
			return false;
		}
		ctx.world.setBlockState(pos, torch);
		ctx.sound(pos, SoundEvents.BLOCK_WOOD_PLACE, 1.0F, 1.0F);
		return true;
	}
}
