package com.evolvedwarfare.entity;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Bandos: cada jugador forma un bando con sus NPCs (y sus mascotas); los bandidos forman otro.
 * Entre aliados no hay fuego amigo: ni flechas ni golpes.
 */
public final class NpcSides {
	/** Identificador fijo del bando de los bandidos. */
	public static final UUID BANDITS = new UUID(0x45574241_4E444954L, 0x5300000000000001L);

	private NpcSides() {
	}

	@Nullable
	public static UUID sideOf(@Nullable Entity entity) {
		if (entity instanceof BanditEntity) {
			return BANDITS;
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
		return side != null && side.equals(sideOf(b));
	}

	private static boolean ours(Entity entity) {
		return entity instanceof AbstractNpcEntity || entity instanceof BanditEntity;
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
