package com.evolvedwarfare.wood;

import com.evolvedwarfare.config.EwConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La tala:
 * <ul>
 * <li>Los troncos <b>solo se talan con hacha</b>: con la mano (o con otra cosa) no se rompen.</li>
 * <li>Para la primera hacha: los palos salen de las hojas (rómpelas con la mano), 4 palos hacen una losa de roble, dos
 * losas una encima de otra un bloque de tablones; y con sílex y dos palos, un <b>hacha de sílex</b>.</li>
 * <li>Al talar <b>uno de los dos primeros troncos</b> de un árbol (no agachado), el árbol <b>cae entero</b> hacia donde
 * miras ({@link FallingTreeEntity}) y suelta su madera, sus hojas y su fruta al caer.</li>
 * </ul>
 */
public final class Woodcutting {
	private static final int MAX_LOGS = 300;
	private static final int MAX_LEAVES = 1500;
	/** Lo lejos (en hojas) que se busca la copa desde los troncos. */
	private static final int CROWN = 6;
	/** Un palo de cada tantas hojas rotas a mano (además de los de siempre). */
	private static final float STICK_CHANCE = 0.25F;

	/** El hacha de sílex: como la de madera, pero de piedra tallada (dura un poco menos). */
	public static final ToolMaterial FLINT = new ToolMaterial() {
		@Override
		public int getDurability() {
			return 48;
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return 2.5F;
		}

		@Override
		public float getAttackDamage() {
			return 0.5F;
		}

		@Override
		public TagKey<Block> getInverseTag() {
			return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
		}

		@Override
		public int getEnchantability() {
			return 5;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.ofItems(Items.FLINT);
		}
	};

	private Woodcutting() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!world.isClient && needsAxe(player.isCreative(), world.getBlockState(pos), player.getMainHandStack())) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.wood.need_axe").formatted(Formatting.GOLD), true);
			}
			return ActionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld sw)) {
				return;
			}
			if (state.isIn(BlockTags.LEAVES) && !player.isCreative() && !player.getMainHandStack().isOf(Items.SHEARS)
					&& sw.random.nextFloat() < STICK_CHANCE) {
				Block.dropStack(sw, pos, new ItemStack(Items.STICK));
			}
			ItemStack tool = player.getMainHandStack();
			if (EwConfig.get().treeFelling && state.isIn(BlockTags.LOGS) && !player.isSneaking() && tool.isIn(ItemTags.AXES)) {
				FallingTreeEntity tree = fell(sw, pos, player.getHorizontalFacing());
				if (tree != null && !player.isCreative()) {
					tool.damage(tree.logCount() / 3, player, EquipmentSlot.MAINHAND);
				}
			}
		});
	}

	/** ¿Hace falta un hacha (y no la lleva)? */
	public static boolean needsAxe(boolean creative, BlockState state, ItemStack tool) {
		return EwConfig.get().axeForLogs && !creative && state.isIn(BlockTags.LOGS) && !tool.isIn(ItemTags.AXES);
	}

	private static boolean isLog(World world, BlockPos pos) {
		return world.getBlockState(pos).isIn(BlockTags.LOGS);
	}

	private static boolean naturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && state.contains(LeavesBlock.PERSISTENT) && !state.get(LeavesBlock.PERSISTENT);
	}

	/**
	 * Tira el árbol del tronco que se acaba de talar en {@code cut} (ya no está): si era uno de los dos primeros y lo de
	 * arriba es un árbol de verdad (con hojas naturales, no una casa de troncos). Devuelve el árbol que cae, o null.
	 */
	@Nullable
	public static FallingTreeEntity fell(ServerWorld world, BlockPos cut, Direction fall) {
		boolean firstTwo = !isLog(world, cut.down()) || !isLog(world, cut.down(2));
		if (!firstTwo || fall.getAxis().isVertical()) {
			return null;
		}
		// los troncos unidos, de ahí hacia arriba
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> logs = new ArrayList<>();
		seen.add(cut);
		queue.add(cut);
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos p = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos q = p.add(dx, dy, dz);
						if (q.getY() < cut.getY() || !seen.add(q)) {
							continue;
						}
						if (isLog(world, q)) {
							logs.add(q);
							queue.add(q);
						}
					}
				}
			}
		}
		if (logs.isEmpty()) {
			return null;
		}
		// la copa: las hojas naturales unidas a esos troncos
		Set<BlockPos> leafSeen = new HashSet<>();
		List<BlockPos> leaves = new ArrayList<>();
		ArrayDeque<BlockPos> front = new ArrayDeque<>();
		ArrayDeque<Integer> depth = new ArrayDeque<>();
		for (BlockPos log : logs) {
			for (Direction d : Direction.values()) {
				BlockPos q = log.offset(d);
				if (!seen.contains(q) && leafSeen.add(q) && naturalLeaves(world.getBlockState(q))) {
					leaves.add(q);
					front.add(q);
					depth.add(1);
				}
			}
		}
		if (leaves.size() < 3) {
			return null; // sin copa: no es un árbol (una casa, un poste...)
		}
		while (!front.isEmpty() && leaves.size() < MAX_LEAVES) {
			BlockPos p = front.poll();
			int dist = depth.poll();
			if (dist >= CROWN) {
				continue;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.offset(d);
				if (q.getY() >= cut.getY() && !seen.contains(q) && leafSeen.add(q) && naturalLeaves(world.getBlockState(q))) {
					leaves.add(q);
					front.add(q);
					depth.add(dist + 1);
				}
			}
		}
		List<BlockPos> all = new ArrayList<>(logs);
		all.addAll(leaves);
		int[] states = new int[all.size()];
		int[] rel = new int[all.size()];
		for (int i = 0; i < all.size(); i++) {
			BlockPos p = all.get(i);
			states[i] = Block.getRawIdFromState(world.getBlockState(p));
			rel[i] = FallingTreeEntity.pack(p.getX() - cut.getX(), p.getY() - cut.getY(), p.getZ() - cut.getZ());
		}
		for (BlockPos p : all) {
			world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
		FallingTreeEntity tree = FallingTreeEntity.create(world, cut, fall, states, rel, logs.size());
		world.spawnEntity(tree);
		world.playSound(null, cut, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.6F, 0.5F);
		world.playSound(null, cut, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.5F, 0.6F);
		return tree;
	}
}
