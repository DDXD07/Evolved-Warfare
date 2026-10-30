package com.evolvedwarfare.skill;

import com.evolvedwarfare.ClientBridge;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.network.SkillPayloads;
import com.evolvedwarfare.research.ResearchState;
import com.evolvedwarfare.research.Tech;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El árbol de habilidades (como en Skyrim): las habilidades suben usándolas ({@link #gain}), cada subida da
 * experiencia de personaje y cada nivel de personaje, un punto de ventaja para gastar en las constelaciones. Las ventajas
 * de "forja" desbloquean la fabricación (con la opción de la configuración); las demás dan bonus y efectos
 * ({@link SkillEffects}).
 */
public final class Skills {
	private static Map<Item, Perk> locks;
	/** Jugadores con cambios pequeños (experiencia) que mandar al cliente en el siguiente segundo. */
	private static final Set<UUID> PENDING = new HashSet<>();

	private Skills() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SkillPayloads.Data.ID, SkillPayloads.Data.CODEC);
		PayloadTypeRegistry.playC2S().register(SkillPayloads.Unlock.ID, SkillPayloads.Unlock.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkillPayloads.Unlock.ID, (payload, context) -> {
			Perk perk = Perk.byId(payload.perk());
			if (perk != null) {
				unlock(context.player(), perk);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			migrate(handler.getPlayer());
			sync(handler.getPlayer(), false);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 == 0 && !PENDING.isEmpty()) {
				for (UUID id : List.copyOf(PENDING)) {
					ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
					if (player != null) {
						sync(player, false);
					}
				}
				PENDING.clear();
			}
			SkillEffects.tick(server);
		});
	}

	// ------------------------------------------------------------------ consultar

	public static SkillData get(MinecraftServer server, UUID player) {
		return SkillState.get(server).of(player);
	}

	/** El rango que tiene alguien de una ventaja (en el servidor, aunque no esté conectado). */
	public static int rank(@Nullable World world, @Nullable UUID player, Perk perk) {
		if (world == null || player == null || world.getServer() == null) {
			return 0;
		}
		SkillData data = SkillState.get(world.getServer()).peek(player);
		return data == null ? 0 : data.rank(perk);
	}

	/** El rango de un jugador: en el servidor, el suyo; en el cliente, solo se sabe el del jugador propio. */
	public static int rank(PlayerEntity player, Perk perk) {
		if (player.getWorld().isClient) {
			return ClientBridge.clientPerkRank.applyAsInt(player, perk);
		}
		return rank(player.getWorld(), player.getUuid(), perk);
	}

	public static boolean has(@Nullable World world, @Nullable UUID player, Perk perk) {
		return rank(world, player, perk) > 0;
	}

	public static boolean has(PlayerEntity player, Perk perk) {
		return rank(player, perk) > 0;
	}

	/** Lo que da una ventaja a un jugador con su rango (0 si no la tiene). */
	public static float value(PlayerEntity player, Perk perk) {
		return perk.value(rank(player, perk));
	}

	// ------------------------------------------------------------------ fabricar

	/** La ventaja que hace falta para fabricar esto (o null). */
	@Nullable
	public static Perk lockOf(Item item) {
		if (locks == null) {
			Map<Item, Perk> map = new HashMap<>();
			for (Perk perk : Perk.values()) {
				for (Item unlocked : perk.unlocks()) {
					map.put(unlocked, perk);
				}
			}
			locks = map;
		}
		return locks.get(item);
	}

	public static boolean canCraft(ServerPlayerEntity player, ItemStack result) {
		if (!EwConfig.get().researchRequired || player.getAbilities().creativeMode) {
			return true;
		}
		Perk perk = lockOf(result.getItem());
		return perk == null || rank(player, perk) > 0;
	}

	// ------------------------------------------------------------------ subir

	/** Usó la habilidad ('amount' en "usos": más o menos el daño hecho, parado o recibido). */
	public static void gain(ServerPlayerEntity player, Skill skill, float amount) {
		if (amount <= 0.0F || player.isSpectator() || player.getServer() == null) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		int i = skill.ordinal();
		if (data.levels[i] >= Skill.MAX_LEVEL) {
			return;
		}
		data.xp[i] += amount * (float) EwConfig.get().skillXpRate;
		boolean leveled = false;
		while (data.levels[i] < Skill.MAX_LEVEL && data.xp[i] >= Skill.xpToNext(data.levels[i])) {
			data.xp[i] -= Skill.xpToNext(data.levels[i]);
			data.levels[i]++;
			leveled = true;
			characterXp(player, data, data.levels[i]);
		}
		if (data.levels[i] >= Skill.MAX_LEVEL) {
			data.xp[i] = 0.0F;
		}
		state.markDirty();
		if (leveled) {
			player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 0.7F);
			sync(player, false);
		} else {
			PENDING.add(player.getUuid());
		}
	}

	private static void characterXp(ServerPlayerEntity player, SkillData data, float amount) {
		data.levelXp += amount;
		while (data.levelXp >= SkillData.levelXpToNext(data.level)) {
			data.levelXp -= SkillData.levelXpToNext(data.level);
			data.level++;
			data.points++;
			player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8F, 1.0F);
		}
	}

	/** Pone una habilidad en un nivel (comandos y pruebas); la experiencia de personaje sube como si la hubiera usado. */
	public static void setLevel(ServerPlayerEntity player, Skill skill, int level) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		int target = Math.max(0, Math.min(Skill.MAX_LEVEL, level));
		while (data.levels[skill.ordinal()] < target) {
			data.levels[skill.ordinal()]++;
			characterXp(player, data, data.levels[skill.ordinal()]);
		}
		data.levels[skill.ordinal()] = target;
		data.xp[skill.ordinal()] = 0.0F;
		state.markDirty();
		sync(player, false);
	}

	// ------------------------------------------------------------------ desbloquear

	public enum Unlock {
		OK, MAX_RANK, LEVEL, REQUIRES, POINTS
	}

	/** Si se puede coger el siguiente rango de la ventaja (sin mirar los puntos si es creativo). */
	public static Unlock check(SkillData data, Perk perk, boolean free) {
		int rank = data.rank(perk);
		if (rank >= perk.ranks()) {
			return Unlock.MAX_RANK;
		}
		if (data.level(perk.skill) < perk.levelFor(rank + 1)) {
			return Unlock.LEVEL;
		}
		if (rank == 0 && !perk.requires().isEmpty()) {
			boolean any = false;
			for (Perk before : perk.requires()) {
				any |= data.rank(before) > 0;
			}
			if (!any) {
				return Unlock.REQUIRES;
			}
		}
		if (!free && data.points <= 0) {
			return Unlock.POINTS;
		}
		return Unlock.OK;
	}

	public static Unlock unlock(ServerPlayerEntity player, Perk perk) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		boolean free = player.getAbilities().creativeMode;
		Unlock result = check(data, perk, free);
		if (result == Unlock.OK) {
			data.perks.put(perk, data.rank(perk) + 1);
			if (!free) {
				data.points--;
			}
			state.markDirty();
			player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.4F);
			SkillEffects.refresh(player);
		} else {
			player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.6F);
		}
		sync(player, false);
		return result;
	}

	/** Le da una ventaja sin pedir nada (comandos, migración). */
	public static void grant(ServerPlayerEntity player, Perk perk, int rank) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		data.perks.put(perk, Math.max(0, Math.min(perk.ranks(), rank)));
		state.markDirty();
		SkillEffects.refresh(player);
		sync(player, false);
	}

	/** Le devuelve todos los puntos gastados y quita las ventajas (comando). */
	public static void reset(ServerPlayerEntity player) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		data.points += data.spentPoints();
		data.perks.clear();
		state.markDirty();
		SkillEffects.refresh(player);
		sync(player, false);
	}

	public static void addPoints(ServerPlayerEntity player, int points) {
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		data.points = Math.max(0, data.points + points);
		state.markDirty();
		sync(player, false);
	}

	// ------------------------------------------------------------------ lo de antes

	/**
	 * Lo que se tenía investigado en la mesa (la investigación de antes) pasa a ser ventajas, sin gastar puntos ni pedir
	 * nivel: así nadie pierde lo que ya podía fabricar ni las mejoras de sus soldados.
	 */
	public static void migrate(ServerPlayerEntity player) {
		if (player.getServer() == null) {
			return;
		}
		SkillState state = SkillState.get(player.getServer());
		SkillData data = state.of(player.getUuid());
		if (data.migrated) {
			return;
		}
		data.migrated = true;
		state.markDirty();
		ResearchState.Entry entry = ResearchState.get(player.getServer()).peek(player.getUuid());
		if (entry == null || entry.done.isEmpty()) {
			return;
		}
		List<Perk> given = new ArrayList<>();
		for (Tech tech : entry.done) {
			for (Perk perk : fromResearch(tech)) {
				if (data.rank(perk) == 0) {
					data.perks.put(perk, 1);
					given.add(perk);
				}
			}
		}
		if (!given.isEmpty()) {
			MutableText list = Text.empty();
			for (int i = 0; i < given.size(); i++) {
				if (i > 0) {
					list.append(", ");
				}
				list.append(Text.translatable(given.get(i).translationKey()));
			}
			player.sendMessage(Text.translatable("message.evolvedwarfare.skills.migrated", list).formatted(Formatting.GOLD), false);
		}
	}

	/** Qué ventajas dan las técnicas de la investigación de antes. */
	public static List<Perk> fromResearch(Tech tech) {
		return switch (tech) {
			case MILITARY_SMITHING -> List.of(Perk.DAGGER_SMITH, Perk.SPEAR_SMITH, Perk.JAVELIN_SMITH);
			case HEAVY_WEAPONS -> List.of(Perk.LONGSWORD_SMITH, Perk.MACE_SMITH, Perk.BATTLE_AXE_SMITH);
			case POLEARMS -> List.of(Perk.HALBERD_SMITH);
			case ARCHERY -> List.of(Perk.LONGBOW_SMITH);
			case SIEGE_CARPENTRY -> List.of(Perk.RAM_SMITH);
			case TORSION -> List.of(Perk.TORSION);
			case COUNTERWEIGHT -> List.of(Perk.COUNTERWEIGHT);
			case INCENDIARIES -> List.of(Perk.INCENDIARIES);
			case FORTIFICATION -> List.of(Perk.FORTIFICATION);
			case DRILL -> List.of(Perk.DRILL);
			case TACTICS -> List.of(Perk.TACTICS);
			case VETERANS -> List.of(Perk.VETERANS);
			case FIELD_MEDICINE -> List.of(Perk.FIELD_MEDICINE);
		};
	}

	// ------------------------------------------------------------------ al cliente

	public static void sync(ServerPlayerEntity player, boolean open) {
		if (player.getServer() == null || !ServerPlayNetworking.canSend(player, SkillPayloads.Data.ID)) {
			return;
		}
		SkillData data = SkillState.get(player.getServer()).of(player.getUuid());
		ServerPlayNetworking.send(player, new SkillPayloads.Data(data.toNbt(), open, EwConfig.get().researchRequired));
		PENDING.remove(player.getUuid());
	}
}
