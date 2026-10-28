package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.config.EwConfig;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Nivel de las ciudades (medievales, del desierto y del norte), de 1 a 10: sube con la distancia al punto de aparición
 * (uno cada 600 bloques, configurable) más un poco de azar fijo por ciudad. Cuanto más alto, mejores cosas venden sus
 * comerciantes y más caro. Al entrar en una ciudad sale en pantalla su nivel.
 */
public final class CityLevels {
	public static final TagKey<Structure> CITIES = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("trade_cities"));
	public static final int MAX_LEVEL = 10;
	private static final int NOTICE_RANGE = 8;
	private static final long REPEAT_TICKS = 1200L;

	private record Shown(long key, long time) {
	}

	private static final Map<UUID, Long> INSIDE = new HashMap<>();
	private static final Map<UUID, Shown> LAST_SHOWN = new HashMap<>();

	private CityLevels() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(CityLevels::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			INSIDE.remove(handler.getPlayer().getUuid());
			LAST_SHOWN.remove(handler.getPlayer().getUuid());
		});
	}

	public static int maxLevel() {
		return MathHelper.clamp(EwConfig.get().cityMaxLevel, 1, MAX_LEVEL);
	}

	/** Multiplicador de los precios de venta de los comerciantes de una ciudad de ese nivel. */
	public static double priceMultiplier(int level) {
		return 1.0 + EwConfig.get().traderPricePerLevel * (Math.max(1, level) - 1);
	}

	private static int levelFor(ServerWorld world, ChunkPos chunk, int extraRoll) {
		BlockPos spawn = world.getSpawnPos();
		double dx = chunk.getCenterX() - spawn.getX();
		double dz = chunk.getCenterZ() - spawn.getZ();
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		long hash = HashCommon.mix(world.getSeed() ^ (chunk.toLong() * 0xC2B2AE3D27D4EB4FL));
		int level = 1 + distance / Math.max(1, EwConfig.get().cityBlocksPerLevel) + (int) Math.floorMod(hash, (long) extraRoll);
		return MathHelper.clamp(level, 1, maxLevel());
	}

	/** Nivel de una ciudad (siempre el mismo para el mismo mundo y sitio). */
	public static int levelOf(ServerWorld world, StructureStart start) {
		return levelFor(world, start.getPos(), 2);
	}

	/** Nivel de la ciudad que contiene ese sitio; fuera de las ciudades, el que tocaría por la distancia. */
	public static int levelAt(ServerWorld world, BlockPos pos) {
		StructureStart start = world.getStructureAccessor().getStructureContaining(pos, CITIES);
		if (start.hasChildren()) {
			return levelOf(world, start);
		}
		return levelFor(world, new ChunkPos(pos), 1);
	}

	public static Formatting levelColor(int level) {
		return SettlementLevels.levelColor(level);
	}

	// ------------------------------------------------------------------ aviso al entrar

	@Nullable
	private static StructureStart near(ServerWorld world, BlockPos pos) {
		Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		ChunkPos center = new ChunkPos(pos);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int cx = center.x + dx;
				int cz = center.z + dz;
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				for (StructureStart start : world.getStructureAccessor().getStructureStarts(new ChunkPos(cx, cz),
						structure -> registry.getEntry(structure).isIn(CITIES))) {
					if (start.hasChildren() && start.getBoundingBox().expand(NOTICE_RANGE).contains(pos)) {
						return start;
					}
				}
			}
		}
		return null;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 20 != 7) {
			return;
		}
		long now = world.getTime();
		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID id = player.getUuid();
			if (player.isSpectator()) {
				continue;
			}
			StructureStart start = near(world, player.getBlockPos());
			if (start == null) {
				INSIDE.remove(id);
				continue;
			}
			long key = start.getPos().toLong() * 31L + start.getStructure().hashCode();
			Long inside = INSIDE.get(id);
			if (inside != null && inside == key) {
				continue;
			}
			INSIDE.put(id, key);
			Shown last = LAST_SHOWN.get(id);
			if (last != null && last.key() == key && now - last.time() < REPEAT_TICKS) {
				continue;
			}
			LAST_SHOWN.put(id, new Shown(key, now));
			announce(world, player, start);
		}
	}

	private static void announce(ServerWorld world, ServerPlayerEntity player, StructureStart start) {
		Identifier id = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(start.getStructure());
		int level = levelOf(world, start);
		Text title = Text.translatable("structure.evolvedwarfare." + (id != null ? id.getPath() : "city"))
				.formatted(Formatting.GOLD, Formatting.BOLD);
		int percent = (int) Math.round((priceMultiplier(level) - 1.0) * 100.0);
		Text subtitle = (level > 1 ? Text.translatable("gui.evolvedwarfare.city.level", level, percent)
				: Text.translatable("gui.evolvedwarfare.city.level_base", level)).formatted(levelColor(level));
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		player.networkHandler.sendPacket(new TitleS2CPacket(title));
		player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.AMBIENT, 0.7F, 1.2F);
	}
}
