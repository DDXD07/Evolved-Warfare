package com.evolvedwarfare.legendary;

import com.evolvedwarfare.config.EwConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Lo que sale de donde menos te lo esperas (fase 105, como en RLCraft):
 * <ul>
 * <li><b>Al picar piedra</b>: un nido de lepismas o un minero zombi (con su pico y su casco); en la pizarra profunda,
 * arañas de cueva o un esqueleto minero; en la roca del Nether, cubos de magma.</li>
 * <li><b>Al talar</b>: el árbol se despierta (un <b>ent</b>) o caen arañas de la copa.</li>
 * <li><b>Al cavar</b> tierra, arena o grava: sale un zombi enterrado (un momificado en la arena).</li>
 * <li><b>Al despertar</b>: a veces (un 2 %) <b>el Coco</b> te está esperando ({@link CocoEntity}).</li>
 * </ul>
 */
public final class BlockMobs {
	public enum Source {
		STONE, DEEP, LOG, DIRT, NETHER
	}

	private BlockMobs() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp && automatic()) {
				Source source = source(state, pos);
				if (source != null && canHappen(sw, sp) && sw.random.nextDouble() < EwConfig.get().blockMobChance) {
					spawn(sw, sp, pos, source);
				}
			}
		});
		// al despertar (de día, tras dormir la noche), a veces el Coco te está esperando
		net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayerEntity player && automatic() && player.getServerWorld().isDay()
					&& canHappen(player.getServerWorld(), player) && player.getRandom().nextDouble() < EwConfig.get().cocoChance) {
				coco(player.getServerWorld(), player);
			}
		});
	}

	static boolean automatic() {
		return System.getProperty("fabric-api.gametest") == null;
	}

	private static boolean canHappen(ServerWorld world, PlayerEntity player) {
		return EwConfig.get().blockMobsEnabled && world.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && !player.isCreative()
				&& !player.isSpectator();
	}

	/** De qué es el bloque (o null si de ahí no sale nada). */
	public static Source source(BlockState state, BlockPos pos) {
		if (state.isIn(BlockTags.LOGS)) {
			return Source.LOG;
		}
		if (state.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || state.isOf(Blocks.DEEPSLATE) || pos.getY() < 0 && state.isIn(BlockTags.PICKAXE_MINEABLE)
				&& state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
			return Source.DEEP;
		}
		if (state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.STONE_ORE_REPLACEABLES)
				|| state.isIn(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES)) {
			return Source.STONE;
		}
		if (state.isIn(BlockTags.BASE_STONE_NETHER)) {
			return Source.NETHER;
		}
		if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL)) {
			return Source.DIRT;
		}
		return null;
	}

	/** Sale lo que toque de ese bloque y va a por el jugador. Devuelve lo que ha salido. */
	public static List<MobEntity> spawn(ServerWorld world, PlayerEntity player, BlockPos pos, Source source) {
		List<MobEntity> out = new ArrayList<>();
		Random r = world.random;
		String message;
		switch (source) {
			case STONE -> {
				if (r.nextFloat() < 0.6F) {
					for (int k = 0; k < 3 + r.nextInt(2); k++) {
						add(out, world, EntityType.SILVERFISH, pos, player);
					}
				} else {
					MobEntity miner = add(out, world, EntityType.ZOMBIE, pos, player);
					equipMiner(miner, Items.STONE_PICKAXE, Items.LEATHER_HELMET);
				}
				message = "stone";
			}
			case DEEP -> {
				if (r.nextBoolean()) {
					add(out, world, EntityType.CAVE_SPIDER, pos, player);
					add(out, world, EntityType.CAVE_SPIDER, pos, player);
				} else {
					MobEntity miner = add(out, world, EntityType.SKELETON, pos, player);
					equipMiner(miner, Items.IRON_PICKAXE, Items.IRON_HELMET);
				}
				message = "deep";
			}
			case LOG -> {
				if (r.nextFloat() < 0.7F) {
					add(out, world, Legendaries.TREANT, pos, player);
				} else {
					add(out, world, EntityType.SPIDER, pos.up(2), player);
					add(out, world, EntityType.SPIDER, pos.up(2), player);
				}
				message = "log";
			}
			case NETHER -> {
				add(out, world, EntityType.MAGMA_CUBE, pos, player);
				message = "nether";
			}
			default -> {
				boolean sand = world.getBiome(pos).value().getTemperature() >= 1.5F;
				add(out, world, sand ? EntityType.HUSK : EntityType.ZOMBIE, pos, player);
				message = "dirt";
			}
		}
		if (!out.isEmpty()) {
			world.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 0.8F, 1.4F);
			player.sendMessage(Text.translatable("message.evolvedwarfare.ambush." + message).formatted(Formatting.RED, Formatting.ITALIC), true);
		}
		return out;
	}

	private static void equipMiner(MobEntity miner, net.minecraft.item.Item tool, net.minecraft.item.Item helmet) {
		if (miner == null) {
			return;
		}
		miner.equipStack(EquipmentSlot.MAINHAND, new ItemStack(tool));
		miner.equipStack(EquipmentSlot.HEAD, new ItemStack(helmet));
		miner.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.15F);
		miner.setEquipmentDropChance(EquipmentSlot.HEAD, 0.1F);
	}

	private static MobEntity add(List<MobEntity> out, ServerWorld world, EntityType<? extends MobEntity> type, BlockPos pos, PlayerEntity target) {
		MobEntity mob = type.create(world);
		if (mob == null) {
			return null;
		}
		mob.refreshPositionAndAngles(pos.getX() + 0.5 + world.random.nextFloat() * 0.4 - 0.2, pos.getY(), pos.getZ() + 0.5 + world.random.nextFloat() * 0.4 - 0.2,
				world.random.nextFloat() * 360, 0);
		mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
		if (type == EntityType.ZOMBIE || type == EntityType.SKELETON) {
			// los mineros: solo lo que les damos
			mob.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
			mob.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
			mob.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
		}
		mob.setTarget(target);
		world.spawnEntity(mob);
		out.add(mob);
		return mob;
	}

	/** El Coco: aparece a la espalda del que se despierta (solo puede haber uno en el mundo). */
	public static CocoEntity coco(ServerWorld world, ServerPlayerEntity player) {
		if (!world.getEntitiesByType(Legendaries.COCO, e -> e.isAlive()).isEmpty()) {
			return null;
		}
		CocoEntity coco = Legendaries.COCO.create(world);
		if (coco == null) {
			return null;
		}
		coco.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0);
		world.spawnEntity(coco);
		if (!coco.behind(player)) {
			coco.refreshPositionAndAngles(player.getX() + 2, player.getY(), player.getZ(), player.getYaw(), 0);
		}
		coco.setTarget(player);
		world.playSound(null, player.getBlockPos(), SoundEvents.AMBIENT_CAVE.value(), SoundCategory.HOSTILE, 2.0F, 0.5F);
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.DARKNESS, 100, 0));
		player.sendMessage(Text.translatable("message.evolvedwarfare.coco.appears").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
		return coco;
	}
}
