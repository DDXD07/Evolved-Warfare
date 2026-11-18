package com.evolvedwarfare.skill;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.difficulty.Elites;
import com.evolvedwarfare.hell.MiniBossEntity;
import com.evolvedwarfare.legendary.Legendary;
import com.evolvedwarfare.network.SurvivorPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * La experiencia de superviviente (fase 112, como en ARK): además de las subidas de habilidad, se gana matando (mucho más
 * con los élites y los legendarios), minando menas, talando, cosechando, fabricando algo por primera vez, entrando en un
 * bioma nuevo y viendo amanecer. Lo que pone el jugador no da nada al romperlo (para que no se pueda poner y quitar una
 * mena o un tronco una y otra vez).
 */
public final class Survivor {
	/** Lo último que han puesto los jugadores, por mundo (no da experiencia al romperlo). */
	private static final Map<RegistryKey<World>, LinkedHashSet<Long>> PLACED = new HashMap<>();
	private static final int PLACED_MAX = 50_000;

	private Survivor() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SurvivorPayload.ID, SurvivorPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity player && entity != player) {
				float xp = killXp(entity);
				notable(player, xp, xp >= 40.0F ? "kill" : null, entity.getType().getTranslationKey());
			}
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayerEntity sp && !world.isClient) {
				boolean placed = unplace(world, pos);
				if (!placed) {
					give(sp, blockXp(state));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 40 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (!player.isSpectator() && player.isAlive()) {
					explore(player);
					dawn(player);
				}
			}
		});
	}

	private static boolean enabled() {
		return EwConfig.get().survivorXpRate > 0.0;
	}

	// ------------------------------------------------------------------ cuánto

	/** Lo que da matar algo. */
	public static float killXp(LivingEntity entity) {
		float hp = entity.getMaxHealth();
		float xp;
		if (entity instanceof EnderDragonEntity || entity instanceof WitherEntity) {
			xp = 500.0F;
		} else if (entity instanceof Legendary || entity instanceof MiniBossEntity) {
			xp = Math.max(150.0F, hp * 1.5F);
		} else if (entity instanceof PlayerEntity) {
			xp = 25.0F;
		} else if (entity instanceof Monster) {
			xp = hp * 0.5F;
		} else if (entity instanceof AnimalEntity) {
			xp = 1.5F;
		} else {
			// las personas del mod (bandidos, soldados...) y lo demás
			xp = hp * 0.3F;
		}
		if (!Elites.affixes(entity).isEmpty()) {
			xp *= 3.0F;
		}
		return Math.min(xp, 1500.0F);
	}

	/** Lo que da romper un bloque (que no haya puesto un jugador). */
	public static float blockXp(BlockState state) {
		if (state.isIn(ConventionalBlockTags.ORES)) {
			String id = Registries.BLOCK.getId(state.getBlock()).getPath();
			if (id.contains("ancient_debris")) {
				return 25.0F;
			}
			if (id.contains("diamond") || id.contains("emerald")) {
				return 12.0F;
			}
			if (id.contains("gold")) {
				return 5.0F;
			}
			if (id.contains("lapis")) {
				return 4.0F;
			}
			if (id.contains("iron") || id.contains("redstone")) {
				return 3.0F;
			}
			if (id.contains("coal") || id.contains("copper") || id.contains("quartz")) {
				return 2.0F;
			}
			return 3.0F;
		}
		if (state.isIn(BlockTags.LOGS)) {
			return 0.6F;
		}
		if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
			return 0.4F;
		}
		return 0.0F;
	}

	public static final float FIRST_CRAFT_XP = 4.0F, FIRST_CRAFT_BLOCK_XP = 0.5F, NEW_BIOME_XP = 12.0F, DAWN_XP = 10.0F;

	// ------------------------------------------------------------------ dar

	/** Da experiencia (por la configuración); sin aviso propio (sale el "+N" de siempre). */
	public static void give(ServerPlayerEntity player, float amount) {
		notable(player, amount, null, "");
	}

	/** Da experiencia y, si 'reason' no es null, avisa de por qué (bioma nuevo, primera vez que fabrica...). */
	public static void notable(ServerPlayerEntity player, float amount, String reason, String arg) {
		if (amount <= 0.0F || !enabled()) {
			return;
		}
		float xp = amount * (float) EwConfig.get().survivorXpRate;
		Skills.survivorXp(player, xp);
		if (reason != null && ServerPlayNetworking.canSend(player, SurvivorPayload.ID)) {
			ServerPlayNetworking.send(player, new SurvivorPayload(xp, reason, arg));
		}
	}

	/** Fabricó (o sacó del horno) algo: la primera vez de cada cosa da experiencia. */
	public static void crafted(ServerPlayerEntity player, ItemStack stack) {
		if (stack.isEmpty() || player.getServer() == null || !enabled()) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		if (data.crafted.add(Registries.ITEM.getId(stack.getItem()).toString())) {
			state.markDirty();
			// los bloques (escaleras, losas, vallas de cada madera y color...) dan poco: si no, aprender la construcción
			// de madera daba 9 niveles de golpe fabricando una de cada
			float xp = stack.getItem() instanceof net.minecraft.item.BlockItem ? FIRST_CRAFT_BLOCK_XP : FIRST_CRAFT_XP;
			notable(player, xp, xp >= FIRST_CRAFT_XP ? "craft" : null, stack.getItem().getTranslationKey());
		}
	}

	private static void explore(ServerPlayerEntity player) {
		RegistryEntry<Biome> biome = player.getWorld().getBiome(player.getBlockPos());
		var key = biome.getKey();
		if (key.isEmpty() || !enabled()) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		String id = key.get().getValue().toString();
		if (data.biomes.add(id)) {
			state.markDirty();
			// el primero (donde aparece) no cuenta como descubrimiento
			if (data.biomes.size() > 1) {
				notable(player, NEW_BIOME_XP, "biome", "biome." + key.get().getValue().getNamespace() + "." + key.get().getValue().getPath());
			}
		}
	}

	/** Al amanecer, los que han pasado la noche despiertos y vivos (llevan al menos media jornada sin morir). */
	private static void dawn(ServerPlayerEntity player) {
		ServerWorld overworld = player.getServer().getOverworld();
		long time = overworld.getTimeOfDay();
		long day = time / 24000L;
		long tod = time % 24000L;
		if (tod > 1200L) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		if (data.lastDawn >= day) {
			return;
		}
		boolean first = data.lastDawn < 0;
		data.lastDawn = day;
		state.markDirty();
		if (!first && day > 0 && player.age >= 10000) {
			notable(player, DAWN_XP, "dawn", String.valueOf(day));
		}
	}

	// ------------------------------------------------------------------ lo que se pone

	public static void placed(World world, BlockPos pos) {
		if (world.isClient) {
			return;
		}
		LinkedHashSet<Long> set = PLACED.computeIfAbsent(world.getRegistryKey(), k -> new LinkedHashSet<>());
		set.add(pos.asLong());
		if (set.size() > PLACED_MAX) {
			Iterator<Long> it = set.iterator();
			it.next();
			it.remove();
		}
	}

	/** Si lo había puesto un jugador (y lo olvida). */
	private static boolean unplace(World world, BlockPos pos) {
		LinkedHashSet<Long> set = PLACED.get(world.getRegistryKey());
		return set != null && set.remove(pos.asLong());
	}

	public static boolean wasPlaced(World world, BlockPos pos) {
		LinkedHashSet<Long> set = PLACED.get(world.getRegistryKey());
		return set != null && set.contains(pos.asLong());
	}
}
