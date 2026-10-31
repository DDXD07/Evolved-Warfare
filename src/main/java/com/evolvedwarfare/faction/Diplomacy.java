package com.evolvedwarfare.faction;

import com.evolvedwarfare.entity.FactionGear;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcOrder;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.entity.SoldierEntity;
import com.evolvedwarfare.logistics.Market;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.world.FactionSpawner;
import java.util.UUID;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Lo que se puede hacer desde la pantalla de diplomacia (con el sello de embajador). Se paga con monedas
 * ({@link Market#currency()}).
 * <ul>
 *   <li><b>Tributo</b>: {@value #TRIBUTE_COINS} monedas → +{@value #TRIBUTE_GAIN} de reputación.</li>
 *   <li><b>Tregua</b> (si sois enemigos): 3 días sin ataques ni asaltos. Cuanto más te odien, más cara.</li>
 *   <li><b>Alianza</b> (con {@value Reputation#ALLIED_AT} de reputación): {@value #ALLIANCE_COINS} monedas. Luchan a tu
 *       lado y puedes pedirles ayuda. Romperla te cuesta {@value #BETRAYAL} de reputación.</li>
 *   <li><b>Pedir ayuda</b> (aliados): {@value #AID_SIZE} guerreros te acompañan 5 minutos; una vez al día.</li>
 *   <li><b>Mercenario</b> (amistosos o aliados): {@value #MERCENARY_COINS} monedas → un soldado tuyo con su equipo.</li>
 * </ul>
 */
public final class Diplomacy {
	public static final int TRIBUTE = 0;
	public static final int TRUCE = 1;
	public static final int ALLIANCE = 2;
	public static final int BREAK_ALLIANCE = 3;
	public static final int AID = 4;
	public static final int MERCENARY = 5;

	public static final int TRIBUTE_COINS = 5;
	public static final int TRIBUTE_GAIN = 5;
	public static final long TRUCE_TICKS = 72000L;
	public static final int ALLIANCE_COINS = 30;
	public static final int BETRAYAL = 25;
	public static final long AID_COOLDOWN = 24000L;
	public static final long AID_TICKS = 6000L;
	public static final int AID_SIZE = 4;
	public static final int MERCENARY_COINS = 12;

	private Diplomacy() {
	}

	/** Precio de una tregua: 10 monedas más 1 por cada 5 puntos por debajo de cero. */
	public static int truceCost(int reputation) {
		return 10 + Math.max(0, -reputation) / 5;
	}

	public static void perform(ServerPlayerEntity player, Faction faction, int action) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		UUID id = player.getUuid();
		int rep = Reputation.get(server, id, faction);
		Standing standing = Reputation.standing(server, id, faction);
		long now = server.getOverworld().getTime();
		switch (action) {
			case TRIBUTE -> {
				if (rep >= faction.maxReputation()) {
					fail(player, "message.evolvedwarfare.diplomacy.max", faction);
				} else if (pay(player, TRIBUTE_COINS)) {
					Reputation.change(server, id, faction, TRIBUTE_GAIN);
					sound(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
				}
			}
			case TRUCE -> {
				if (standing != Standing.HOSTILE) {
					fail(player, "message.evolvedwarfare.diplomacy.no_truce_needed", faction);
				} else if (pay(player, truceCost(rep))) {
					Reputation.setTruce(server, id, faction, now + TRUCE_TICKS);
					player.sendMessage(Text.translatable("message.evolvedwarfare.diplomacy.truce", faction.displayName())
							.formatted(Formatting.GOLD), false);
					sound(player, SoundEvents.BLOCK_BELL_USE);
				}
			}
			case ALLIANCE -> {
				if (!faction.canAlly()) {
					fail(player, "message.evolvedwarfare.diplomacy.never_ally", faction);
				} else if (standing == Standing.ALLIED) {
					fail(player, "message.evolvedwarfare.diplomacy.already_allied", faction);
				} else if (rep < Reputation.ALLIED_AT) {
					fail(player, "message.evolvedwarfare.diplomacy.need_reputation", faction);
				} else if (pay(player, ALLIANCE_COINS)) {
					Reputation.setAllied(server, id, faction, true);
					sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
				}
			}
			case BREAK_ALLIANCE -> {
				if (standing == Standing.ALLIED) {
					Reputation.setAllied(server, id, faction, false);
					Reputation.change(server, id, faction, -BETRAYAL);
				}
			}
			case AID -> {
				if (standing != Standing.ALLIED) {
					fail(player, "message.evolvedwarfare.diplomacy.need_alliance", faction);
				} else if (Reputation.aidReadyAt(server, id, faction) > now) {
					fail(player, "message.evolvedwarfare.diplomacy.aid_cooldown", faction);
				} else if (sendAid(player, faction, now)) {
					Reputation.setAidReadyAt(server, id, faction, now + AID_COOLDOWN);
				}
			}
			case MERCENARY -> {
				if (!standing.atLeast(Standing.FRIENDLY)) {
					fail(player, "message.evolvedwarfare.diplomacy.need_friendship", faction);
				} else if (pay(player, MERCENARY_COINS)) {
					hireMercenary(player, faction);
				}
			}
			default -> {
			}
		}
	}

	private static boolean pay(ServerPlayerEntity player, int coins) {
		if (player.isCreative() || Market.takeCoins(player.getInventory(), coins)) {
			return true;
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.shop.no_money", coins, Market.currency().getName())
				.formatted(Formatting.RED), true);
		return false;
	}

	private static void fail(ServerPlayerEntity player, String key, Faction faction) {
		player.sendMessage(Text.translatable(key, faction.displayName()).formatted(Formatting.RED), true);
	}

	private static void sound(ServerPlayerEntity player, net.minecraft.sound.SoundEvent sound) {
		player.getWorld().playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, 0.8F, 1.0F);
	}

	/** Un grupo de guerreros aliados aparece cerca y te escolta un rato. */
	private static boolean sendAid(ServerPlayerEntity player, Faction faction, long now) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		Random random = world.getRandom();
		BlockPos spot = FactionSpawner.findSpot(world, player.getBlockPos(), 6, 12, random);
		if (spot == null) {
			spot = player.getBlockPos();
		}
		NpcRole[] roles = {NpcRole.CAPTAIN, NpcRole.WARRIOR, NpcRole.WARRIOR,
				faction == Faction.PIRATES ? NpcRole.CROSSBOWMAN : NpcRole.ARCHER};
		int arrived = 0;
		for (int i = 0; i < AID_SIZE; i++) {
			FactionNpcEntity ally = FactionSpawner.spawnMember(world, faction, spot.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2),
					roles[i % roles.length], SpawnReason.EVENT, random);
			if (ally != null) {
				ally.startEscort(player.getUuid(), now + AID_TICKS);
				arrived++;
			}
		}
		if (arrived == 0) {
			return false;
		}
		world.playSound(null, spot, SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.NEUTRAL, 6.0F, 1.4F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.diplomacy.aid", faction.displayName(), arrived)
				.formatted(Formatting.AQUA), false);
		return true;
	}

	/** Un soldado tuyo, con el equipo de la facción. Viene con 3 días de paga. */
	private static void hireMercenary(ServerPlayerEntity player, Faction faction) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Random random = world.getRandom();
		BlockPos spot = FactionSpawner.findSpot(world, player.getBlockPos(), 3, 6, random);
		if (spot == null) {
			spot = player.getBlockPos();
		}
		SoldierEntity soldier = ModEntities.SOLDIER.create(world);
		if (soldier == null) {
			return;
		}
		NpcRole role = switch (faction) {
			case VIKINGS -> random.nextBoolean() ? NpcRole.WARRIOR : (random.nextBoolean() ? NpcRole.BARBARIAN : NpcRole.ARCHER);
			case PIRATES -> random.nextBoolean() ? NpcRole.WARRIOR : NpcRole.CROSSBOWMAN;
			case BANDITS -> random.nextBoolean() ? NpcRole.WARRIOR : NpcRole.ARCHER;
		};
		soldier.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		soldier.setOwner(player);
		soldier.presetRole(role);
		FactionGear.equip(soldier, faction, role, random, false);
		com.evolvedwarfare.armor.FactionArmor.dress(soldier, faction, role, random, false);
		soldier.initialize(world, world.getLocalDifficulty(spot), SpawnReason.EVENT, null);
		soldier.setOrder(NpcOrder.FOLLOW);
		soldier.getProvisions().addStack(new ItemStack(Items.BREAD, 3));
		world.spawnEntityAndPassengers(soldier);
		world.playSound(null, spot, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.diplomacy.mercenary", soldier.getName(), faction.displayName())
				.formatted(Formatting.GREEN), false);
	}
}
