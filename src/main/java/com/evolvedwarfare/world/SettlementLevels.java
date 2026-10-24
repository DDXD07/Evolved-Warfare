package com.evolvedwarfare.world;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.faction.Standing;
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
import net.minecraft.text.MutableText;
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
 * Nivel de los asentamientos de las facciones (campamentos y fuertes de bandidos, asentamientos vikingos y calas
 * piratas), de 1 a {@value #MAX_LEVEL}. Sube con la distancia al punto de aparición del mundo (uno cada
 * {@value #BLOCKS_PER_LEVEL} bloques) más un poco de azar fijo por asentamiento; los fuertes de bandidos, uno más, y
 * las fortalezas del caudillo, dos más.
 * Cada nivel por encima del 1 da a su guarnición un 10 % más de vida y de daño.
 *
 * <p>Al acercarte a menos de {@value #NOTICE_RANGE} bloques de uno, sale en pantalla su nombre, su nivel y cómo te
 * trata su facción.</p>
 */
public final class SettlementLevels {
	public static final TagKey<Structure> SETTLEMENTS = TagKey.of(RegistryKeys.STRUCTURE, EvolvedWarfare.id("faction_settlements"));
	public static final int MAX_LEVEL = 10;
	public static final double BONUS_PER_LEVEL = 0.10;
	public static final int BLOCKS_PER_LEVEL = 750;
	public static final int NOTICE_RANGE = 24;
	/** No repetir el aviso del mismo asentamiento antes de esto (por si entras y sales del borde). */
	private static final long REPEAT_TICKS = 1200L;

	private record Shown(long key, long time) {
	}

	private static final Map<UUID, Long> INSIDE = new HashMap<>();
	private static final Map<UUID, Shown> LAST_SHOWN = new HashMap<>();

	private SettlementLevels() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(SettlementLevels::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			INSIDE.remove(handler.getPlayer().getUuid());
			LAST_SHOWN.remove(handler.getPlayer().getUuid());
		});
	}

	/** Multiplicador de vida y daño de un nivel (nivel 1: x1; nivel 10: x1,9). */
	public static double multiplier(int level) {
		return 1.0 + BONUS_PER_LEVEL * (Math.max(1, level) - 1);
	}

	private static Registry<Structure> structures(World world) {
		return world.getRegistryManager().get(RegistryKeys.STRUCTURE);
	}

	@Nullable
	private static Identifier idOf(World world, StructureStart start) {
		return structures(world).getId(start.getStructure());
	}

	/** Nivel de un asentamiento (siempre el mismo para el mismo mundo y sitio). */
	public static int levelOf(ServerWorld world, StructureStart start) {
		ChunkPos chunk = start.getPos();
		BlockPos spawn = world.getSpawnPos();
		double dx = chunk.getCenterX() - spawn.getX();
		double dz = chunk.getCenterZ() - spawn.getZ();
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		long hash = HashCommon.mix(world.getSeed() ^ (chunk.toLong() * 0x9E3779B97F4A7C15L));
		int level = 1 + distance / BLOCKS_PER_LEVEL + (int) Math.floorMod(hash, 3L);
		Identifier id = idOf(world, start);
		if (id != null) {
			switch (id.getPath()) {
				case "bandit_fort" -> level += 1;
				case "bandit_stronghold" -> level += 2;
				default -> {
				}
			}
		}
		return MathHelper.clamp(level, 1, MAX_LEVEL);
	}

	/** Nivel del asentamiento que contiene ese sitio, o 0 si no hay ninguno. */
	public static int levelAt(ServerWorld world, BlockPos pos) {
		StructureStart start = world.getStructureAccessor().getStructureContaining(pos, SETTLEMENTS);
		return start.hasChildren() ? levelOf(world, start) : 0;
	}

	public static Faction factionOf(@Nullable Identifier id) {
		String path = id == null ? "" : id.getPath();
		if (path.startsWith("viking")) {
			return Faction.VIKINGS;
		}
		if (path.startsWith("pirate")) {
			return Faction.PIRATES;
		}
		return Faction.BANDITS;
	}

	public static Formatting levelColor(int level) {
		if (level <= 3) {
			return Formatting.GREEN;
		}
		if (level <= 6) {
			return Formatting.GOLD;
		}
		return level <= 8 ? Formatting.RED : Formatting.DARK_PURPLE;
	}

	// ------------------------------------------------------------------ aviso al acercarse

	/** El asentamiento a menos de {@value #NOTICE_RANGE} bloques de esa posición, o null. */
	@Nullable
	private static StructureStart near(ServerWorld world, BlockPos pos) {
		Registry<Structure> registry = structures(world);
		ChunkPos center = new ChunkPos(pos);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int cx = center.x + dx;
				int cz = center.z + dz;
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				for (StructureStart start : world.getStructureAccessor().getStructureStarts(new ChunkPos(cx, cz),
						structure -> registry.getEntry(structure).isIn(SETTLEMENTS))) {
					if (start.hasChildren() && start.getBoundingBox().expand(NOTICE_RANGE).contains(pos)) {
						return start;
					}
				}
			}
		}
		return null;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 20 != 0) {
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
		Identifier id = idOf(world, start);
		Faction faction = factionOf(id);
		int level = levelOf(world, start);
		Standing standing = Reputation.standing(world.getServer(), player.getUuid(), faction);
		Text title = Text.translatable("structure.evolvedwarfare." + (id != null ? id.getPath() : "settlement"))
				.formatted(faction.color(), Formatting.BOLD);
		int percent = (int) Math.round((multiplier(level) - 1.0) * 100.0);
		MutableText subtitle = Text.empty()
				.append(level > 1 ? Text.translatable("gui.evolvedwarfare.settlement.level", level, percent)
						: Text.translatable("gui.evolvedwarfare.settlement.level_base", level))
				.formatted(levelColor(level))
				.append(Text.literal(" · ").formatted(Formatting.GRAY))
				.append(faction.displayName())
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable(standing.translationKey()).formatted(standing.color()));
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		player.networkHandler.sendPacket(new TitleS2CPacket(title));
		player.playSoundToPlayer(standing == Standing.HOSTILE ? SoundEvents.EVENT_RAID_HORN.value() : SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
				SoundCategory.AMBIENT, standing == Standing.HOSTILE ? 0.4F : 0.8F, 1.0F);
	}
}
