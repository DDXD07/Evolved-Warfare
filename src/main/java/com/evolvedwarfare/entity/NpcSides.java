package com.evolvedwarfare.entity;

import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Bandos: cada jugador forma un bando con sus NPCs (y sus mascotas); cada facción (bandidos, vikingos, piratas) forma
 * otro. Una facción aliada de un jugador y su bando también se consideran aliados. Entre aliados no hay fuego amigo:
 * ni flechas ni golpes.
 */
public final class NpcSides {
	/** Identificador fijo del bando de los bandidos. */
	public static final UUID BANDITS = Faction.BANDITS.side();

	private NpcSides() {
	}

	@Nullable
	public static UUID sideOf(@Nullable Entity entity) {
		if (entity instanceof FactionNpcEntity faction) {
			return faction.getFaction().side();
		}
		if (entity instanceof AbstractNpcEntity npc) {
			return npc.getOwnerUuid();
		}
		if (entity instanceof PlayerEntity player) {
			return player.getUuid();
		}
		if (entity instanceof Tameable tameable) {
			return tameable.getOwnerUuid();
		}
		return null;
	}

	public static boolean allies(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null) {
			return false;
		}
		UUID side = sideOf(a);
		if (side != null && side.equals(sideOf(b))) {
			return true;
		}
		return friendlyFaction(a, b) || friendlyFaction(b, a);
	}

	/** ¿Es 'npc' de una facción aliada del bando de 'other' (o le está escoltando)? */
	private static boolean friendlyFaction(Entity npc, Entity other) {
		if (!(npc instanceof FactionNpcEntity member) || other instanceof FactionNpcEntity) {
			return false;
		}
		UUID player = sideOf(other);
		if (player == null) {
			return false;
		}
		return member.isEscortOf(player) || Reputation.allied(npc.getWorld(), player, member.getFaction());
	}

	private static boolean ours(Entity entity) {
		return entity instanceof AbstractNpcEntity || entity instanceof FactionNpcEntity;
	}

	/** Cancela el daño entre aliados cuando uno de los dos es un NPC del mod. */
	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getAttacker();
			if (attacker == null || attacker == entity) {
				return true;
			}
			return !((ours(attacker) || ours(entity)) && allies(attacker, entity));
		});
	}
}
