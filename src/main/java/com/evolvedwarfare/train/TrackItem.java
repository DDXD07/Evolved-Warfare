package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un trozo de vía grande. Se pone donde se mira; si cerca hay una punta de vía suelta, se engancha a ella (sigue la vía en
 * su dirección y a su altura). Las cuestas suben hacia delante; agachado, bajan.
 */
public class TrackItem extends Item {
	public final Track.Shape shape;
	public final boolean left;

	public TrackItem(Track.Shape shape, boolean left, Settings settings) {
		super(settings);
		this.shape = shape;
		this.left = left;
	}

	/** Dónde iría el trozo: su esquina de origen, la altura, hacia dónde mira, si se engancha a una punta y si cabe. */
	public record Plan(Track.Piece piece, boolean snapped, boolean valid) {
	}

	/** Dónde iría el trozo si se pone en target (el bloque donde se pondría un bloque normal), mirando hacia facing. */
	public static Plan plan(World world, BlockPos target, Direction facing, Track.Shape shape, boolean left, boolean down) {
		Vec3d center = Vec3d.ofBottomCenter(target).add(0, Track.RAIL_TOP, 0);
		// la punta suelta más cercana
		Vec3d bestEnd = null, bestOut = null;
		double best = 3.2 * 3.2;
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos p : BlockPos.iterate(target.add(-4, -1, -4), target.add(4, 1, 4))) {
			BlockState s = world.getBlockState(p);
			if (!(s.getBlock() instanceof TrackBlock)) {
				continue;
			}
			Track.Piece piece = Track.piece(p.toImmutable(), s);
			if (!seen.add(piece.main())) {
				continue;
			}
			for (int r = 0; r < Track.routes(piece.shape()); r++) {
				for (boolean atEnd : new boolean[] {false, true}) {
					// la punta común de un desvío se cuenta una vez
					if (r == 1 && !atEnd) {
						continue;
					}
					double t = atEnd ? piece.length(r) : 0;
					Vec3d e = piece.point(r, t);
					if (Math.abs(e.y - center.y) > 1.1) {
						continue;
					}
					double d = (e.x - center.x) * (e.x - center.x) + (e.z - center.z) * (e.z - center.z);
					if (d < best && !Track.connected(world, piece, r, atEnd)) {
						best = d;
						bestEnd = e;
						bestOut = Track.flat(piece.dir(r, t)).multiply(atEnd ? 1 : -1);
					}
				}
			}
		}
		Track.Piece piece;
		boolean snapped = bestEnd != null;
		if (snapped) {
			Direction out = Direction.getFacing(bestOut.x, 0, bestOut.z);
			int y = (int) Math.floor(bestEnd.y - Track.RAIL_TOP + 0.01);
			double ox = Math.round(bestEnd.x), oz = Math.round(bestEnd.z);
			if (shape == Track.Shape.SLOPE && down) {
				// baja: la punta de arriba de la cuesta en la punta suelta
				piece = new Track.Piece(BlockPos.ORIGIN, shape, out.getOpposite(), false, false, ox + 4 * out.getOffsetX(), oz + 4 * out.getOffsetZ(), y - 1);
			} else {
				piece = new Track.Piece(BlockPos.ORIGIN, shape, out, left, false, ox, oz, y);
			}
		} else {
			Direction r = facing.rotateYClockwise();
			int m = left ? -1 : 1;
			double ox = Math.round(target.getX() + 0.5 - m * 0.5 * r.getOffsetX() - 0.5 * facing.getOffsetX());
			double oz = Math.round(target.getZ() + 0.5 - m * 0.5 * r.getOffsetZ() - 0.5 * facing.getOffsetZ());
			if (shape == Track.Shape.SLOPE && down) {
				piece = new Track.Piece(BlockPos.ORIGIN, shape, facing.getOpposite(), false, false, ox + 4 * facing.getOffsetX(),
						oz + 4 * facing.getOffsetZ(), target.getY() - 1);
			} else {
				piece = new Track.Piece(BlockPos.ORIGIN, shape, facing, left, false, ox, oz, target.getY());
			}
		}
		Track.Piece placed = new Track.Piece(piece.blockOf(0, 0), piece.shape(), piece.facing(), piece.left(), false, piece.ox(), piece.oz(), piece.y());
		boolean valid = true;
		for (BlockPos b : placed.blocks()) {
			BlockState s = world.getBlockState(b);
			if (!s.isReplaceable() || s.getBlock() instanceof TrackBlock || world.isOutOfHeightLimit(b)) {
				valid = false;
				break;
			}
		}
		return new Plan(placed, snapped, valid);
	}

	/** El plan con lo que mira el jugador (o null si no mira a un bloque). */
	@Nullable
	public Plan plan(World world, PlayerEntity player, BlockPos target) {
		return plan(world, target, player.getHorizontalFacing(), shape, left, player.isSneaking());
	}

	/** Pone el trozo (todos sus bloques). */
	public static void place(World world, Track.Piece piece) {
		List<int[]> parts = new ArrayList<>(Track.footprint(piece.shape()));
		// el principal, primero (es el que se dibuja)
		parts.sort((a, b) -> Boolean.compare(Track.part(b[0], b[1]) == Track.MAIN, Track.part(a[0], a[1]) == Track.MAIN));
		for (int[] b : parts) {
			BlockState s = Trains.TRACK.getDefaultState().with(TrackBlock.SHAPE, piece.shape()).with(TrackBlock.FACING, piece.facing())
					.with(TrackBlock.LEFT, piece.left()).with(TrackBlock.PART, Track.part(b[0], b[1])).with(TrackBlock.THROWN, false);
			world.setBlockState(piece.blockOf(b[0], b[1]), s, Block.NOTIFY_ALL);
		}
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (player == null) {
			return ActionResult.PASS;
		}
		BlockPos target = new ItemPlacementContext(context).getBlockPos();
		Plan plan = plan(world, player, target);
		if (plan == null || !plan.valid()) {
			if (!world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.track.no_room").formatted(Formatting.RED), true);
			}
			return ActionResult.FAIL;
		}
		if (!world.isClient) {
			place(world, plan.piece());
			world.playSound(null, plan.piece().main(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 0.7F);
			world.playSound(null, plan.piece().main(), SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.BLOCKS, 1.0F, 0.9F);
			context.getStack().decrementUnlessCreative(1, player);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
		tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
	}
}
