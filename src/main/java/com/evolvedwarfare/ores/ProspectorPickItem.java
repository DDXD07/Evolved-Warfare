package com.evolvedwarfare.ores;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * El pico de prospector: clic derecho a una roca y, por el sonido, se sabe qué mineral hay cerca (a 20 bloques), si mucho o
 * poco, más o menos a qué distancia y hacia dónde (norte, sur... arriba, abajo).
 */
public class ProspectorPickItem extends Item {
	public static final int RADIUS = 20;

	public ProspectorPickItem(Settings settings) {
		super(settings);
	}

	/** El mineral de una mena (o null si no lo es). */
	public static MineralPebblesBlock.Mineral mineral(BlockState s) {
		Block b = s.getBlock();
		if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) {
			return MineralPebblesBlock.Mineral.COAL;
		}
		if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE || b == Blocks.RAW_IRON_BLOCK) {
			return MineralPebblesBlock.Mineral.IRON;
		}
		if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE || b == Blocks.RAW_COPPER_BLOCK) {
			return MineralPebblesBlock.Mineral.COPPER;
		}
		if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE || b == Ores.GOLD_GRAVEL) {
			return MineralPebblesBlock.Mineral.GOLD;
		}
		if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) {
			return MineralPebblesBlock.Mineral.REDSTONE;
		}
		if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) {
			return MineralPebblesBlock.Mineral.LAPIS;
		}
		if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
			return MineralPebblesBlock.Mineral.DIAMOND;
		}
		if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) {
			return MineralPebblesBlock.Mineral.EMERALD;
		}
		return null;
	}

	/** Lo que encuentra: el mineral que más hay cerca, cuánto, y el bloque más cercano de él (o null si no hay nada). */
	public record Finding(MineralPebblesBlock.Mineral mineral, int count, BlockPos nearest) {
	}

	public static Finding prospect(ServerWorld world, BlockPos center) {
		Map<MineralPebblesBlock.Mineral, Integer> counts = new EnumMap<>(MineralPebblesBlock.Mineral.class);
		Map<MineralPebblesBlock.Mineral, BlockPos> nearest = new EnumMap<>(MineralPebblesBlock.Mineral.class);
		Map<MineralPebblesBlock.Mineral, Double> best = new EnumMap<>(MineralPebblesBlock.Mineral.class);
		BlockPos.Mutable m = new BlockPos.Mutable();
		int minY = Math.max(world.getBottomY(), center.getY() - RADIUS), maxY = Math.min(world.getTopY() - 1, center.getY() + RADIUS);
		for (int cx = (center.getX() - RADIUS) >> 4; cx <= (center.getX() + RADIUS) >> 4; cx++) {
			for (int cz = (center.getZ() - RADIUS) >> 4; cz <= (center.getZ() + RADIUS) >> 4; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				Chunk chunk = world.getChunk(cx, cz);
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					ChunkSection section = chunk.getSection(chunk.getSectionIndex(sy << 4));
					// se salta lo que no tiene ninguna mena (mirando su paleta)
					if (section.isEmpty() || !section.hasAny(s -> mineral(s) != null)) {
						continue;
					}
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							int wx = (cx << 4) + x, wz = (cz << 4) + z;
							if (Math.abs(wx - center.getX()) > RADIUS || Math.abs(wz - center.getZ()) > RADIUS) {
								continue;
							}
							for (int y = 0; y < 16; y++) {
								int wy = (sy << 4) + y;
								if (wy < minY || wy > maxY) {
									continue;
								}
								MineralPebblesBlock.Mineral kind = mineral(section.getBlockState(x, y, z));
								if (kind == null) {
									continue;
								}
								counts.merge(kind, 1, Integer::sum);
								double d = center.getSquaredDistance(wx, wy, wz);
								if (d < best.getOrDefault(kind, Double.MAX_VALUE)) {
									best.put(kind, d);
									nearest.put(kind, m.set(wx, wy, wz).toImmutable());
								}
							}
						}
					}
				}
			}
		}
		MineralPebblesBlock.Mineral top = null;
		int most = 0;
		for (var e : counts.entrySet()) {
			// lo raro cuenta más (un diamante se nota más que una veta de carbón)
			int weight = e.getValue() * switch (e.getKey()) {
				case DIAMOND, EMERALD -> 12;
				case GOLD, LAPIS -> 4;
				case REDSTONE -> 2;
				default -> 1;
			};
			if (weight > most) {
				most = weight;
				top = e.getKey();
			}
		}
		return top == null ? null : new Finding(top, counts.get(top), nearest.get(top));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player == null || player.getItemCooldownManager().isCoolingDown(this)) {
			return ActionResult.PASS;
		}
		if (!(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}
		BlockPos pos = context.getBlockPos();
		world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.PLAYERS, 1.0F, 1.4F);
		world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4F, 0.6F);
		player.getItemCooldownManager().set(this, 40);
		context.getStack().damage(1, player, EquipmentSlot.MAINHAND);
		Finding f = prospect(world, pos);
		player.sendMessage(describe(f, player), true);
		return ActionResult.CONSUME;
	}

	public static Text describe(Finding f, PlayerEntity player) {
		if (f == null) {
			return Text.translatable("message.evolvedwarfare.prospect.none").formatted(Formatting.GRAY);
		}
		String amount = f.count() >= 40 ? "lots" : f.count() >= 10 ? "some" : "little";
		double dx = f.nearest().getX() + 0.5 - player.getX(), dy = f.nearest().getY() + 0.5 - player.getEyeY(), dz = f.nearest().getZ() + 0.5 - player.getZ();
		int dist = Math.max(2, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) / 2.0) * 2);
		Text where;
		if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
			where = Text.translatable("message.evolvedwarfare.prospect." + (dy < 0 ? "below" : "above"));
		} else {
			// hacia dónde, en 8 rumbos (el norte es -z)
			double ang = Math.toDegrees(Math.atan2(dx, -dz));
			String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
			String dir = dirs[Math.floorMod((int) Math.round(ang / 45.0), 8)];
			Text h = Text.translatable("message.evolvedwarfare.prospect.dir." + dir);
			where = Math.abs(dy) > 4 ? Text.translatable("message.evolvedwarfare.prospect.dir_and", h,
					Text.translatable("message.evolvedwarfare.prospect." + (dy < 0 ? "below" : "above"))) : h;
		}
		return Text.translatable("message.evolvedwarfare.prospect.found", Text.translatable("message.evolvedwarfare.prospect.amount." + amount),
				Text.translatable("mineral.evolvedwarfare." + f.mineral().asString()).formatted(Formatting.GOLD), dist, where);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.prospector_pick.desc").formatted(Formatting.GRAY));
	}

	@SuppressWarnings("unused")
	private static float unused() {
		return MathHelper.PI;
	}
}
